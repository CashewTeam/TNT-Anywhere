# TNT Anywhere Agent Guide

## 1. 项目目标与一句话定位

`TNT Anywhere` 是一个面向 `SmartisanOS / TNT / Moonlight` 的 Android 串流与系统能力整合项目：它把 Android App、Shizuku、隐藏 API、VirtualDisplay、MediaCodec 和 native Sunshine 风格服务串在一起，让设备既能启动和承载 `TNT`，也能作为可被 Moonlight 连接和操控的主机。

这不是普通的“投屏 App”仓库。很多功能依赖 SmartisanOS 私有行为、Android 隐藏接口、Shizuku 用户服务，以及 Java 和 native 间的紧耦合桥接。

## 2. 仓库结构与模块职责

- `app-mirror`
  - 当前主开发模块，也是绝大多数功能改动的落点。
  - 负责 UI、任务调度、Shizuku 服务接入、TNT/镜像链路选择、Sunshine 会话桥接、输入回流和调试面板。
- `hidden-api-stub`
  - 为隐藏 API 提供编译期声明。
  - 改这里通常意味着系统接口有新增或签名需要补齐。
- `app-extend`
  - 原项目遗留的扩展屏相关模块。
  - 可以参考历史做法，但当前主链路不在这里。
- `termux-x11-app` / `termux-x11-shell-loader`
  - 与 Termux X11 相关。
  - 只有在处理 `Termux X11` 投屏或联动逻辑时才需要进入。

默认假设：如果需求与当前 TNT / Moonlight / Sunshine 主链路有关，优先看并修改 `app-mirror`。

## 3. 关键入口与阅读顺序

首次进入仓库时，建议按下面顺序建立上下文：

1. `README.md`
   - 看当前产品定位、兼容性、模块说明和已有专项文档入口。
2. `app-mirror/src/main/java/com/connect_screen/mirror/State.java`
   - 全局状态中心，很多任务、服务、显示器和调试状态都从这里串起来。
3. `app-mirror/src/main/java/com/connect_screen/mirror/MirrorMainActivity.java`
   - 主页主控制逻辑，适合先理解当前用户入口和运行态 UI。
4. `app-mirror/src/main/java/com/connect_screen/mirror/MirrorSettingsActivity.java`
   - 设置项最多，很多行为开关都在这里接入。
5. `app-mirror/src/main/java/com/connect_screen/mirror/job/ProjectViaMoonlight.java`
   - Moonlight 会话实际启动时的总入口，决定 TNT 模式和镜像模式怎么分流。
6. `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineServer.java`
   - Java 和 native Sunshine 的桥接层，很多会话事件、调试信息和资源创建都经过这里。
7. `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java`
   - Shizuku 用户服务实现，负责很多系统级动作，例如创建镜像、控制显示、亮灭屏等。
8. `app-mirror/src/main/java/com/connect_screen/mirror/job/CreateVirtualDisplay.java`
   - 统一的虚拟显示创建工具，跨 Android 版本兼容逻辑集中在这里。
9. `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java`
   - 当前默认 TNT 启动辅助实现。
10. `app-mirror/src/main/cpp/sunshine.cpp`
    - native 串流主逻辑入口，处理会话、编码、回调、输入统计和 JNI 桥接。

如果是 UI 改动，再补看：

- `app-mirror/src/main/res/layout/activity_main.xml`
- `app-mirror/src/main/res/layout/activity_mirror_settings.xml`
- `UI_ELEMENT_INVENTORY.md`

## 4. 典型工作流

### 改 UI / 文案

- 先确认目标页在 `activity_*.xml`、`fragment_*.xml` 还是弹窗 `dialog_*.xml`。
- 再看对应的 `Activity` 或 `Fragment` 是否有动态文案覆盖、显隐逻辑、运行态刷新。
- 同步检查主页、设置页、调试面板里的相关状态文案是否需要一起调整。
- 如果需求涉及 UI 重构或清点现状，优先参考 `UI_ELEMENT_INVENTORY.md`。

### 改 TNT 启动 / 显示链路

- 先确认需求属于 `TNT 模式` 还是 `镜像模式`，两条链路不能混着改。
- TNT 主链路优先看：
  - `ProjectViaMoonlight`
  - `TntDebugVirtualDisplayHelper`
  - `CreateVirtualDisplay`
  - `UserService`
- 如果是“显示器为什么没被 TNT 识别”这一类问题，先看 display owner、displayId、layerStack、是否由 Shizuku / UserService 创建。
- 旧 overlay/root 方案只是备用调试入口，不要把它误改成默认主链路。

### 改 Moonlight / 编码 / 输入回流链路

- 会话入口和 Java 桥接先看 `SunshineServer`。
- 编码、网络、输入统计、握手参数等 native 逻辑优先看 `sunshine.cpp`，以及同目录下的 `video.cpp`、`audio.cpp`、`input.cpp`、`stream.cpp`、`stat_trackers.cpp`。
- 如果 native 把某个语义传回 Java，修改时一定同时核对 JNI 参数含义，不要只看变量名猜意思。
- 输入问题要分清楚是“客户端发来的原始协议包问题”、还是“Java/native 过滤与映射问题”、还是“系统注入阶段问题”。

## 5. 高风险区与项目约束

- `com.smartisanos.tntanywhere` 包名不是普通命名调整项。
  - 它和 SmartisanOS 对 TNT Anywhere 的识别有关，不能随意改回旧包名或做无意重构。
- Shizuku / hidden API / `UserService` 链路是系统能力基础设施。
  - 这里的改动会直接影响显示器创建、镜像、灭屏、输入和权限路径。
- TNT 模式和镜像模式必须严格分流。
  - 修改一条链路时，要确认不会意外触发另一条。
- 音频链路里，“是否静音本机播放”和“是否向客户端发送音频”不是一回事。
  - native 回调语义和 Java 侧字段含义必须一一对应。
- Smartisan 后台性能模式是当前稳定性关键项之一。
  - 相关逻辑涉及进程 cpuset boost，不要在不了解回收时机的前提下随意移动或合并。
- 显示相关调试时，优先关注这些信号：
  - `displayId`
  - `ownerPackageName`
  - `layerStack`
  - 是否进入 `pc:true`
  - 是否是 `100000+` 区间外接 / 虚拟显示
- 改 native / Java 桥接时，务必同时核对：
  - Java 方法签名
  - JNI 注册与调用点
  - native 侧字段语义
  - 调试面板展示是否需要同步更新

## 6. 常用验证命令

最常用的最小验证命令：

```bash
bash ./gradlew :app-mirror:assembleDebug
```

只构建主模块时也优先使用：

```bash
bash ./gradlew :app-mirror:assembleDebug
```

如果需要顺手检查整个工程的 Gradle 配置是否还能解析：

```bash
bash ./gradlew tasks
```

排查运行时问题时，常见关注方向是：

- `logcat` 中是否进入了预期任务，例如 `ProjectViaMoonlight`
- `SunshineServer` 是否收到了握手、显示器创建、停止会话等关键回调
- `UserService` 是否成功执行显示镜像或显示器创建
- 调试面板里的 encoder / pipeline / frame pacer / control input 数据是否符合预期
- 必要时再结合 `dumpsys display`、`dumpsys activity`、`dumpsys power`、`dumpsys wifi` 看系统态

## 7. 文档索引

进入专项问题前，优先查这些文档：

- `README.md`
  - 项目介绍、兼容性、总体架构和模块说明。
- `CHANGELOG.md`
  - 近期版本演进，适合判断某条行为是不是最近改出来的。
- `TNT_ACTIVATION_INVESTIGATION.md`
  - TNT 启动流程逆向记录。
- `TNT_OVERLAY_DISPLAY_DEBUG_GUIDE.md`
  - 旧 overlay 调试链路说明。
- `SMARTISANOS_PRIVATE_API_SUMMARY.md`
  - SmartisanOS 私有 API、状态检测、TNT 窗口行为接口汇总。
- `UI_ELEMENT_INVENTORY.md`
  - 当前 UI 元素和文案对照表，适合做 UI 重构、清点和批量改文案。

## 8. Agent 行为建议

- 优先做小改动、局部验证，不要在同一轮里同时重写 UI、显示链路和 native 桥接。
- 开始改之前，先确认问题属于：
  - TNT 模式
  - 镜像模式
  - DisplayLink / 单应用投屏
  - Moonlight 协议与会话
- 改 UI 时，别只改布局文件。
  - 还要检查对应 Activity、动态状态刷新、设置项持久化和调试面板文案。
- 改设置开关时，确认：
  - `Pref` 默认值
  - 设置页读写逻辑
  - 实际任务执行点是否读取了该值
- 改 Java/native 交互时，优先加最小量、可定位的日志或调试字段，避免盲改。
- 如果问题涉及 SmartisanOS 特性，优先假设“系统识别条件”可能是问题根源，而不是先怀疑普通 Android API 行为。
- 如果某个现象只在后台、息屏、TNT 已启动、或特定客户端出现，记录触发条件并按场景拆解，不要合并成一个笼统问题。

## 快速结论

这个仓库最重要的事实是：`app-mirror` 是主战场，`ProjectViaMoonlight -> SunshineServer -> UserService / CreateVirtualDisplay -> native sunshine.cpp` 是最常见的主链路。  
多数功能问题都不是单点 UI Bug，而是“设置项、任务调度、系统能力、native 会话”几层一起作用的结果。先分清模式，再沿链路查，通常比直接改代码更快。
