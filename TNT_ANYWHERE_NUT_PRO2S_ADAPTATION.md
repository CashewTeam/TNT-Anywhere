# 坚果 R1 / Pro2S Android 8.1 适配：核心修复与分流策略

## 结论（当前）

1. 坚果 R1 / Pro2S 使用 **Android 端 Moonlight** 接收，Pro2S 已经可以 **1080P60 流畅运行**。
2. Android 8.1 的 TNT 视频源现在按 Root 状态分流：
   - Root + Xposed/LSPosed：保持无头 TNT 启动。
   - 无 Root：禁止创建无头 TNT；已有真实 TNT/外接显示器时镜像该显示器，否则回退手机主屏镜像。
3. Android 8.1 音频也按 Root 状态分流：
   - 无 Root：使用 Smartisan `audio_loopback`，不静音手机。
   - Root：使用 `REMOTE_SUBMIX`，按客户端请求控制手机静音。
4. 黑屏问题的真正根因是：**编码视频流与 PC 端 Moonlight 解码的兼容性问题**，
   不是虚拟显示没有产帧。
5. 昨天排查的 `setDisplayLayerStack` 镜像、截图轮询、WFDMM Hook 等方案是绕路，
   均未进入最终版本。
6. 通过把 H.264 SPS 从纯 Baseline 改为 Constrained Baseline，**PC 端 Moonlight 硬解黑屏已解决**。
7. Android 8.1 上 PC 键鼠注入已通过 `IInputManager.injectInputEventOtherScreens(event, 2)` 打通。

## 最终版本对比最初版本

| 模块 | 最初版本（Android 10+ 专属） | 最终版本（含坚果 Pro2S） |
|------|------|------|
| 支持系统 | minSdk 28，只面向 Android 10+ | minSdk 27，支持 Android 8.1 / SmartisanOS |
| TNT 启动 | 依赖 Android 10 的虚拟显示 owner 白名单和 `displayId >= 100000` | Android 8.1 只有 Root + Xposed/LSPosed 才创建无头 TNT；无 Root 仅使用已有真实显示或回退镜像 |
| 显示选择 | 只认 Android 10 风格的大 displayId | Android 8.1 无 Root 只选择真实外接显示器；`tntanywhere.base.display` 只是应用基础显示，不作为已有 TNT |
| 视频源 | native 启动编码器后，Java 先建 ImageReader 虚拟屏，再重绑 encoder Surface | Root API 27 仍由 native 先启动 MediaCodec，再把 encoder Surface 交给 `tntanywhere.base.display`；无 Root 在实际入口禁止创建该显示并转入 `ProjectViaMoonlight` 镜像链路 |
| 接收端 | PC 端 Moonlight | **Android 端 Moonlight 接收，1080P60 打通**；PC 端硬解通过 Constrained Baseline 修复 |
| 音频 | 仅 Android 10+ 的 `AudioPlaybackCaptureConfiguration` | Android 8.1 无 Root 用 `audio_loopback`；Root 用 `REMOTE_SUBMIX`；Android 9+ 使用 `AudioPlaybackCapture` |
| 编码器 | 直接使用 API 28+ 的 AMediaFormat 参数 | API 28+ 参数全部用 `#if __ANDROID_API__ >= 28` 保护；API < 28 关键帧间隔 1 秒；CSD 中的 SPS 由纯 Baseline 原地改写为 Constrained Baseline，PC 硬解已恢复 |
| Shizuku 初始化 | 任一系统服务失败就整体失败 | 每个 binder 独立 try-catch，Mouse/Keyboard 初始化改为非致命 |

## 核心修复

### 1. TNT 激活（必要前提）

SmartisanOS 8.1 在开机时把 `persist.sys.virtual_display_pkg` 缓存进
`android.app.SmtPCUtils.VIRTUAL_DISPLAY_PKG` 静态字段，运行时改属性不生效。

无头 TNT 的必要条件：

- 设置 `persist.sys.virtual_display_pkg=com.smartisanos.tntanywhere` 和 `pc_mode_enable=1`
- 重启手机，让开机缓存变成正确包名
- app 反射读取框架缓存的 whitelist，与当前包名不一致时明确提示“需要重启手机后生效”
- Root 环境下启用本项目 Xposed/LSPosed 模块；模块只修改 PC 模式的显示包名判断，不处理音频权限
- 适用范围限制为坚果 R1 / Pro2S 的 Android 8.1

无 Root 时不再尝试这条无头启动路径。App 先通过 `UserService.isRooted()` 判断 UID；如果没有 Root，则只检查真实外接/TNT 显示器，找不到时直接回退手机主屏镜像。

这部分保证 TNT 桌面能进入 PC 模式，是串流的前提，但不是黑屏问题的最终根因。

### 2. 视频源直连 TNT 虚拟显示（工程改进）

旧流程是“先建 ImageReader 虚拟屏，再把画面重绑到 encoder Surface”，
在 API 27 上会和 `TntManagerService` 进 PC 模式互相拆屏。

Root API < 28 时 native 先启动 MediaCodec，然后把 encoder Surface 交给 Java，
`TntDebugVirtualDisplayHelper.ensureVirtualDisplay(..., encoderSurface)` 一步建成
`tntanywhere.base.display`，TNT 桌面直接渲染进编码器。

实际入口是 `SunshineServer.startMoonlightVideoSource()`。Android 8.1 无 Root 会在这里提前停止无头显示创建，调度 `ProjectViaMoonlight`；后者只选择真实外接显示器，否则将 `State.externalDisplayId` 设为 display 0，进入手机镜像。

### 3. 真正打通画面：切到 Android 端 Moonlight 接收

经过再次排查，黑屏的实际原因是编码视频流和 PC 端 Moonlight 解码不兼容，
与虚拟显示、TNT 识别本身无关。

最终通过 **Android 端 Moonlight 客户端接收**打通：

- Android 解码器能正常解码当前编码流，画面正常
- 坚果 Pro2S 上达到 1080P60 流畅运行

### 4. 音频：Android 8.1 Root 分流

API 27 没有 Android 10+ 的音频捕获 API，当前按 Root 状态选择后端：

- 无 Root：Shizuku shell UserService 启动 Smartisan `audio_loopback` helper，读取 48kHz、立体声、16-bit PCM 并转为 float；不修改手机静音状态。
- Root：UserService 创建 `REMOTE_SUBMIX` 的 `AudioRecord`（48kHz、立体声、float PCM），并反射 `AudioSystem.setDeviceConnectionState` + `setForceUse(media, 0x22)` 强制 TNT 音频路由；客户端请求时才静音手机。
- 两条链路都经 `pushAudioSamples` JNI 送入现有 Opus 编码器。
- Android 9 及以上不使用这两条 API 27 专用路径，继续使用 `AudioPlaybackCaptureConfiguration`。

### 5. PC 端硬解兼容：SPS 改写为 Constrained Baseline（已解决）

为了兼容 PC 端 Moonlight 硬解，工作区加入了：

- API < 28 强制 AVC Baseline + Level 4.2
- CSD/关键帧 hex 日志，用于对比编码配置
- 关键修复：拿到 encoder CSD 后，若 SPS 是纯 Baseline（profile_idc=66 且未置 constraint_set1），
  将 constraint 字节改写为 Constrained Baseline（`67 42 80 2a` → `67 42 c0 2a`）

当前状态：**PC 端 Moonlight 硬解已出画面**，Android 端接收不受影响。

### 6. Android 8.1 键鼠注入外接屏（已解决）

SmartisanOS 8.1 不能把 displayId 直接塞进 `MotionEvent`/`InputEvent`，已排除的路径：

- `MotionEvent.setDisplayId` / `InputEvent.setDisplayId` 不存在
- `createInputForwarder(displayId)` 因 Shizuku 身份是 shell、TNT 屏归属 app uid 被拒
- 8.1 的 `GestureDescription.Builder` 没有 `setDisplayId`

最终方案：调用 `IInputManager.injectInputEventOtherScreens(event, 2)`。
服务端 `InputManagerService.injectInputEventOtherScreens` 内部会自己取
`SmtPCUtilsInner.getExtDisplayId(mContext)`，所以事件不需要携带 displayId，
第二个参数是注入 mode（2），不是 displayId。

当前状态：**PC 端 Moonlight 鼠标点击/拖动和键盘已能作用到 TNT 屏**。
注：纯 `HOVER_MOVE` 仍可能被系统 native 层拒绝，不影响点击/拖动与键盘验证。

## 关键代码位置

| 文件 | 改动 |
|------|------|
| `app-mirror/build.gradle` | minSdk 28 → 27 |
| `termux-x11-app/build.gradle` | minSdk 28 → 27 |
| `app-mirror/src/main/cpp/sunshine.cpp` | API 28+ 参数保护、先启动编码器再交 Surface、`pushAudioSamples`、AVC Baseline |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java` | 开启 Smartisan PC 模式、encoder Surface 直连虚拟屏、白名单重启检测 |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDisplaySelector.java` | 区分真实外接显示器与 `tntanywhere.base.display`，无 Root 只选真实显示 |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineServer.java` | 实际 API 27 视频入口的 Root 检测与无头/镜像分流 |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineMouse.java` / `SunshineKeyboard.java` | API < 28 外接屏输入改走 `injectInputEventOtherScreens(event, 2)` |
| `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineAudio.java` | Android 8.1 Root/无 Root 音频分流、静音策略与恢复 |
| `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java` | `audio_loopback` helper、Root `REMOTE_SUBMIX` AudioRecord、强制音频路由 |
| `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/ServiceUtils.java` | 每个 binder 独立 try-catch |

## 验证结果

- Android 端 Moonlight 接收正常，1080P60 流畅运行
- logcat 持续输出 `Frame #`，帧率稳定在 60fps
- Root 链路的 `dumpsys display` 可显示 `tntanywhere.base.display`，owner 为 `com.smartisanos.tntanywhere`
- 无 Root 且没有真实 TNT 显示器时，不应出现新建的 `tntanywhere.base.display`，而应进入 display 0 镜像
- PC 端 Moonlight 硬解已出画面（H.264，D3D11VA/NVDEC）
- PC 端 Moonlight 键鼠已作用到 TNT 屏，logcat 无 `NoSuchMethodError` / `mode is invalid`

## 待解决问题

- 可选优化：纯 `HOVER_MOVE` 仍可能被系统 native 层拒绝，可考虑转为触摸事件或仅在按下时发送移动

## 最终链路（当前分流路径）

```text
Android 8.1 + Root + Xposed/LSPosed
  → tntanywhere.base.display / TNT 桌面
    → 编码器 Surface
      → Sunshine/H.264 编码
        → Moonlight 客户端

Android 8.1 + 无 Root
  → 已有真实 TNT/外接显示器？
    → 是：外接显示器镜像 → Sunshine/H.264 → Moonlight
    → 否：手机 display 0 镜像 → Sunshine/H.264 → Moonlight

Android 8.1 音频
  → 无 Root: audio_loopback
  → Root: REMOTE_SUBMIX + 可选手机静音

Android 9+
  → 主线 TNT / 镜像链路 + AudioPlaybackCapture

所有视频链路
  → Moonlight 客户端
    → Shizuku 输入注入回手机/TNT 屏

PC 端 Moonlight 客户端：H.264 硬解正常
  → PC 键鼠通过 `injectInputEventOtherScreens(event, 2)` 注入 TNT 屏
```
