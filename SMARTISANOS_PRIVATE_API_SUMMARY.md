# SmartisanOS 私有 API 与状态检测汇总

本文档汇总了 `TNT Anywhere` 当前已经接入、以及前期逆向和源码排查中确认存在的 `SmartisanOS` 私有 API、系统状态、调试信号与关键规则，方便后续继续做：

- `TNT` 启动与退出联动
- 镜像 / 串流兼容性排查
- 后台性能优化
- 新机型 / 新系统适配

## 1. 范围说明

这里的“私有 API / 状态”分为三类：

1. 项目当前已经实际调用的接口或状态。
2. 项目当前只用于检测、调试或兼容判断的隐藏字段 / 信号。
3. 已从 `smartisanos-src` 源码中确认存在，但当前项目还没有直接接入的 Smartisan 专有服务、规则和全局状态。

另外要说明一点：

- 项目里很多关键能力并不都是 `SmartisanOS` 独有接口。
- 实际链路通常是 `Android hidden API + Shizuku/SystemService + Smartisan 私有规则` 组合在一起。
- 对 `TNT` 来说，真正决定系统是否认它、是否进入 `PC mode` 的，往往是 Smartisan 的私有规则，而不是单个 Android 隐藏接口本身。

## 2. 当前项目已实际接入的相关接口

### 2.1 Smartisan 进程性能扩展

用途：

- 解决 App 进入后台或系统息屏后，串流编码/采集线程被系统降权，导致帧率明显下滑的问题。

当前调用点：

- `app-mirror/src/main/java/com/connect_screen/mirror/SmartisanPerformanceHelper.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/job/SunshineServer.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/SunshineService.java`

调用方式：

- 通过反射获取 `android.app.ActivityManager.getSmtEx()`
- 再调用 `android.app.ActivityManagerSmtEx.setProcessRunningCpuset(pid, cpusetLevel, timeout, force)`

当前项目中的使用方式：

- Sunshine 会话开始时申请 `cpuset boost`
- Sunshine 会话结束或服务销毁时撤销
- 当前使用的是 `cpusetLevel = 1`

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/app/ActivityManager.java`
- `smartisanos-src/frameworkjar/sources/android/app/ActivityManagerSmtEx.java`
- `smartisanos-src/frameworkjar/sources/android/app/ActivityManagerSmtBase.java`
- `smartisanos-src/servicesjar/sources/com/android/server/am/ActivityManagerServiceSmtBase.java`
- `smartisanos-src/servicesjar/sources/com/android/server/am/ProcessRecordSmtBase.java`

已确认的结论：

- 这是 Smartisan 自己扩展出来的 AMS 能力，不是标准 AOSP 公共 API。
- 它本质上会影响进程的运行期 cpuset / sched group。
- 这条接口对本项目的后台串流性能有实际帮助，已经验证可用。

### 2.2 以 `com.smartisanos.tntanywhere` 身份进入 TNT 识别链路

用途：

- 让系统把我们创建 / 持有的显示链路纳入 `TNT Anywhere` 相关识别路径。

当前接入点：

- `app-mirror/build.gradle`

当前包名：

- `com.smartisanos.tntanywhere`

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtils.java`
- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsInner.java`

已确认的结论：

- `SmartisanOS` 框架内部明确存在 `TNT_ANYWHERE_DISPLAY_PKG = "com.smartisanos.tntanywhere"`。
- 包名命中后，系统会把它视作 TNT 相关显示身份的一部分。
- 但“包名命中”不等于“进程自动获得完整 PC 前台身份”，这两件事要分开看。

### 2.3 Android 11 / 12 上的显示镜像隐藏接口

用途：

- 在不依赖传统 App overlay 链路的情况下，把主屏或外接屏画面镜像到 Sunshine 编码 `Surface`。

当前实现位置：

- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/SurfaceControl.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/DisplayManager.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/job/ProjectViaMoonlight.java`

当前实际使用的两条路径：

1. Android 12+ / API 31+
   - 通过隐藏重载 `DisplayManager.createVirtualDisplay(String name, int width, int height, int displayIdToMirror, Surface surface)`
   - 用于把指定 display 直接镜像到编码 `Surface`

2. Android 11 / API 30
   - 通过 `SurfaceControl.createDisplay(...)`
   - 再配合：
     - `setDisplaySurface(...)`
     - `setDisplayProjection(...)`
     - `setDisplayLayerStack(...)`
     - `setDisplayPowerMode(...)`
   - 手动拼出镜像显示

关键依赖状态：

- `IDisplayManager.getDisplayInfo(displayId)`
- `DisplayInfo.layerStack`

补充兼容逻辑：

- 如果 `DisplayInfo.layerStack` 无法直接读到，会退回 `dumpsys display` 解析。

已确认的结论：

- 这组接口不属于 Smartisan 私有 API，但它们是当前 TNT / 镜像串流链路的底层基础。
- 真正让 TNT 被激活的，不是这些接口本身，而是这些显示结果是否满足 Smartisan 的 TNT 规则。

### 2.4 App 内部的 TNT 基础显示创建

用途：

- 在客户端请求启动 `TNT` 时，先创建一个基础显示，让系统后续有机会在其上派生出 TNT 虚拟显示。

当前实现位置：

- `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java`

关键点：

- 显示名称：`tntanywhere.base.display`
- 通过标准 `DisplayManager.createVirtualDisplay(...)` 创建
- 会记录 display `name / type / ownerPackageName / size / flags`

当前项目中的作用：

- 它不是官方 TNT 系统服务入口。
- 它更像是当前项目为触发 Smartisan TNT 识别链路而构造的“基础显示”。

## 3. 当前项目已在用的隐藏字段与状态检测

### 3.1 `Display.getOwnerPackageName()`

用途：

- 判断某个显示当前归属哪个包名。
- 用于排查系统是否把某块显示当成 `TNT Anywhere`、`wirelesscast`、或其它 Smartisan 内部路径创建的显示。

当前调用点：

- `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java`

说明：

- 这是通过反射取到的隐藏方法。
- 在 TNT 激活问题排查里很有价值，因为 Smartisan 对“显示 owner 包名”非常敏感。

### 3.2 `Display.getType()`

用途：

- 辅助判断当前 display 属于内屏、外接、虚拟显示或特定类型。

当前调用点：

- `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java`

说明：

- 项目当前主要把它作为调试信息输出，不直接作为 TNT 最终判断依据。

### 3.3 `IDisplayManager.getDisplayInfo()` 与 `DisplayInfo.layerStack`

用途：

- 在 Android 11 上拼装 `SurfaceControl` 镜像时，必须拿到目标 display 的 `layerStack`。

当前调用点：

- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java`

兼容逻辑：

- 先尝试直接访问 `android.view.DisplayInfo.layerStack`
- 若失败，再从 `dumpsys display` 中回退解析

说明：

- `layerStack` 对 API 30 的镜像链路是关键条件之一。
- 之前镜像失败过一次，就是因为运行时服务还在用旧逻辑，把 `layerStack=0` 错误当成非法值。

### 3.4 以“最大 displayId”近似选择 TNT / 外接显示

用途：

- 当前项目里在 TNT 模式下，会优先选取最大的 displayId 作为当前外接 / TNT 目标显示。

当前实现位置：

- `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDisplaySelector.java`

说明：

- 这不是 Smartisan 官方 API，只是当前项目基于设备行为总结出来的经验规则。
- 在目标系统上，`100000+` 的 TNT 显示器通常天然会比普通显示 ID 更大，所以这个规则在现阶段是有效的。
- 它本质是“经验性检测”，不是系统正式契约。

### 3.5 `dumpsys activity processes` 中的 `pc:true`

用途：

- 判断某个进程是否真的被系统归类为 `PC mode` 进程。

当前使用方式：

- 这是人工调试信号，不是项目代码直接读取的字段。
- 但它是判断“进程身份是否真的进了 TNT/PC 语义”的最关键线索之一。

已确认的经验结论：

- 当 App 的 `Activity` 真正启动到 TNT 端时，更容易出现 `pc:true`
- 但当前 Sunshine 主链路长期承载的是手机侧进程，因此很多时候只表现为 `fg-service`，不会自动变成 `pc:true`

## 4. TNT 窗口行为定义接口（第三方开发者相关）

这部分是从 `frameworkjar + smartisan-framework-tnt + smartisan-services-tnt` 三层源码里整理出来的，比较接近当年面向第三方开发者的 `TNT` 窗口能力。

它不是单一一个 SDK，而是几组接口共同生效：

1. `AndroidManifest.xml` 里的 `meta-data` 静态参数。
2. `ActivityInfo / ApplicationInfo / Configuration` 上的 Smartisan 扩展字段。
3. `smt_pcm / ISmtPCManager / SmtPCUtils / TntManager` 这类运行时窗口控制接口。

### 4.1 `windowParams`：静态定义 TNT 窗口默认行为

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/content/pm/SmtWindowParams.java`
- `smartisanos-src/frameworkjar/sources/android/content/pm/parsing/PackageInfoWithoutStateUtils.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/SmtPCWindowDefaultConfig.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/SmtPCWindowManager.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/TntTaskImpl.java`

已确认的静态入口：

- 包级元数据会被解析到 `ApplicationInfo.mSmtWindowParams`
- Activity 级元数据会被解析到 `ActivityInfo.mSmtWindowParams`
- 两者都通过 `SmtWindowParams.obtain(Bundle metaData)` 读取
- 元数据键名固定为：`windowParams`

值格式：

- `windowParams = "ver,windowMode,resizeMode,forceResizeMode,width,height,minWidth,minHeight"`

字段顺序与含义：

1. `ver`
   - 配置版本号
   - 系统会拿它和 `/system/etc/revone_window_config.xml`、`/data/system/revone_window_config.xml` 里的版本做比较，优先使用版本更高的一侧
2. `windowMode`
   - 默认窗口模式
3. `resizeMode`
   - 拉伸/缩放能力位掩码
4. `forceResizeMode`
   - 是否强制支持某些窗口切换能力
5. `width`
   - 默认宽度
6. `height`
   - 默认高度
7. `minWidth`
   - 最小宽度
8. `minHeight`
   - 最小高度

尺寸单位说明：

- `width / height / minWidth / minHeight` 在系统侧会按 `dp` 解释，再由 `SmtPCWindowManager.dp2px(...)` 换算成像素
- 也就是说这些值不是直接写物理像素

优先级规则：

- Activity 级 `windowParams` 可以覆盖包级 `windowParams`
- 系统还允许用 `revone_window_config.xml` 对特定包或特定 Activity 做覆盖
- 最终由 `SmtPCWindowDefaultConfig` 按 `version` 选择“应用自带配置”还是“系统文件配置”

当前已能从源码确认的 `windowMode` 含义：

- `0`：竖屏窗口 `WINDOW_MODE_PORTRAIT`
- `1`：横屏窗口 `WINDOW_MODE_LANDSCAPE`
- `2`：最大化 `WINDOW_MODE_MAXIMIZED`
- `4`：全屏 `WINDOW_MODE_FULLSCREEN`
- `5`：左半屏
- `6`：右半屏
- `7 / 8 / 9 / 16`：四象限小窗
- `17`：上半屏
- `18`：下半屏
- `19`：竖屏最大化 `WINDOW_PORTRAIT_MAXIMIZED`
- `20`：竖屏密度缩放态 `WINDOW_PORTRAIT_DENSITY_SCALED`

当前已能从源码确认的 `resizeMode` 位含义：

- `0`：不可自由拉伸 `RESIZE_MODE_NONE`
- `1`：自由拉伸 `RESIZE_MODE_FREE`
- `2`：按高度维度缩放 `RESIZE_MODE_HEIGHT`
- `4`：支持全屏相关行为 `RESIZE_MODE_FULLSCREEN`
- `8`：保持比例缩放 `RESIZE_MODE_RATIO`

实际效果可以叠加：

- 例如 `1 | 8` 更像“允许拖拽缩放，但保持长宽比”
- 如果不带 `1`，系统会更倾向于把它当成固定形态窗口，而不是自由拉伸窗口

`forceResizeMode` 含义：

- `0`：`FORCE_RESIZE_MODE_NOT_SUPPORT`
- `1`：`FORCE_RESIZE_MODE_SUPPORT`

从系统实现看，它会影响：

- 窗口是否允许进入某些“强制切换”的形态
- 旋转、恢复和保存窗口尺寸时，系统是否把它当成“可强制调整”的任务处理

这些静态参数最终会影响：

- TNT 下首次打开时的默认横竖屏形态
- 默认窗口大小
- 最小窗口大小
- 是否允许自由拉伸
- 是否倾向于全屏 / 最大化 / 恢复
- Activity 切换后是否需要跟随请求方向重新排版窗口

### 4.2 `ActivityInfoSmt` / `ActivityInfoSmtBase`：Activity 级 Smartisan 扩展

源码锚点：

- `smartisanos-src/frameworkjar/sources/smartisanos/api/ActivityInfoSmt.java`
- `smartisanos-src/frameworkjar/sources/android/content/pm/ActivityInfoSmtBase.java`
- `smartisanos-src/frameworkjar/sources/android/content/pm/ActivityInfoSmtEx.java`
- `smartisanos-src/frameworkjar/sources/android/content/pm/ITntActivityInfo.java`

可确认的公开包装类：

- `smartisanos.api.ActivityInfoSmt`

当前能确认的关键能力：

- 提供 `SCREEN_ORIENTATION_APP_PORTRAIT = 15`
- 提供 `SCREEN_ORIENTATION_APP_LANDSCAPE = 16`
- 提供反向方向：
  - `SCREEN_ORIENTATION_APP_REVERSE_PORTRAIT = 17`
  - `SCREEN_ORIENTATION_APP_REVERSE_LANDSCAPE = 18`
- 提供 `orientationToString(...)`
- 可读取 `ActivityInfo.getSmtEx().userOrientation`
- 可读取 / 修改 `ActivityInfo.getSmtEx().chosenPriority`
- 可读取 `ActivityInfo.getSmtEx().smXMLFlags`

`ActivityInfoSmtBase` 里当前可见的扩展字段：

- `chosenPriority`
- `smXMLFlags`
- `userOrientation`
- `forceDisplayFlags`
- `autoDisplayFlags`

说明：

- `chosenPriority` 更偏向系统 resolver / 选择器优先级
- `userOrientation` 是用户或系统后续写入的方向偏好，不是标准 Android `screenOrientation`
- `forceDisplayFlags / autoDisplayFlags` 属于 Smartisan 的多显示 / 自动投放策略字段，但当前源码里没有像 `windowParams` 那样直观的开发者文档入口，更像系统内部扩展面

和配置变更相关的常量：

- `ITntActivityInfo.CONFIG_TNT_WINDOW_CONFIGURATION = 268435456`
- `ActivityInfoSmtEx.CONFIG_WINDOW_CONFIGURATION_EX = 67108864`

这说明：

- `TNT` 窗口形态变化本身是会进入 Activity 配置变更语义的
- 如果 App 想在 TNT 环境里更精细地响应窗口状态变化，后续可以继续围绕这两组 config change 位深挖

### 4.3 `ApplicationInfoSmtBase`：应用级 Smartisan 标记

源码锚点：

- `smartisanos-src/frameworkjar/sources/smartisanos/api/ApplicationInfoSmt.java`
- `smartisanos-src/frameworkjar/sources/android/content/pm/ApplicationInfoSmtBase.java`
- `smartisanos-src/frameworkjar/sources/android/content/pm/ApplicationInfoSmtEx.java`

和 TNT / 多显示比较相关的字段：

- `SMARTISAN_FLAG_ACT_CAN_MOVED_TO_DIFF_DISPLAY = 16384`
- `SMARTISAN_FLAG_FROM_PC = 1024`
- `SMARTISAN_FLAG_TNT_COMPATIBILITY_MODE = 2048`
- `PEROPT_PACKAGE_HIGH_PRIORITY_FOR_PC = 67108864`
- `forceDisplayFlags`
- `autoDisplayFlags`

说明：

- 这些字段里，部分明显是系统运行态标记，不是给普通第三方 App 随便写 manifest 就能稳定驱动的能力
- 但它们说明 Smartisan 在应用级确实维护了一套“是否来自 PC / 是否具备 TNT 兼容模式 / 是否应拿更高 PC 优先级”的扩展状态

补充状态键：

- `smartisanos.api.SettingsSmt.Global.TNT_COMPATIBILITY_MODE`
- `android.provider.SettingsSmtEx.Global.TNT_COMPATIBILITY_MODE`

这更像系统层的兼容模式开关，而不是单 Activity 的窗口尺寸配置。

### 4.4 运行时窗口控制接口：`SmtPCUtils` / `ISmtPCManager`

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtils.java`
- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java`
- `smartisanos-src/frameworkjar/sources/android/pc/ISmtPCManager.java`
- `smartisanos-src/frameworkjar/sources/smartisanos/tnt/TntManager.java`

服务来源：

- binder service 名称固定为：`smt_pcm`
- `SmtPCUtils.getSmtPCManager()` / `SmtPCUtilsSmtBase.getSmtPCManager()` 都是通过 `ServiceManager.getService("smt_pcm")` 获取

这组接口不是“定义默认窗口参数”，而是“App 在 TNT 运行后动态控制窗口”的运行时 API。

当前已确认的常用能力：

- `SmtPCUtils.smtFullscreenWindow(IBinder token, boolean)`
- `SmtPCUtils.smtFullscreenOrNot(IBinder token)`
- `SmtPCUtils.smtMaximizeWindow(IBinder token, boolean)`
- `SmtPCUtils.smtMaximizeOrNot(IBinder token)`
- `SmtPCUtils.smtResizeTask(int taskId, Rect bounds)`
- `SmtPCUtils.resizeTaskToTargetWindowMode(int taskId, int targetWindowMode)`
- `SmtPCUtils.smtRestoreTask(int taskId, float x, float y)`
- `SmtPCUtils.getDefaultWindowBounds(boolean isPortrait)`
- `SmtPCUtils.getDefaultWindowMode(IBinder token)`
- `SmtPCUtils.getDefaultWindowModeOfTopActivityByTaskId(int taskId)`
- `SmtPCUtils.getTaskMaximizedBounds(int taskId)`
- `SmtPCUtils.getTaskRestoredBounds(int taskId)`
- `SmtPCUtils.smtPinTask(int taskId, boolean)`
- `SmtPCUtils.smtIsPinnedTask(int taskId)`
- `SmtPCUtils.setPackageDensity(int taskId, float density)`

`ISmtPCManager` 里还可以看到对应的底层 Binder 方法：

- `smtFullscreenWindow`
- `smtMaximizeWindow`
- `smtResizeTask`
- `resizeTaskToTargetWindowMode`
- `getBoundsForTaskId`
- `getDefaultWindowBounds`
- `getDefaultWindowMode`
- `getTaskMaximizedBounds`
- `getTaskRestoredBounds`
- `setPinStack`
- `setPackageDensity`

对于第三方开发者来说，这说明：

- Smartisan 当年确实不只是给了“静态窗口参数”
- 还提供了运行时把 TNT 窗口切换到全屏、最大化、固定模式、恢复模式、置顶 pin 等的控制入口

### 4.5 面向 App 的 TNT 运行态回传：`Configuration.windowConfiguration.getTnt()`

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/app/ITntWindowConfiguration.java`
- `smartisanos-src/smartisan-framework-tnt/sources/android/app/TntWindowConfigurationImpl.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/TntConfigurationContainerImpl.java`
- `smartisanos-src/frameworkjar/sources/smartisanos/tnt/TntManager.java`

当前能确认，App 运行在 TNT 下时，系统会把一组 TNT 扩展状态挂到：

- `Configuration.windowConfiguration.getTnt()`

其中包含：

- `getScreenMode()`
- `getDisplayId()`
- `getTaskWindowState()`
- `getStackMode()`
- `isInPinStack()`
- `isAlwaysOnTop()`

关键常量：

- `SCREEN_MODE_PC = 2`
- `STACK_MODE_PIN = 2`

实际意义：

- App 可以知道自己是不是正运行在 `PC/TNT` 模式
- 可以拿到当前承载自己的 displayId
- 可以读取当前任务窗口状态 `taskWindowState`
- 可以知道自己是不是被 pin 住、是不是 always-on-top

`smartisanos.tnt.TntManager` 还额外提供了较上层的便捷判断：

- `isPcMode(Configuration)`
- `setPinStack(View, boolean)`
- `setPinStack(int taskId, boolean)`
- `isInPinStack(View)`
- `getRunningTasks(...)`

对于做 TNT 适配的 App 来说，这部分更像“运行态检测接口”。

### 4.6 系统侧如何消费这些参数

源码锚点：

- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/TntTaskImpl.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/SmtPCWindowManager.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/SmtPCWindowDefaultConfig.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java`

已确认的系统消费链路：

- `TntTaskImpl` 持有：
  - `mApplicationParams`
  - `mActivityParams`
- `SmtPCWindowManager.getComponentName(..., params)` 会把包级和 Activity 级 `SmtWindowParams` 一起取出来
- `SmtPCWindowDefaultConfig` 负责合并：
  - App 自带 `windowParams`
  - Activity 自带 `windowParams`
  - 系统文件 `revone_window_config.xml`
- `SmtPCWindowManager.getMinSize(...)` 会把 `minWidth / minHeight / width / height` 真正转成 TNT 下的最小窗口尺寸
- `SmtPCWindowManager.getBoundsByPkgName(...)` / `getDefaultBounds(...)` / `getWindowBounds(...)` 会据此生成默认窗口大小
- `TntTaskImpl.initTaskSize()` 会在任务首次进入 TNT 时套用默认窗口状态与尺寸
- `SmtPCWindowManager.resizeTaskByRequestedOrientation(...)` 会在顶部 Activity 请求横竖屏变化时，结合这些规则重新调整 TNT 窗口

可直接得出的结论：

- Smartisan 当年的 TNT 适配能力，核心不是一组“神秘黑箱策略”
- 它确实把“默认窗口模式、默认大小、最小大小、可否拉伸、运行时全屏/最大化/Pin、当前 TNT 窗口状态回传”做成了一套完整接口面
- 其中最接近“第三方开发者文档”的静态入口，就是 `windowParams`
- 最接近“运行时控制 API”的入口，就是 `SmtPCUtils / ISmtPCManager / TntManager`

## 5. 已从系统源码确认、但当前项目未直接接入的 Smartisan 专有能力

### 5.1 `smt_pcm` 系统服务

服务名：

- `smt_pcm`

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java`
- `smartisanos-src/frameworkjar/sources/android/pc/ISmtPCManager.java`

说明：

- `SmtPCUtilsSmtBase.getSmtPCManager()` 会通过 `ServiceManager.getService("smt_pcm")` 获取 binder。
- 对应的服务接口是 `android.pc.ISmtPCManager`。
- `TntManagerService` 就是这条服务链的重要实现方之一。

可提供的能力示例：

- `getCurrentExtDisplayId()`
- `isTntDisplay()`
- `getWindowPackageByPoint(...)`
- `getFocusedTaskInfoExtTnt()`

当前状态：

- 项目还没有直接去绑定或调用这条服务。
- 但它是后续如果要做“更官方的 TNT 状态判断 / PC 窗口状态判断”时最值得继续挖的接口。

### 5.2 `SmtPCUtils` / `SmtPCUtilsInner`

用途：

- 这是 Smartisan PC 模式 / TNT 规则中心，不只是一个普通工具类。

源码锚点：

- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtils.java`
- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsInner.java`
- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java`

当前已确认的重要规则：

- `TNT_ANYWHERE_DISPLAY_PKG = "com.smartisanos.tntanywhere"`
- `TNT_VIRTUAL_DISPLAY_NAME = "smt.tnt.virtual.display"`
- `TNT_VIRTUAL_DISPLAY_ID = 100000`
- `isInPCModeList(...)` 会把下列项视作 PC/TNT 相关显示身份：
  - `com.smartisanos.boston.phone`
  - `com.smartisanos.smartfolder.aoa`
  - `com.smartisanos.tntanywhere`
  - `smt.tnt.virtual.display`
  - 以及若干无线投屏 / 兼容路径
- `isValidExtDisplayType(type, pkgName)` 会按 display type + owner pkg 双重判断某块显示是否可参与 PC 模式逻辑
- `isDisplayForTntAnywhere(pkgName)` 明确把：
  - `com.smartisanos.smartfolder.aoa`
  - `com.smartisanos.tntanywhere`
  视作 TNT Anywhere 相关身份
- `isPcMode()` 最终依赖的是内部 `sDisplayIdInPcMode`

当前状态：

- 项目并没有直接调用这些 framework 方法。
- 但整个 TNT 激活方案的成功，实质上就是在“尽量让我们创建出来的显示结果符合这些规则”。

### 5.3 `TntManagerService`

用途：

- Smartisan TNT 真正的系统级模式控制器。

源码锚点：

- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java`

已确认的重要行为：

- `enterPCModeLocked(displayId)` 才是真正的 TNT 进入流程
- 进入后会写入：
  - `global_pc_mode_settings = 1`
  - `tnt_translate_mode = 0`
  - `tnt_foo_display_state = 0`
- 退出时会把 `global_pc_mode_settings` 改回 `0`
- `scheduleDisplayAdded(...)` 会直接忽略 `< 100000` 的 displayId
- 只有 `100000+` 的 TNT 显示器才会进入正式 TNT 添加 / 退出处理流程

当前状态：

- 项目当前没有直接控制这个系统服务。
- 当前方案是通过构造符合条件的基础显示，让系统自己最终走到它的 TNT 处理分支。

### 5.4 `TntDisplayManagerServiceImpl`

用途：

- 管理 TNT 专用虚拟显示的系统级实现。

源码锚点：

- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java`

已确认的重要行为：

- 内部维护 `mNextTntVirtualDisplayId = 100000`
- 会创建一个名字固定为 `smt.tnt.virtual.display` 的虚拟显示
- 再把它分配进 `100000+` 的 TNT 显示 ID 空间

当前状态：

- 项目当前无法直接替代这段系统逻辑。
- 但对调试来说，看到 `100000+` 显示器，基本就意味着已经摸到真正 TNT 逻辑了。

### 5.5 `global_pc_mode_settings`

用途：

- TNT / PC mode 的全局状态输出之一。

源码锚点：

- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java`

已确认结论：

- 它更像“进入 TNT 后由系统写出的状态结果”
- 不是一个单独改值就能稳定触发 TNT 的根开关

当前状态：

- 项目当前不依赖它作为主触发手段。
- 仅适合作为调试参考状态。

### 5.6 `tnt_display_connected`

用途：

- 与官方 TNT 硬件接入状态更相关的全局标记。

源码锚点：

- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java`

已确认结论：

- TNT 相关 USB 硬件连上时写入 `1`
- 断开时写回 `0`
- 更像是官方硬件态 / 线缆态标志，而不是通用软件开关

当前状态：

- 项目不应把它当成 TNT 触发主条件。

## 6. 当前项目中最有价值的调试信号

如果后面还要继续排查兼容性，以下信号最有用：

### 6.1 显示维度

- display `id`
- display `name`
- display `type`
- display `ownerPackageName`
- display `layerStack`
- 是否出现 `100000+` TNT display

当前主要来源：

- `DisplayManager`
- `IDisplayManager.getDisplayInfo()`
- `dumpsys display`
- 项目内 `TntDebugVirtualDisplayHelper` / `UserService` 日志

### 6.2 TNT / PC mode 维度

- `global_pc_mode_settings`
- `tnt_display_connected`
- `dumpsys activity processes` 中的 `pc:true`
- 进程是否只是 `fg-service`

说明：

- 这些信息非常适合判断“系统到底有没有真的把我们视作 TNT / PC 进程”

### 6.3 性能维度

- `Smartisan boost: active / idle / unsupported`
- 后台或息屏前后的 `source fps / output fps`
- `toph` 中编码线程、采集线程和 native 主线程占用

说明：

- 这一组信号对判断“是网络问题、编码问题，还是系统调度问题”很关键。

## 7. 后续建议

如果后面还要继续做 Smartisan 兼容与增强，建议优先沿这三条线继续：

1. 继续围绕 `smt_pcm / ISmtPCManager / SmtPCUtils*` 深挖
   - 这条线最接近 Smartisan 官方 TNT/PC 模式语义

2. 把“显示身份”和“进程身份”分开处理
   - `com.smartisanos.tntanywhere` 解决的是显示 / 包名识别问题
   - `pc:true`、cpuset、sched group 才更接近进程调度和后台性能问题

3. 保留现有经验检测，同时逐步替换为更稳定的系统信号
   - 例如当前“选最大 displayId”的策略能工作，但它仍是经验规则
   - 未来如果能直接接入 `ISmtPCManager` 或更明确的 display 分类接口，会更稳

## 8. 相关文件速查

项目侧：

- `app-mirror/src/main/java/com/connect_screen/mirror/SmartisanPerformanceHelper.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/UserService.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/SurfaceControl.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/shizuku/DisplayManager.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/job/ProjectViaMoonlight.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDisplaySelector.java`
- `app-mirror/src/main/java/com/connect_screen/mirror/job/TntDebugVirtualDisplayHelper.java`

系统源码侧：

- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtils.java`
- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsInner.java`
- `smartisanos-src/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java`
- `smartisanos-src/frameworkjar/sources/android/app/ActivityManagerSmtEx.java`
- `smartisanos-src/frameworkjar/sources/android/pc/ISmtPCManager.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java`
- `smartisanos-src/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java`

补充调查记录：

- `TNT_ACTIVATION_INVESTIGATION.md`
