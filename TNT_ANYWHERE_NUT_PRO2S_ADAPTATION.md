# 坚果 Pro2S TNT-Anywhere 适配：核心修复与前后对比

## 结论（最新）

1. 坚果 Pro2S 目前用 **Android 端 Moonlight** 接收，已经可以 **1080P60 流畅运行**。
2. 昨天黑屏问题的真正根因是：**编码视频流与 PC 端 Moonlight 解码的兼容性问题**，
   不是虚拟显示没有产帧。
3. 昨天排查的 `setDisplayLayerStack` 镜像、截图轮询、WFDMM Hook 等方案是绕路，
   均未进入最终版本。
4. 通过把 H.264 SPS 从纯 Baseline 改为 Constrained Baseline，**PC 端 Moonlight 硬解黑屏已解决**。
5. 剩余未解决问题：Android 8.1 上 PC 键鼠注入 TNT 外接屏仍未打通。

## 最终版本对比最初版本

| 模块 | 最初版本（Android 10+ 专属） | 最终版本（含坚果 Pro2S） |
|------|------|------|
| 支持系统 | minSdk 28，只面向 Android 10+ | minSdk 27，支持 Android 8.1 / SmartisanOS |
| TNT 启动 | 依赖 Android 10 的虚拟显示 owner 白名单和 `displayId >= 100000` | API < 28 显式设置 `persist.sys.virtual_display_pkg=com.smartisanos.tntanywhere`、`global_pc_mode_settings=1`、`pc_mode_enable=1`；开机缓存白名单不一致时提示重启手机 |
| 显示选择 | 只认 Android 10 风格的大 displayId | API < 28 优先选择 `tntanywhere.base.display` |
| 视频源 | native 启动编码器后，Java 先建 ImageReader 虚拟屏，再重绑 encoder Surface | API < 28 改为 native 先启动 MediaCodec，Java 直接把 encoder Surface 建成 `tntanywhere.base.display`，没有重绑竞态；失败走 `notifyVideoSourceFailure` |
| 接收端 | PC 端 Moonlight | **Android 端 Moonlight 接收，1080P60 打通**；PC 端硬解通过 Constrained Baseline 修复 |
| 音频 | 仅 Android 10+ 的 `AudioPlaybackCaptureConfiguration` | API < 28 用 Shizuku UserService 的 `REMOTE_SUBMIX` AudioRecord，强制 TNT 音频路由，float PCM 推入 Opus 编码器；媒体音量保持非零并在结束后恢复 |
| 编码器 | 直接使用 API 28+ 的 AMediaFormat 参数 | API 28+ 参数全部用 `#if __ANDROID_API__ >= 28` 保护；API < 28 关键帧间隔 1 秒；CSD 中的 SPS 由纯 Baseline 原地改写为 Constrained Baseline，PC 硬解已恢复 |
| Shizuku 初始化 | 任一系统服务失败就整体失败 | 每个 binder 独立 try-catch，Mouse/Keyboard 初始化改为非致命 |

## 核心修复

### 1. TNT 激活（必要前提）

SmartisanOS 8.1 在开机时把 `persist.sys.virtual_display_pkg` 缓存进
`android.app.SmtPCUtils.VIRTUAL_DISPLAY_PKG` 静态字段，运行时改属性不生效。

修复：

- 设置 `persist.sys.virtual_display_pkg=com.smartisanos.tntanywhere` 和 `pc_mode_enable=1`
- 重启手机，让开机缓存变成正确包名
- app 反射读取框架缓存的 whitelist，与当前包名不一致时明确提示“需要重启手机后生效”

这部分保证 TNT 桌面能进入 PC 模式，是串流的前提，但不是黑屏问题的最终根因。

### 2. 视频源直连 TNT 虚拟显示（工程改进）

旧流程是“先建 ImageReader 虚拟屏，再把画面重绑到 encoder Surface”，
在 API 27 上会和 `TntManagerService` 进 PC 模式互相拆屏。

改进：API < 28 时 native 先启动 MediaCodec，然后把 encoder Surface 交给 Java，
`TntDebugVirtualDisplayHelper.ensureVirtualDisplay(..., encoderSurface)` 一步建成
`tntanywhere.base.display`，TNT 桌面直接渲染进编码器。

### 3. 真正打通画面：切到 Android 端 Moonlight 接收

经过再次排查，黑屏的实际原因是编码视频流和 PC 端 Moonlight 解码不兼容，
与虚拟显示、TNT 识别本身无关。

最终通过 **Android 端 Moonlight 客户端接收**打通：

- Android 解码器能正常解码当前编码流，画面正常
- 坚果 Pro2S 上达到 1080P60 流畅运行

### 4. 音频：REMOTE_SUBMIX

API 27 没有 Android 10+ 的音频捕获 API，改用系统级 `REMOTE_SUBMIX`：

- Shizuku UserService 创建 `REMOTE_SUBMIX` 的 `AudioRecord`（48kHz、立体声、float PCM）
- 反射 `AudioSystem.setDeviceConnectionState` + `setForceUse(media, 0x22)` 强制 TNT 音频路由
- Java 线程读 PCM，经 `pushAudioSamples` JNI 送入现有 Opus 编码器

### 5. PC 端硬解兼容：SPS 改写为 Constrained Baseline（已解决）

为了兼容 PC 端 Moonlight 硬解，工作区加入了：

- API < 28 强制 AVC Baseline + Level 4.2
- CSD/关键帧 hex 日志，用于对比编码配置
- 关键修复：拿到 encoder CSD 后，若 SPS 是纯 Baseline（profile_idc=66 且未置 constraint_set1），
  将 constraint 字节改写为 Constrained Baseline（`67 42 80 2a` → `67 42 c0 2a`）

当前状态：**PC 端 Moonlight 硬解已出画面**，Android 端接收不受影响。

### 6. Android 8.1 键鼠注入外接屏（未解决）

PC 端 Moonlight 键鼠事件无法注入 TNT 外接屏，已排除/确认的路径：

- `MotionEvent.setDisplayId` / `InputEvent.setDisplayId` 在 SmartisanOS 8.1 上不存在
- `createInputForwarder(displayId)` 因 Shizuku 身份是 shell、TNT 屏归属 app uid 被拒
- 8.1 的 `GestureDescription.Builder` 没有 `setDisplayId`
- SmartisanOS `input --ext-display` 可用，底层是 `IInputManager.injectInputEventOtherScreens(InputEvent, int)`；
  当前代码传入 `(event, displayId)` 会报 `mode is invalid`，说明第二个参数是注入 mode，
  displayId 如何挂到事件上仍需确认（反编译材料在本地 `analysis/`）

## 关键代码位置

| 文件 | 改动 |
|------|------|
| `app-mirror/build.gradle` | minSdk 28 → 27 |
| `termux-x11-app/build.gradle` | minSdk 28 → 27 |
| `app-mirror/src/main/cpp/sunshine.cpp` | API 28+ 参数保护、先启动编码器再交 Surface、`pushAudioSamples`、AVC Baseline |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java` | 开启 Smartisan PC 模式、encoder Surface 直连虚拟屏、白名单重启检测 |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDisplaySelector.java` | API < 28 优先选 `tntanywhere.base.display` |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineServer.java` | `onVideoInputSurface` 视频源、音频 JNI |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineAudio.java` | REMOTE_SUBMIX 采集、音量保持与恢复 |
| `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java` | REMOTE_SUBMIX AudioRecord、强制音频路由 |
| `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/ServiceUtils.java` | 每个 binder 独立 try-catch |

## 验证结果

- Android 端 Moonlight 接收正常，1080P60 流畅运行
- logcat 持续输出 `Frame #`，帧率稳定在 60fps
- `dumpsys display` 显示 `tntanywhere.base.display`（1920x1080）
- display owner 为 `com.smartisanos.tntanywhere`，处于活动状态
- PC 端 Moonlight 硬解已出画面（H.264，D3D11VA/NVDEC）

## 待解决问题

- 确认 SmartisanOS `injectInputEventOtherScreens` 如何携带 displayId，并让 PC 键鼠作用到 TNT 屏

## 最终链路（当前打通路径）

```text
TNT 桌面 (tntanywhere.base.display)
  → 编码器 Surface
    → Sunshine/H.264 编码
      → Android 端 Moonlight 客户端（1080P60 正常）
        → Shizuku 输入注入回手机

PC 端 Moonlight 客户端：硬解正常
```
