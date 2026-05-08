# TNT Shaker

## 项目简介

`TNT Shaker` 是一个运行在 Android 11 设备上的屏幕投屏与串流项目，面向 SmartisanOS 8.5.3 这类基于 Android 11 的系统环境。

它的核心目标是：

1. 通过 `Shizuku` 获取更高权限，调用系统级接口完成虚拟显示、屏幕镜像、输入绑定等操作。
2. 参考 `scrcpy` 在 Android 11 上的屏幕采集思路，使用 `MediaCodec` / `VirtualDisplay` 等方式拿到画面。
3. 将手机镜像画面或外接显示器画面，通过 `Moonlight` 所使用的 NVIDIA GameStream 协议进行串流输出。
4. 在接收端实现触控、键鼠等输入控制，让客户端既能看画面，也能反向操控手机。

这个项目可以理解为一套“手机端自建的 Sunshine 风格服务端”，但它不是桌面端的 Sunshine 官方实现，而是针对 Android 设备做了适配和封装。

## 软件作用

`TNT Shaker` 主要解决三类需求：

1. 手机画面串流到 Moonlight 客户端
   - 支持把当前手机屏幕编码后送给客户端。
   - 支持镜像模式下的本机显示画面输出。
   - 支持外接显示器画面的镜像输出。

2. 基于权限能力的系统控制
   - 通过 `Shizuku` 间接获得 `ADB` 级别能力。
   - 调用隐藏 API / 系统服务完成虚拟显示创建、显示旋转、输入绑定、屏幕电源控制等动作。

3. 输入回流
   - 将 Moonlight 客户端发来的输入事件映射回 Android 设备。
   - 包括触摸、鼠标、键盘等输入通道。

## 总体架构

项目整体可以分成四层：

1. UI 和状态层
   - 负责界面、配置、任务状态和日志展示。
   - 主要代码在 `app-mirror/src/main/java/com/connect_screen/mirror/`。

2. 投屏任务层
   - 以 `Job` 为抽象，把不同场景封装为任务。
   - 例如本机镜像、外接显示器镜像、DisplayLink 投屏、Shizuku 权限获取等。

3. 串流与编码层
   - `SunshineServer` 负责和 native 代码桥接。
   - native 侧完成 Moonlight 协议、RTSP/HTTP、音视频编码、RTP 发送等工作。

4. 系统权限与隐藏 API 层
   - `Shizuku` 绑定 `UserService`。
   - 通过封装后的隐藏 API 完成显示创建、投屏、输入路由、亮灭屏控制等系统级动作。

## 模块划分

### `app-mirror`

这是当前项目的核心模块，负责 Moonlight 投屏主链路。

主要职责：

1. 启动和维护串流服务。
2. 创建镜像显示或外接显示器镜像。
3. 建立 Moonlight 协议所需的服务器能力。
4. 处理客户端输入与设备侧响应。

关键类：

- `State`
  - 全局状态中心，维护当前任务、日志、`Shizuku` 服务、虚拟显示等。

- `ProjectViaMoonlight`
  - Moonlight 串流任务入口。
  - 决定走本机镜像还是外接显示器镜像。

- `SunshineServer`
  - Java 与 native 的桥接入口。
  - 负责启动 native Sunshine 服务、创建虚拟显示、停止投屏等。

- `UserService`
  - `Shizuku` 绑定后的系统服务实现。
  - 提供创建显示、外接显示器镜像、屏幕电源控制、音频读取等能力。

- `CreateVirtualDisplay`
  - 统一创建虚拟显示的工具类。
  - 负责在不同 Android 版本上选择合适的创建路径。

### `app-extend`

这是扩展屏相关模块，偏向“外接显示器/第二屏/输入绑定”能力。

主要职责：

1. 维护外接屏配置。
2. 创建和管理扩展显示。
3. 处理外接输入和显示参数调整。

### `hidden-api-stub`

用于承接 Android 隐藏 API 的编译期声明。

作用：

1. 让项目可以编译对接系统内部类和方法。
2. 配合 `refine` / `hiddenapibypass` / `Shizuku` 使用系统能力。

### `scrcpy-master`

仓库里保留了 `scrcpy` 源码参考目录，用于对照 Android 11 采集和编码实现思路。

它更像是参考实现和能力对照，不是当前主链路的唯一入口。

## 运行流程

### 1. 启动与权限准备

1. App 启动后进入主界面。
2. 通过 `Shizuku` 绑定 `UserService`。
3. 按需申请或确认系统级权限。
4. 初始化 Moonlight 串流所需的 native 服务。

### 2. 发起连接

1. Moonlight 客户端连接到设备。
2. `SunshineServer` 在 native 层完成会话建立。
3. 进入 `ProjectViaMoonlight` 任务。

### 3. 选择镜像模式

项目会根据配置和设备状态分成两条路径：

1. 本机镜像模式
   - 创建虚拟显示。
   - 把手机主屏内容编码后发送给客户端。

2. 外接显示器镜像模式
   - 通过 `Shizuku` 调用系统接口。
   - 创建外接显示器的镜像输出。

### 4. 采集、编码、发送

1. 屏幕内容进入 `MediaCodec` 编码器。
2. native 层从编码器输出获取 H.264 / HEVC 数据。
3. 数据被封装为 Moonlight / Sunshine 协议帧。
4. 通过 RTP / RTSP 相关网络通道发送给客户端。

### 5. 输入回流

1. 客户端输入事件到达服务端。
2. native / Java 层根据事件类型分发。
3. 触摸、键盘、鼠标输入被映射到 Android 输入系统。

## 架构上的关键点

1. `TNT Shaker` 的核心不是“纯应用层投屏”，而是“应用层 + 系统权限 + native 串流”的混合架构。
2. `Shizuku` 是系统能力的开关，没有它，很多显示和输入操作会降级或失败。
3. `Moonlight` 只是客户端协议名，服务端实际上是 Android 端自己实现的一套 Sunshine 风格串流服务。
4. 视频链路最敏感的部分是：
   - 虚拟显示是否创建成功
   - 编码器是否启动成功
   - 首帧是否成功送出
   - JNI 线程生命周期是否正确

## 目录速查

- `app-mirror/src/main/java/com/connect_screen/mirror/State.java`
  - 全局状态和任务调度中心。

- `app-mirror/src/main/java/com/connect_screen/mirror/job/ProjectViaMoonlight.java`
  - Moonlight 串流任务入口。

- `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineServer.java`
  - Java 到 native 的桥接层。

- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java`
  - Shizuku 系统服务实现。

- `app-mirror/src/main/cpp/sunshine.cpp`
  - native 串流主逻辑，包含编码、Surface、JNI 和回调处理。

- `app-mirror/src/main/java/com/connect_screen/mirror/job/CreateVirtualDisplay.java`
  - 虚拟显示创建工具。

## 简短结论

`TNT Shaker` 的本质，是把 Android 设备当成一个可被 Moonlight 连接的串流主机：用 `Shizuku` 提供系统权限，用 `VirtualDisplay` / `MediaCodec` 获取画面，用 native Sunshine 风格协议把画面送出去，再把输入送回来。

## 原项目简介

`TNT Shaker` 继承自原始项目“安卓屏连”的思路。原项目的定位是让 Android 手机通过有线或无线方式连接屏幕、电脑和外接设备，补足部分厂商阉割掉的投屏与显示能力。

它解决的典型问题包括：

1. 某些设备把 `USB 3` 限制成 `USB 2` 后，难以获得完整的满屏投屏体验。
2. 某些系统删除或限制了原生桌面模式、双屏异显等能力。
3. 很多手机自带的投屏方案偏向同品牌或固定场景，跨设备兼容性不够好。

原项目的思路很直接，就是把厂商弱化掉的“接屏幕能力”尽量补回来。

## 参考链接

### 原项目对外链接

- 用户手册: [https://connect-screen.com/](https://connect-screen.com/)
- 小红书: [安卓屏连](https://www.xiaohongshu.com/user/profile/602cc4c0000000000100be64)
- B 站: [安卓屏连](https://space.bilibili.com/494726825)
- 抖音: [安卓屏连](https://www.douyin.com/user/MS4wLjABAAAAolJRQWuFI6KZwaBUvPfzDejygnorK2K-CY_6b1OuWQM)
- YouTube: [安卓屏连](https://www.youtube.com/@connect-screen)

### 原项目参考资料

- [nightmare.press](http://nightmare.press/)
- [Easycontrol](https://github.com/eiyooooo/Easycontrol)
- [Easycontrol_For_Car](https://github.com/eiyooooo/Easycontrol_For_Car)
- [awesome-shizuku](https://github.com/timschneeb/awesome-shizuku)
- [scrcpy](https://github.com/Genymobile/scrcpy)
- [scrcpy-mask](https://github.com/AkiChase/scrcpy-mask/blob/master/README-zh.md)
- [ScreenStream](https://github.com/dkrivoruchko/ScreenStream)
- [ActivityManager](https://github.com/sdex/ActivityManager)
- [SecondScreen](https://github.com/farmerbb/SecondScreen)
- [gkd Shizuku UserService](https://github.com/jiuqianyuan/gkd/blob/main/app/src/main/kotlin/li/songe/gkd/shizuku/UserService.kt)
- [Flyme-FreeForm](https://github.com/Live-Block/Flyme-FreeForm/blob/flyme/app/src/main/java/com/sunshine/freeform/ui/freeform/FreeformService.kt)
- [Android-SettingTools](https://github.com/MagicianGuo/Android-SettingTools)
- [Android-Screener](https://github.com/jiesou/Android-Screener)
- [AndroidQuestions Miracast thread](https://www.reddit.com/r/AndroidQuestions/comments/xra3hu/is_there_a_technical_reason_there_are_no_miracast/)
- [zuojie blog](https://www.cnblogs.com/zuojie)
- [SceenLive VideoCodec](https://github.com/itzuo/SceenLive/blob/master/app/src/main/java/com/zxj/screenlive/VideoCodec.java)
- [AndroidUHidPureJava](https://github.com/WuDi-ZhanShen/AndroidUHidPureJava)
- [ScreenOff](https://github.com/WuDi-ZhanShen/ScreenOff)
- [CSDN article](https://blog.csdn.net/liaosongmao1/article/details/136129774)
- [KeyMapper](https://github.com/keymapperorg/KeyMapper/)
