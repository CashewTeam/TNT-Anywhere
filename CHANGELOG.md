# Changelog

## v0.9.7

### Android 8.1 TNT 串流

- 已有真实 TNT 外接屏时，直接截图镜像该屏幕到编码器，不再创建并选中无内容的 `tntanywhere.base.display`。
- 8.1 显示选择优先使用真实外接屏，避免 `base.display` 覆盖已启动 TNT 的画面源。

### 音频

- Android 8.1 由 Shizuku shell 原生 helper 调用 Smartisan `audio_loopback` 系统服务，避开应用 linker namespace 限制，不依赖 Root 或 Xposed 权限 Hook。

### 状态显示

- Moonlight 会话建立和结束时立即刷新主页，正确显示“已连接”与“等待连接”。

### LSPosed 模块

- 模块仅修改 SmartisanOS PC 模式的虚拟显示包名判断，不处理系统音频权限。

## v0.9.6

### 初始化向导

- Android 8.1 设备打开启动向导时，提示坚果 R1 / 坚果 Pro2S 无头启动 TNT 需要启用 LSPosed Xposed 模块。
- 提示模块仅适用于 Android 8.1，其他 Android 版本无需启用。

## v0.9.5

### 音频

- `REMOTE_SUBMIX` 音频链路严格限制为 Android 8.1（API 27）。
- Android 9 及以上版本继续使用原生 `AudioPlaybackCapture` 链路。
- 高版本清理串流时不再修改 `REMOTE_SUBMIX` 全局路由。
- Android 8.1 路由建立失败时停止录音，不再继续使用可能无声的录音链路。

## v0.9.4

### LSPosed 模块

- Xposed hook 仅在 Android 8.1（API 27）的 `android` 进程中启用。
- 模块介绍改为中文，并明确仅适用于坚果 R1 / 坚果 Pro2S。

## v0.9.3

完善编码设置页与 native 编码参数的连接，默认值沿用当前 Android 8.1 固定配置。

### 编码设置

- 新增 H.264 Profile（Baseline/High）和 Level（4.2/5.1/5.2）手动配置。
- 码率模式、复杂度、低延迟、实时优先级等设置现在会写入 `MediaFormat`。
- 启动服务和恢复默认时统一使用设置页显示的默认编码参数。

## v0.9.2

本版本用于 Smartisan R2 Android 11 手动测试，暂时统一使用 Android 8.1 的视频编码参数配置。

### 编码

- 高版本沿用 Android 8.1 的 H.264 profile/level、关键帧、B 帧和色彩元数据配置行为。
- 移除高版本额外的 API 28+ 编码参数，便于验证 Android 8.1 编码配置在 Smartisan R2 上的可用性。

## v0.9.1

本次更新主要新增 Android 8.1（坚果 Pro2S）适配，并修复 SmartisanOS 8.1 下 Moonlight 黑屏、无声和键鼠注入问题。

### 新增

- 支持 Android 8.1 / SmartisanOS 8.1，minSdk 降至 27。
- 新增 LSPosed 模块，绕过 SmartisanOS 虚拟显示包白名单限制。
- Android 8.1 使用 `REMOTE_SUBMIX` 采集系统音频，保持媒体音量非零。
- Android 8.1 外接屏键鼠注入支持。

### 修复

- 修复 SmartisanOS 8.1 下 Moonlight 黑屏问题。
- 修复 PC 端 Moonlight 硬解黑屏：H.264 SPS 改写为 Constrained Baseline。
- 修复 Android 8.1 截图镜像取错 display token 的问题。

## v0.8.2

本次更新主要重构了 Moonlight 音频捕获链路，改善客户端无声音问题。

### 音频

- Moonlight 音频采集改为固定使用 Android 原生 `AudioPlaybackCaptureConfiguration`。
- 移除 `REMOTE_SUBMIX` 音频采集后端和对应设置项。

## v0.8.1

本次更新主要围绕 `UI` 细节、息屏体验和 `TNT` 拉起稳定性进行了完善。

### UI

- 新增竖条纹背景，进一步强化界面的层次感。
- 优化主页服务按钮的视觉表现，增强拟物质感。

### 修复

- 修复黑色画面模拟息屏功能不可用的问题。
- 修复自动息屏逻辑，当前会在客户端连接 `30` 秒后自动息屏。
- 重写 `TNT` 拉起流程，修复 `overlay` 方式无法被动拉起 `TNT` 的问题。

## v0.8.0

- `UI` 界面全面重构，整体风格更加贴近 `Smartisan`，更美观、更简洁，也更易用。

## v0.7.5

本次更新主要针对 `TNT` 启动、镜像链路、编码调优和后台稳定性进行改进。

### 新增

- 新增 Moonlight 握手调试入口，可查看最近一次连接的握手摘要并复制。
- 新增控制输入统计调试入口，可查看最近一次控制输入统计并复制。

### 优化

- 提高了编码和触控的性能与流畅度。
- 优化了后台运行时的 Wi-Fi 优先级，提高连接稳定性。
- 新增 `Smartisan` 后台串流性能模式，用于在后台或息屏时维持 Sunshine 会话的运行 cpuset 优先级。
- 编码链路新增“动态帧率”开关；关闭后可使用固定帧节奏，改善低画面变化场景下的控制响应。
- `TNT` 与镜像模式的启动链路进一步分流：`TNT` 模式与镜像模式分别走各自更稳定的显示创建与捕获路径。
- 串流调试面板改为展示最近 1 秒的实时统计，便于观察编码、发送和帧复制状态。

### 修复

- 修复镜像模式误触发 `TNT` 启动、错误创建应用侧显示器的问题。
- 修复镜像模式画面不出图的问题，改为通过 `Shizuku/UserService` 镜像主屏显示。
- 修复 Moonlight 音频链路中 `shouldMute` 被误当成“是否发送音频”的问题，恢复镜像模式音频输出。
- 修复部分情况下息屏按钮会消失。

## v0.7.2

- 修复刚开机、尚未连接过 `TNT` 时无法启动服务的 Bug。

## v0.7.1

- 新增鼠标模拟触控功能，可以增强一些情况下的兼容性。

## v0.7.0

- 更换包名为 `com.smartisanos.tntanywhere`。
- 使用 Smartisan OS 8.5 原生方案，实现免 Root 无头启动 `TNT`。
- 支持在客户端连接或断开时自动开关 `TNT`。
- 支持在 `TNT` 启动时自动适应客户端分辨率。
