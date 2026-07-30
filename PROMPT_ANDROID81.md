# TNT-Anywhere Android 8.1 适配 & ARctrl 功能移植方案

## 背景

我们在 ARctrl 项目中完成了一轮 Android 8.1 适配工作，目标设备是**坚果 Pro2S (OE106, SmartisanOS, Android 8.1.0, API 27, 骁龙710, arm64-v8a)**。

ARctrl 的沙盒方案在 Android 8.1 上失败了（BlackBox 的 SurfaceControl.mirrorSurface 是 API 28+，沙盒进程的 ContentProvider 注入也断裂了）。因此我们决定切换到 **TNT-Anywhere** 项目，利用其 TNT 桌面 + Moonlight 串流 + Shizuku 输入注入的架构。

## 当前状态

- TNT-Anywhere 代码位于 `D:\TNT-Anywhere`
- 当前 minSdk = 28，需要降到 27
- 已在 ARctrl 上验证过 Android 8.1 的关键兼容点（见下方"已验证的兼容经验"）

## 第一阶段：Android 8.1 基础适配

### 1.1 降低 minSdk

- `app-mirror/build.gradle`: `minSdk 28` → `minSdk 27`
- 检查 `hidden-api-stub` 和其他模块是否也需要调整

### 1.2 VirtualDisplay 兼容

- `CreateVirtualDisplay.java` 中使用了 `VirtualDisplayConfig`（API 28+），Android 8.1 上不存在
- 需要走旧版 `DisplayManager.createVirtualDisplay(String, int, int, int, Surface, int)` 签名
- 参考 `TntDebugVirtualDisplayHelper.java` 中已有的 flag 候选逻辑

### 1.3 ImageReader 兼容

- 骁龙710 的 gralloc 不支持 `USAGE_GPU_SAMPLED_IMAGE | USAGE_GPU_COLOR_OUTPUT` (0x300) 组合
- 如果用到 ImageReader，可能需要只用 `USAGE_GPU_SAMPLED_IMAGE` (0x100)
- `Image.hardwareBuffer` 是 API 28+，Android 8.1 上不存在

### 1.4 getMainExecutor 兼容

- `Activity.getMainExecutor()` 是 API 28+ 的方法
- 修复方式：用 `Executor { mainHandler.post(it) }` 替代
- 已在 ARctrl 的 `ArGlassesHomeController.kt` 中验证通过

### 1.5 Vulkan 桥接

- 如果有 Vulkan 渲染路径在 Android 8.1 上失败（format + usage 组合不支持），需要提供直接 Surface 传递的 fallback
- 已在 ARctrl 的 `SunshineVirtualDisplaySession.kt` 中验证：API < 28 时跳过 Vulkan 桥接，直接把编码器 Surface 传给 VirtualDisplay

### 1.6 Manifest 权限兼容

以下权限在 API 27 上不存在，需要加 `tools:remove` 或 `maxSdkVersion`：
- `BLUETOOTH_CONNECT` (API 31)
- `DETECT_SCREEN_CAPTURE` (API 29)
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` (API 34)
- `FOREGROUND_SERVICE_SPECIAL_USE` (API 34)
- `POST_NOTIFICATIONS` (API 33)

## 第二阶段：验证基础链路

在 Android 8.1 上按以下顺序验证：

1. **Shizuku 权限获取** — `AcquireShizuku` job 能否正常工作
2. **TNT 桌面启动** — `TntDebugVirtualDisplayHelper` 能否创建虚拟显示并激活 TNT
3. **VirtualDisplay 创建** — `CreateVirtualDisplay` 在 API 27 上能否成功
4. **Moonlight 串流** — `ProjectViaMoonlight` → `SunshineServer` → native sunshine 能否正常编码和串流
5. **输入注入** — `SunshineMouse`/`SunshineKeyboard` 通过 Shizuku 的 `injectInputEvent` 能否工作
6. **DisplayLink** — 如果有 USB DisplayLink 适配器，验证 `ProjectViaDisplaylink` 能否工作

## 第三阶段：从 ARctrl 移植功能

### 3.1 AirPlay 投屏

ARctrl 有一个自研的 `com.taowen.airplay` 库，提供：
- `AirPlayScreenMirror` — AirPlay 串流核心
- `AirPlayClient` — AirPlay 客户端发现和配对
- `AirPlayReceiverDiscovery` — mDNS 发现 AirPlay 接收端

移植方式：
- 在 TNT-Anywhere 中创建新的 `AirPlayJob`（参考 `ProjectViaMoonlight` 的结构）
- AirPlay 的 Surface 输入模式与 Moonlight 类似：获取 Surface → 创建 VirtualDisplay → 画面送入 Surface
- 需要处理 AirPlay 配对 PIN 码流程
- 需要处理音频转发（ARctrl 有 `AirPlaySandboxAudioForwarder` 和 `AirPlayPlaybackAudioForwarder`）

### 3.2 AR 眼镜 USB 直连

ARctrl 有 `ar-glass-lib` 模块，支持：
- USB Type-C 连接的 AR 眼镜（通过私有协议）
- 眼镜摄像头读取
- 眼镜 2D/3D 模式切换

移植方式：
- 将 `third_party/ar-glass-lib` 模块引入 TNT-Anywhere
- 在 TNT-Anywhere 的 Job 体系中创建 `ArGlassDisplayJob`
- 需要处理 USB 权限和设备发现

### 3.3 不需要移植的功能

- **2D→3D 立体转换** — 骁龙710 跑不动，放弃
- **BlackBox 沙盒** — Android 8.1 上不工作，放弃
- **深度推理 (ZipDepth/ncnn)** — 依赖 Vulkan + 骁龙710 NPU，不需要

## 已验证的兼容经验（来自 ARctrl）

### getMainExecutor 修复
```kotlin
// 之前（API 28+）
activity.mainExecutor
// 之后（兼容 API 27）
Executor { mainHandler.post(it) }
```

### Vulkan 桥接降级
```kotlin
// API < 28 时跳过 Vulkan 桥接
val frameBridge: SunshineVulkanFrameBridge? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    try { SunshineVulkanFrameBridge(...) } catch (error: RuntimeException) { return }
} else null
val displaySurface = frameBridge?.sourceSurface ?: surface
```

## 设备信息

| 项目 | 值 |
|------|-----|
| 设备 | 坚果 Pro2S (OE106) |
| 系统 | SmartisanOS, Android 8.1.0 |
| API Level | 27 |
| SoC | 骁龙 710 |
| GPU | Adreno 616 |
| 架构 | arm64-v8a |
| ADB 连接 | `adb connect 192.168.50.228` |

## 注意事项

1. **SmartisanOS 私有 API** — 很多 TNT 相关的 API 是锤子私有的，在 Android 8.1 上的行为可能与 Android 10/11 不同
2. **Shizuku 版本** — 建议使用 v13.6.0，在 Android 8.1 上需要验证兼容性
3. **MediaCodec 编码器** — 骁龙710 支持 H.264 编码，但 High Profile Level31 在 720p60 下可能有压力，建议从 720p30 开始测试
4. **虚拟显示创建** — Android 8.1 上 `VirtualDisplayConfig` 不存在，必须用旧版 API
5. **InputManager.injectInputEvent** — 需要 `INJECT_EVENTS` 权限，通过 Shizuku 获取
