# TNT 激活机制调查

## 目的

这份笔记用于记录当前围绕 `SmartisanOS TNT` 实际激活方式的逆向进展，重点回答以下问题：

- `app-mirror` 能否在没有实体 TNT 硬件的情况下，触发一个真正的 TNT 桌面会话？
- 仅写入 `global_pc_mode_settings` 是否足够？
- `100000+` 这一批显示器 ID 在整个流程里到底扮演什么角色？

以下结论基于：

- `smartisanos/smartisan-framework-tnt`
- `smartisanos/smartisan-services-tnt`

## 当前高层结论

现阶段证据强烈表明：

1. `global_pc_mode_settings` 主要是系统服务在进入 TNT 后写出的状态结果，并不是真正的入口触发器。
2. 真正的 TNT 会话是由系统服务统一编排的，核心是 `TntManagerService`，而不是普通 App 直接拉起。
3. `SmartisanOS` 会创建一个专用 TNT 虚拟显示器，名称为 `smt.tnt.virtual.display`，并把它重新映射到从 `100000` 开始的显示 ID 空间。
4. TNT 的进入逻辑响应的是这个 `100000+` 虚拟显示器的生命周期，而不是普通外接显示器本身。
5. 这个 TNT 虚拟显示器看起来是建立在一个更底层的“基础外部显示会话”之上的，系统必须先认可这个基础显示。
6. 因此，仅仅在应用侧创建一个虚拟显示器，或者只切换某个全局设置，基本不太可能完整复现官方 TNT 激活路径。

## 关键发现

### 1. `TntManagerService` 才是真正的 TNT 模式控制器

文件：

- [smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java)

关键点：

- `enterPCModeLocked(int displayId)` 才是真正的 PC 模式进入序列，内部会执行：
  - 电源 / observer 初始化
  - 唤醒
  - 服务绑定
  - `mTntService.enterPcMode(displayId)`
  - 然后写入 `global_pc_mode_settings = 1`
- `exitPCModeLocked(...)` 则执行反向流程，并写回 `global_pc_mode_settings = 0`

相关引用：

- [TntManagerService.java:1253](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1253)
- [TntManagerService.java:1268](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1268)
- [TntManagerService.java:1315](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1315)

解释：

- `global_pc_mode_settings` 看起来更像是 `TntManagerService` 写出的结果标志，而不是让 TNT 启动的根因。

### 2. 系统只把 `100000+` 显示器当成 TNT 进入候选

仍在：

- [TntManagerService.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java)

关键点：

- `scheduleDisplayAdded(...)` 会明确忽略小于 `100000` 的显示 ID
- `handleDisplayAdded(...)` 才是真正可能触发 TNT 进入的分支
- `scheduleDisplayRemoved(...)` 也会忽略非虚拟显示 ID

相关引用：

- [TntManagerService.java:1414](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1414)
- [TntManagerService.java:1416](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1416)
- [TntManagerService.java:1435](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1435)
- [TntManagerService.java:1466](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1466)

解释：

- 系统不会直接对一个底层、较小 ID 的外接显示器进入 TNT。
- 它真正等待的是 `100000+` 这一层 TNT 虚拟显示器。

### 3. `100000+` 是专门的 TNT 虚拟显示器空间

文件：

- [smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java)

关键点：

- `mNextTntVirtualDisplayId = 100000`
- 系统会创建一个名为 `smt.tnt.virtual.display` 的虚拟显示器
- `assignTntVirtualDisplayIdIfNeeded(...)` 会把这个显示器重新映射到 `100000+` 区间

相关引用：

- [TntDisplayManagerServiceImpl.java:47](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:47)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntDisplayManagerServiceImpl.java:220](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:220)
- [TntDisplayManagerServiceImpl.java:290](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:290)

解释：

- 调试时观察到的 `10000x` / `100000+` 显示 ID，本来就是系统预期行为，不是异常。

### 4. TNT 虚拟显示器是叠在基础显示之上的

仍在：

- [TntDisplayManagerServiceImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java)

关键点：

- `addVirtualDisplayLocked(...)` 会先用 `SmtPCUtilsInner.isValidExtDisplayType(...)` 校验一个基础显示
- 然后把它记录为 `mBaseDisplayId`
- 再创建 `smt.tnt.virtual.display`
- `showVirtualDisplayIfNeededLocked(...)` 决定当前应该返回默认显示、基础显示，还是 TNT 虚拟显示器

相关引用：

- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:208](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:208)
- [TntDisplayManagerServiceImpl.java:226](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:226)
- [TntDisplayManagerServiceImpl.java:265](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:265)

解释：

- 当前看到的架构更像这样：
  - 先存在一个有效的基础外接显示 / 会话
  - 然后系统在其之上创建 TNT 虚拟显示器
  - 再由 `TntManagerService` 基于这个 TNT 虚拟显示器进入 TNT

这和日志现象是一致的：

- 较小的显示 ID：基础显示 / 基础会话
- `100000+`：TNT 虚拟显示器

### 5. 真正的 TNT 进入还会同步更新 AMS / WMS 状态

文件：

- [smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java)

关键点：

- `enterPcMode(int displayId)` 会调用：
  - `SmtPCUtilsInner.setIsPcMode(displayId, true)`
  - `mTntWindowManager.enterPcMode(display, true)`

相关引用：

- [TntActivityTaskManagerServiceImpl.java:510](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:510)
- [TntActivityTaskManagerServiceImpl.java:519](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:519)

解释：

- 真正的 TNT 会话不只是“有一个显示器存在”这么简单。
- 它还要求系统级窗口 / Activity 策略一起切到该显示器对应的 PC 模式。

### 6. framework 层同样假设 TNT 已经是系统已建立状态

文件：

- [smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java)

关键点：

- framework 代码会观察 `global_pc_mode_settings`
- 对于非系统 App，`adjustChoosePresentationDisplay(...)` 会在 PC 模式尚未开启时，把 `displayId >= 100000` 的显示器过滤掉

相关引用：

- [TntMediaRouterImpl.java:26](E:/Sunshine-android-master/smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java:26)
- [TntMediaRouterImpl.java:54](E:/Sunshine-android-master/smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java:54)

解释：

- 就连 framework 侧的 Presentation 行为，也是假设 `100000+` TNT 显示器是特殊对象，且在 TNT 真正激活前不应暴露给普通 App 流程。

### 7. `tnt_display_connected` 看起来更偏向硬件态

回到：

- [TntManagerService.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java)

关键点：

- `tnt_display_connected = 1` 会在 TNT USB 相关硬件接入时写入
- 断开时会写回 `tnt_display_connected = 0`

相关引用：

- [TntManagerService.java:3196](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3196)
- [TntManagerService.java:3358](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3358)

解释：

- 这个设置项更像是官方 TNT 硬件探测相关状态，而不是通用 TNT 激活开关。

### 8. 存在一条专门的 “Boston / TNT Anywhere” 特殊路径

相关引用：

- [TntDisplayManagerServiceImpl.java:478](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:478)
- [TntPowerManagerServiceImpl.java:59](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/power/TntPowerManagerServiceImpl.java:59)

关键点：

- 系统代码会检查 `SmtPCUtilsInner.isDisplayForTntAnywhere(ownerPackageName)`
- 也存在针对 `com.smartisanos.boston.phone` 的显式判断

解释：

- Smartisan 很可能为无线 TNT / Boston 硬件 / 专属 App 流程准备了一个带特权的内部显示 / 会话路径。
- 这条路径和普通第三方 App 创建显示器的方式是不同的。

## 这对 `app-mirror` 意味着什么

根据当前证据，`app-mirror` 很可能可以：

- 创建采集 / 串流 / 控制链路
- 创建或使用应用侧显示器
- 在某些显示器上触发类似 TNT 的 UI 渲染

但 `app-mirror` 不太可能完整复现官方 TNT 激活，除非它也能满足系统服务侧的预期条件，例如：

- 一个被系统认定为有效的基础外部显示分类
- 通过系统显示服务逻辑创建 TNT 虚拟显示器
- `TntManagerService` / AMS / WMS 的 PC 模式编排
- 可能还包括 Boston / TNT Anywhere 专属条件

## 来自 `framework.jar` 的新发现

我们现在已经定位到 framework 侧的真实实现：

- [SmtPCUtilsInner.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java)
- [SmtPCUtils.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java)
- [SmtPCUtilsSmtBase.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java)
- [SmtPCUtilsInnerBase.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java)

这确认了 `SmtPCUtilsInner` 不只是被服务层引用，它本身就是 Smartisan framework 里的规则中心。

### 9. `SmtPCUtilsInner` 是规则中心，不只是一个薄封装 helper

关键点：

- `SmtPCUtilsInner` 会保存 `sDisplayIdInPcMode`
- `setIsPcMode(displayId, true/false)` 只是在 framework 侧更新这份状态
- `SmtPCUtilsInnerBase` 里的 `isPcMode()` 本质上是检查 `sDisplayIdInPcMode` 是否是一个有效外接显示 ID

相关引用：

- [SmtPCUtilsInner.java:278](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:278)
- [SmtPCUtilsInnerBase.java:13](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java:13)
- [SmtPCUtilsInnerBase.java:18](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java:18)

解释：

- framework 侧“PC 模式是否工作中”是由一个基于 displayId 的状态模型驱动的。
- 当前处于 PC 模式的真实显示 ID，是系统核心状态之一。

### 10. `SmtPCUtils` framework 代码会绑定到 TNT 系统服务 `smt_pcm`

关键点：

- `SmtPCUtilsSmtBase.getSmtPCManager()` 会通过 `ServiceManager.getService("smt_pcm")` 获取服务
- 然后把 binder 包装成 `android.pc.ISmtPCManager`

相关引用：

- [SmtPCUtilsSmtBase.java:39](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:39)
- [SmtPCUtilsSmtBase.java:44](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:44)

解释：

- App / framework 侧的 `SmtPCUtils*` API，本质上是 `smt_pcm` 系统服务的客户端 facade。
- 现在 `SystemServer` 侧发现和 framework 侧发现已经可以完整对齐。

### 11. `ISmtPCManager` 是由 `TntManagerService` 实现的 binder 协议

关键点：

- `TntManagerService` 继承自 `ISmtPCManager.Stub`
- `ISmtPCManager` 内含的方法包括：
  - `getCurrentExtDisplayId()`
  - `isTntDisplay()`

相关引用：

- [TntManagerService.java:130](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:130)
- [ISmtPCManager.java:48](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/pc/ISmtPCManager.java:48)
- [ISmtPCManager.java:112](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/pc/ISmtPCManager.java:112)

解释：

- `SmtPCUtils -> ISmtPCManager -> TntManagerService` 现在已经是确认过的调用链。

### 12. framework 层的外接显示校验规则已经明确

关键点：

- `isValidExtDisplayType(int type, String pkgName)` 接受以下情况：
  - `type == 2`
  - `type == 3`
  - `type == 5` 仅当包名在 Smartisan PC 模式白名单内
  - `type == 4` 仅当开启 overlay-display 测试属性

相关引用：

- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)

解释：

- 这是理解“为什么有些显示器会被接受、有些会被忽略”的重大突破。
- 第三方创建的显示器，必须符合 Smartisan 认可的 type / package 模型，才能参与 TNT 逻辑。

### 13. Smartisan 的 PC 模式包名白名单是显式写死的

关键点：

- `isInPCModeList(...)` 包含：
  - `com.smartisanos.boston.phone`
  - `com.smartisanos.smartfolder.aoa`
  - `com.smartisanos.tntanywhere`
  - `smt.tnt.virtual.display`
  - `com.bytedance.wirelesscast`（默认虚拟显示包名属性）
  - `ScreenCastThread-display`

相关引用：

- [SmtPCUtils.java:144](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:144)
- [SmtPCUtils.java:252](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:252)

解释：

- Smartisan 明确识别一组带特权的显示 / 会话包名。
- 这进一步支持了一个判断：官方无线 TNT 和 Boston 流程是按包名打标签、再走特殊分支处理的。

### 14. TNT Anywhere 的识别是基于包名的

关键点：

- `isDisplayForTntAnywhere(pkgName)` 对以下包名返回 true：
  - `HANDSHAKER_DISPLAY_PKG`
  - `TNT_ANYWHERE_DISPLAY_PKG`

相关引用：

- [SmtPCUtilsInner.java:463](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:463)
- [SmtPCUtils.java:64](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:64)
- [SmtPCUtils.java:119](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:119)

解释：

- TNT Anywhere 不是仅凭“显示行为像不像”来推断的，它和已知包名身份直接绑定。
- 根据后续手动产品识别，`com.smartisanos.smartfolder.aoa` 现在可以确认是 Smartisan Handshaker 包（安卓 / PC / Mac 文件传输工具）。
- 因此 `HANDSHAKER_DISPLAY_PKG` 目前更应该被视作一种旧兼容 / 特殊白名单身份，而不是复现 TNT 无线桌面入口的首要目标。
- 后续继续调查 TNT 时，优先级更高的包名应该是 `com.smartisanos.tntanywhere`。

### 14A. `com.smartisanos.tntanywhere` 在 framework / services 里被引用，但它自己的 App 代码不在当前导出中

相关引用：

- [SmtPCUtils.java:119](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:119)
- [SmtPCUtils.java:253](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:253)
- [SmtPCUtilsInner.java:463](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:463)

关键点：

- 当前仓库里可以看到 framework / service 对 `com.smartisanos.tntanywhere` 的引用
- 但还没有找到这个包对应的反编译目录、manifest、activity、service，或显式组件启动路径
- 在当前导入源码里，也还没有发现针对 `com.smartisanos.tntanywhere` 的直接 `startActivity(...)`、`startService(...)`、`bindService(...)` 或 `sendBroadcast(...)`

解释：

- `com.smartisanos.tntanywhere` 很可能是一个独立的预装 / 系统包，只是它对应的 APK 或 jar 还没有被导出到当前工作区。
- 因此，现有系统侧代码可以告诉我们：当某个显示已经存在后，系统会如何识别这个包；
- 但还不能告诉我们：这个包本身是如何启动官方无线 TNT 流程的。

### 15. 外接显示器的发现顺序由 framework 定义

关键点：

- `findExtDisplayIfPossible(Context)` 会枚举全部显示器，并仅保留满足以下条件的对象：
  - `isValidExtDisplayId(displayId)` 为 true
  - `isValidExtDisplayType(type, ownerPackageName)` 为 true
- `type == 2` 的显示器会被插入结果列表头部

相关引用：

- [SmtPCUtilsInner.java:286](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:286)

解释：

- 这解释了为什么显示器顺序以及“优先选中哪个显示器”会受到 display type 的影响。

### 16. 官方 Smartisan 无线入口其实是一条专门的服务路径

相关引用：

- [WifiDisplaySettings.java:240](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:240)
- [WifiDisplaySettings.java:246](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:246)
- [AndroidManifest.xml:7](E:/Sunshine-android-master/lebo/app/src/main/AndroidManifest.xml:7)
- [AndroidManifest.xml:39](E:/Sunshine-android-master/lebo/app/src/main/AndroidManifest.xml:39)
- [WirelessCastService.java:290](E:/Sunshine-android-master/lebo/app/src/main/java/com/bytedance/wirelesscast/WirelessCastService.java:290)
- [WirelessCastService.java:362](E:/Sunshine-android-master/lebo/app/src/main/java/com/bytedance/wirelesscast/WirelessCastService.java:362)

关键点：

- Smartisan Settings 会直接绑定 `com.bytedance.wirelesscast/.WirelessCastService`
- 这个服务属于 `com.bytedance.wirelesscast` 包
- `WirelessCastService` 会注册一个 `DisplayManager.DisplayListener`
- 当有显示器被添加时，它只记录 `ownerPackageName == getPackageName()` 的显示器

解释：

- 官方无线投屏路径并不是一个通用的 `Settings -> MediaProjection` 流程。
- Smartisan 内置了一个专门的无线投屏服务包，而这个服务本身就把显示所有权身份看得很重。
- 这进一步强化了一个观点：显示 / 会话身份本身就是协议的一部分。

### 16A. Miracast / Wi-Fi Display 和 Wireless TNT 共享同一个 Settings 入口界面，但后端分支不同

相关引用：

- [WifiDisplaySettingsFragment.java:20](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettingsFragment.java:20)
- [WifiDisplaySettingsFragment.java:29](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettingsFragment.java:29)
- [WifiDisplaySettings.java:330](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:330)
- [DatabaseHelper.java:323](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/settingitemsprovider/DatabaseHelper.java:323)
- [DatabaseHelper.java:666](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/settingitemsprovider/DatabaseHelper.java:666)

关键点：

- Settings 暴露的是一个统一的 `WifiDisplaySettings` UI 组件
- 该组件在普通无线显示场景下会以 `entry_from_wifi=true` 打开
- 同一个组件在 `Wireless TNT` 场景下则以 `entry_from_wifi=false` 打开
- 当不是从 Wi-Fi 普通入口进入时，页面标题和开关标题会被改写成 `Wireless TNT`

解释：

- Smartisan 是刻意把 Miracast 和 Wireless TNT 合并到同一个前端发现 / 连接界面里的。
- 用户看到的入口是同一个，但设备选中后的后端分支，会根据目标设备类型和当前 TNT / 显示状态发生变化。

### 16B. Miracast 本身是一条物理 Wi-Fi Display 链：Wi-Fi P2P + RTSP + `RemoteDisplay.listen(...)`

相关引用：

- [DisplayManagerService.java:535](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:535)
- [WifiDisplayAdapter.java:246](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:246)
- [WifiDisplayAdapter.java:274](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:274)
- [WifiDisplayController.java:740](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:740)
- [WifiDisplayController.java:794](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:794)
- [WifiDisplayController.java:800](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:800)
- [WifiDisplayController.java:682](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:682)
- [WifiDisplayAdapter.java:159](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:159)
- [WifiDisplayAdapter.java:445](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:445)
- [WifiDisplayAdapter.java:573](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:573)
- [WifiDisplayAdapter.java:626](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:626)

关键点：

- `DisplayManagerService.connectWifiDisplay(address)` 会委托给 `WifiDisplayAdapter.requestConnectLocked(address)`
- `WifiDisplayAdapter` 内部持有 `WifiDisplayController`
- `WifiDisplayController` 负责 Wi-Fi P2P 建链，包括 WPS 配置和 group 建立
- P2P 建立后，再通过 `RemoteDisplay.listen(...)` 开始监听 Miracast RTSP 流
- 当 RTSP 会话建立后，会触发 `onDisplayConnected(surface, width, height, flags, session)`
- 然后 adapter 会用这个 `Surface` 创建 `WifiDisplayDevice`
- 这个显示设备会具备：
  - `uniqueId = "wifi:" + macAddress`
  - `type = 3`
  - `address = DisplayAddress.fromMacAddress(mac)`
  - 没有显式 `ownerPackageName`

解释：

- Miracast 路径本质上是一条物理外接显示链路，而不是应用拥有的虚拟显示链路。
- 这和运行时观察到的 `uniqueId` 以 `wifi:` 开头是吻合的。
- 这也解释了为什么物理 Miracast 显示器通常不带 TNT Anywhere 依赖的那种包名 owner 身份。

### 16C. TNT 只会在 Miracast 物理显示已经被系统接纳为 display device 之后再接入

相关引用：

- [DisplayManagerService.java:722](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:722)
- [DisplayManagerService.java:737](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:737)
- [DisplayManagerService.java:1282](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:1282)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntDisplayManagerServiceImpl.java:258](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:258)

关键点：

- 任何 display device 被添加后，`DisplayManagerService.handleDisplayDeviceAddedLocked(...)` 都会运行
- 如果当前开启 TNT / PC 支持，这个方法会把新设备继续转给 TNT 显示逻辑
- TNT 此后再决定：
  - 这个设备是不是一个有效基础外接显示
  - 是否需要创建 `smt.tnt.virtual.display`
- 后续在显示配置阶段，`showVirtualDisplayIfNeededLocked(...)` 可能会把可见内容路由到：
  - 默认显示器
  - 基础物理 Miracast 显示器
  - 或其上层 TNT 虚拟显示器

解释：

- TNT 并不是替代了 Miracast 传输层。
- 更准确地说，是 Miracast 先创建一个普通外接显示，然后 TNT 再择机在它上面叠出自己的桌面层。
- 这非常支持以下模型：
  - Miracast / Wi-Fi Display 负责传输和基础显示准入
  - TNT 负责后续的桌面虚拟化与内容路由

### 16D. Settings 会在策略层明确区分“普通 Miracast 无线连接”和 TNT 虚拟模式

相关引用：

- [SmtTntUtil.java:59](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:59)
- [SmtTntUtil.java:68](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:68)
- [SmtTntUtil.java:84](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:84)
- [WifiDisplaySettings.java:413](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:413)
- [WifiEnabler.java:209](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wifi/WifiEnabler.java:209)
- [WifiApEnablerEx.java:148](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wifi/WifiApEnablerEx.java:148)

关键点：

- `isMiracastWirelessConnect(...)` 会检查：
  - 当前外接显示 type `== 3`
  - 有匹配的 `MediaRouter` route name
  - route status code `== 6`
- `isSmtDisplayVirtualMode(...)` 则把 display type `3` 或 `5` 视作虚拟 / 无线 TNT 相关模式
- 多个 Settings 策略都会用这些 helper 去控制 Wi-Fi、热点，以及 TNT 关闭 / 重启提示

解释：

- Smartisan 的 Settings 层明确知道：
  - 纯 Miracast 接入的物理显示是一类状态
  - TNT 相关虚拟 / 桌面状态是另一类状态
- 所以即便前端 UI 共用一套界面，策略层依然会区别对待这两类状态。

### 16E. Miracast 基础显示之所以能被 TNT 接纳，主要靠 `displayId` 和 `type`，而不是 Miracast 会话元数据

相关引用：

- [SmtPCUtilsSmtBase.java:56](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:56)
- [SmtPCUtilsInner.java:286](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:286)
- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)
- [WifiDisplayAdapter.java:573](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:573)
- [WifiDisplayAdapter.java:626](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:626)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:287](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:287)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

关键点：

- framework 层 `isValidExtDisplayId(displayId)` 只要求：
  - `displayId != -1`
  - `displayId != 0`
- framework 层 `isValidExtDisplayType(type, ownerPackageName)` 接受：
  - `type == 2`
  - `type == 3`
  - `type == 5` 仅当 `ownerPackageName` 在 `PC_MODE_LIST` 中
  - `type == 4` 仅 overlay 测试模式
- 物理 Miracast `WifiDisplayDevice` 创建时具备：
  - `type = 3`
  - `uniqueId = "wifi:" + mac`
  - `address = DisplayAddress.fromMacAddress(mac)`
  - 没有显式 `ownerPackageName`
- 因此，一个普通 Miracast 显示器天然就满足 TNT 的基础显示类型门槛：
  - `isValidExtDisplayType(3, null) == true`
- `TntDisplayManagerServiceImpl.addVirtualDisplayLocked(...)` 接下来额外要求：
  - 设备名不能已经是 `smt.tnt.virtual.display`
  - 当前不能已经锁定了一个基础显示，除非存在 force-update
  - 必须能给这个设备找到对应的 logical display
- 目前没有发现任何 TNT 准入检查使用了：
  - Miracast `WifiDisplaySessionInfo`
  - `groupId`
  - `sessionId`
  - `custom_key_wireless_cast_name`
  - `wifi:` uniqueId 前缀本身
  - MAC 地址内容

解释：

- Miracast 显示器之所以能成为 TNT 基础显示，原因比预想中简单很多：
  - 它是一个非默认显示器
  - 并且它的 display type 是 `3`
- 在“这个显示器能不能成为 TNT 基础显示”这一层，Smartisan 看起来并不要求任何特殊的 Miracast 会话元数据。
- 更严格的条件发生在后面的真正 TNT 进入阶段：
  - boot / provision 状态必须就绪
  - 该显示必须已经被 ext-display manager 跟踪
  - 当前不能已有显示处于 PC 模式
  - 同时还必须满足 `pc_mode_enable == 1`，或者 TNT Anywhere 模式已激活
- 这意味着：普通 Miracast 和真正 TNT 之间的主要差距，不在 Miracast 传输握手本身，而在于后续系统模式切换条件。

### 16F. `isValidExtDisplayType(...)` 本质上就是在过滤具体的 Android 显示后端类型

相关引用：

- [Display.java:56](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/view/Display.java:56)
- [Display.java:550](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/view/Display.java:550)
- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)
- [LocalDisplayAdapter.java:458](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/LocalDisplayAdapter.java:458)
- [LocalDisplayAdapter.java:464](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/LocalDisplayAdapter.java:464)
- [WifiDisplayAdapter.java:626](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:626)
- [OverlayDisplayAdapter.java:299](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:299)
- [VirtualDisplayAdapter.java:375](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:375)

关键点：

- Android `Display` type 常量为：
  - `0 = UNKNOWN`
  - `1 = INTERNAL`
  - `2 = EXTERNAL`
  - `3 = WIFI`
  - `4 = OVERLAY`
  - `5 = VIRTUAL`
- `LocalDisplayAdapter` 会赋值：
  - 内建本地显示为 `type = 1`
  - 非内建本地物理显示为 `type = 2`
- `WifiDisplayAdapter.WifiDisplayDevice` 会赋值：
  - `type = 3`
- `OverlayDisplayAdapter.OverlayDisplayDevice` 会赋值：
  - `type = 4`
- `VirtualDisplayAdapter.VirtualDisplayDevice` 会赋值：
  - `type = 5`
- TNT framework 门槛 `isValidExtDisplayType(type, ownerPackageName)` 则把它们解释为：
  - `type == 2`：有线物理外接显示，始终接受
  - `type == 3`：Wi-Fi Display / Miracast 物理显示，始终接受
  - `type == 5`：App / 系统虚拟显示，仅当 `ownerPackageName` 在 Smartisan PC 模式白名单时才接受
  - `type == 4`：overlay 调试显示，仅当 `persist.easycast.show_overlay_display=true` 时接受
  - `type == 1` 或 `0`：不会被视为 TNT 外接显示候选

解释：

- 实际上，`isValidExtDisplayType(...)` 并不是一个模糊的策略钩子，它是在对真实显示后端家族做分类：
  - 手机 / 平板内建面板
  - 有线 HDMI / DP 类输出
  - Miracast / Wi-Fi Display 输出
  - 开发者 overlay 调试显示
  - 应用创建的虚拟显示
- 这也解释了为什么普通 Miracast 几乎不需要额外元数据就能工作：
  - 因为它天然落在带特权的物理外接桶 `type == 3`
- 同时也解释了为什么第三方虚拟显示更难用于 TNT：
  - 因为它落在 `type == 5`，而这一路是被包名身份严格限制的。

### 17. HPPlay / 乐播镜像后端确实会创建 `ScreenCastThread-display`

相关引用：

- [PermissionBridgeActivity.java:150](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/permission/PermissionBridgeActivity.java:150)
- [PermissionBridgeActivity.java:152](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/permission/PermissionBridgeActivity.java:152)
- [MirrorManagerImpl.java:132](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/MirrorManagerImpl.java:132)
- [MirrorManagerImpl.java:142](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/MirrorManagerImpl.java:142)
- [MirrorManagerImpl.java:161](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/MirrorManagerImpl.java:161)
- [ScreenCastService.java:416](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/ScreenCastService.java:416)
- [ScreenCastService.java:531](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/ScreenCastService.java:531)
- [ScreenCastService.java:538](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/ScreenCastService.java:538)
- [g.java:536](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/g.java:536)
- [i.java:97](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/hpplay/sdk/source/mirror/i.java:97)
- [i.java:779](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/hpplay/sdk/source/mirror/i.java:779)
- [SmtPCUtils.java:252](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:252)

关键点：

- hpplay 栈会先通过 `PermissionBridgeActivity` 申请 `MediaProjection`
- 随后启动并绑定 `ScreenCastService`
- `ScreenCastService` 会把权限 `Intent` 转成真实 `MediaProjection`
- 这个 `MediaProjection` 会被传入镜像工作线程 `g`
- 无论是独立无线投屏 App，还是 TNT Go App 中的 Lebo 路径，最终都会调用：
  - `MediaProjection.createVirtualDisplay("ScreenCastThread-display", ...)`
- Smartisan 的 PC 模式白名单明确包含 `ScreenCastThread-display`

解释：

- `ScreenCastThread-display` 不是一个偶然字符串，它是一个系统已知的官方后端身份。
- Smartisan 是有意识地识别这一类镜像后端的。
- 但大多数 framework / service 校验点，传入 `isValidExtDisplayType(...)` 的依然是 `ownerPackageName`，不是显示名。
- 因此显示名可以看作一个已知辅助身份，但在 framework 侧，包名所有权仍然像是更强的准入信号。

### 17A. 在官方无线虚拟显示流程中，`ownerPackageName` 来自调用者 App 包名

相关引用：

- [MediaProjection.java:72](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/media/projection/MediaProjection.java:72)
- [MediaProjection.java:73](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/media/projection/MediaProjection.java:73)
- [DisplayManagerGlobal.java:344](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/hardware/display/DisplayManagerGlobal.java:344)
- [DisplayManagerService.java:609](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:609)
- [DisplayManagerService.java:615](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:615)
- [VirtualDisplayAdapter.java:66](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:66)
- [VirtualDisplayAdapter.java:200](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:200)
- [VirtualDisplayAdapter.java:379](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:379)
- [WifiDisplayAdapter.java:613](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:613)

关键点：

- `MediaProjection.createVirtualDisplay(...)` 最终会调用 `DisplayManagerGlobal.createVirtualDisplay(context, ...)`
- `DisplayManagerGlobal` 会把 `context.getPackageName()` 传给 `IDisplayManager.createVirtualDisplay(...)`
- `DisplayManagerService.createVirtualDisplayInternal(...)` 再把这个包名继续转发给 `VirtualDisplayAdapter.createVirtualDisplayLocked(...)`
- `VirtualDisplayAdapter.VirtualDisplayDevice` 会把这个字符串保存为 `mOwnerPackageName`
- `VirtualDisplayAdapter.getDisplayDeviceInfoLocked()` 会写入：
  - `mInfo.ownerPackageName = mOwnerPackageName`
- 因此在官方 `lebo/hpplay` 无线虚拟显示路径里，预期 owner package 是 `com.bytedance.wirelesscast`
- 相比之下，物理 `WifiDisplayAdapter` 这条显示信息路径则不会填充 `ownerPackageName`

解释：

- 在 Smartisan 官方无线虚拟显示流程里，`ownerPackageName` 不是 TNT 后续逻辑临时伪造出来的。
- 它是从真正调用 `createVirtualDisplay(...)` 的 App / Service 上下文继承下来的。
- 这意味着：运行时看到 `ownerPackageName = com.bytedance.wirelesscast`、`com.smartisanos.smartfolder.aoa` 或 `com.smartisanos.tntanywhere`，都非常有助于判断到底是哪条高层入口路径创建了这个显示器。
- 同时它也把两类显示器清晰地区分开：
  - 物理 Miracast / WifiDisplay 显示器，此时 `ownerPackageName` 通常不是主身份信号
  - App 创建的虚拟显示器，此时 `ownerPackageName` 是 framework 明确可见的一等身份信号
- 结合当前产品知识，`com.smartisanos.smartfolder.aoa` 应更谨慎地理解为 Handshaker 相关兼容身份，而 `com.smartisanos.tntanywhere` 依旧是更像 TNT 无线桌面专属路径的包名。

### 17B. 即使 `pc_mode_enable` 没有置位，`tntanywhere` 也会在系统服务层改变 TNT 行为

相关引用：

- [DisplayManagerService.java:739](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:739)
- [DisplayManagerService.java:804](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:804)
- [TntDisplayManagerServiceImpl.java:258](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:258)
- [TntDisplayManagerServiceImpl.java:278](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:278)
- [TntDisplayManagerServiceImpl.java:478](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:478)
- [TntManagerService.java:1436](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1436)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)
- [TntManagerService.java:3922](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3922)
- [TntManagerService.java:3958](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3958)
- [TntManagerService.java:4063](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:4063)
- [TntManagerService.java:4069](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:4069)

关键点：

- `DisplayManagerService` 会在某个带 `ownerPackageName` 的显示器被添加 / 移除时通知 TNT 显示逻辑
- `TntDisplayManagerServiceImpl.checkDisplayForTntAnywhere(...)` 会在包名被识别为 TNT Anywhere 时切换 `mInTntAnywhereMode`
- 在镜像路由逻辑里，`showVirtualDisplayIfNeededLocked(...)` 会在基础显示 owner 为 `tntanywhere` 时，返回 TNT 虚拟显示器而不是默认显示器
- 在 `TntManagerService.handleDisplayAdded(...)` 中，只要满足以下其一就允许进入 TNT：
  - `mDisplayMode == 1`
  - 或 `mTntDisplayMS.getInTntAnywhereMode()` 为 true
- `handleDisplayModeChanged(0)` 在 TNT Anywhere 模式激活时，也会避免走通常的路径重置逻辑
- keep-alive 相关逻辑同样会在若干决策里，把 TNT Anywhere 当成“等价于已进入 PC 模式”

解释：

- `com.smartisanos.tntanywhere` 不是一个被动存在的白名单字符串而已。
- 一旦出现一个由该包拥有的显示器，系统服务会把它视作一种带特权的 TNT 无线桌面身份。
- 这是当前最强的一组证据，表明如果要复现官方 TNT 无线入口，很可能需要满足以下之一：
  - 真正拿到 `com.smartisanos.tntanywhere` 这个包
  - 或足够精准地模拟这个包使用的包名 / 显示身份及其创建时序

### 17C. `TntExtendDisplayManager` 主要跟踪的是 TNT 虚拟显示器，而不是底层物理基础显示器

相关引用：

- [RootWindowContainer.java:2418](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/wm/RootWindowContainer.java:2418)
- [TntManagerService.java:1414](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1414)
- [TntManagerService.java:1416](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1416)
- [TntManagerService.java:1424](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1424)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)
- [TntExtendDisplayManager.java:84](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntExtendDisplayManager.java:84)
- [TntExtendDisplayManager.java:242](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntExtendDisplayManager.java:242)
- [TntExtendDisplayManager.java:262](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntExtendDisplayManager.java:262)

关键点：

- `RootWindowContainer.onDisplayAdded()` 会把每一个非默认显示器都转发到 `scheduleDisplayAdded(displayId, ..., newDisplay=true)`
- 但 `TntManagerService.scheduleDisplayAdded(...)` 会立刻拒绝 `displayId < 100000`：
  - `"don't process non virtual displayId"`
- 当新显示器 `>= 100000` 时，`scheduleDisplayAdded(...)` 才会：
  - 把它加入 `mTntExtDisplayManager`
  - 然后选出 `getPreferredDisplayIdIfPossible()`
  - 再 post 后续真正的 `handleDisplayAdded(...)`
- `TntExtendDisplayManager.rebuildDisplayList(...)` 在重建时也只会重新加入 `id >= 100000` 的显示器
- `getPreferredDisplayIdIfPossible()` 本质上只是返回列表中的第一项
- `handleDisplayAdded(...)` 也只有在 `mTntExtDisplayManager.get(displayId) != null` 时才可能进入 TNT

解释：

- 尽管名字叫 `TntExtendDisplayManager`，它并不是所有物理外接显示器的权威注册表。
- 它的行为更像是：
  - TNT 层显示器的候选队列
  - 而在实践中，这基本就意味着被重新映射到 `100000+` 的 `smt.tnt.virtual.display`
- 较小 ID 的物理基础显示器仍然重要，但它主要存在于 `TntDisplayManagerServiceImpl` 的内部状态里，例如：
  - `mBaseDisplay`
  - `mBaseDevice`
  - `mBaseDisplayId`
- 这把 TNT 流程清晰地分成了两个阶段：
  1. 先接纳一个物理或带特权的虚拟显示，作为基础显示
  2. 再由 TNT 服务栈创建并跟踪自己的 `100000+` 虚拟显示器，作为真正的 PC 模式对象

### 17D. Overlay display 已通过实验确认可以作为无头 TNT 基础显示路径

相关引用：

- [SmtPCUtils.java:145](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:145)
- [SmtPCUtils.java:268](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:268)
- [OverlayDisplayAdapter.java:77](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:77)
- [OverlayDisplayAdapter.java:100](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:100)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

实验结果：

- 通过 ADB / root：
  - 开启 `persist.easycast.show_overlay_display=1`
  - 并借助 `Settings.Global["overlay_display_devices"]` 创建 Android overlay display
- 实机测试中，设备确实能够在无头场景下成功启动 TNT

解释：

- 这是一个非常关键的确认：Smartisan 的 TNT 栈并不从根本上依赖真实有线显示器或 Miracast 接收端。
- 更准确地说，只要系统服务看到一个满足以下条件的显示器就够了：
  - 它能通过 Smartisan 的外接显示策略校验
  - 它能在基础显示筛选阶段存活下来
  - 它允许 TNT 在其上继续创建自己的 `smt.tnt.virtual.display`
- 更重要的是，这条可工作路径使用的是 Android 平台自己官方提供的 overlay-display 调试机制。
- 因此它并不是一个狭义上的脆弱“伪造硬件”小技巧。
- 更合适的理解是：
  - 复用了 Android 官方调试显示后端
  - 同时打开了 Smartisan 内建的 overlay 接纳门
- 这让剩余的产品化问题被明显缩小：
  - 不再是“无头情况下 TNT 到底能不能跑”
  - 而是“`app-mirror` 如何自动且安全地走同一条系统认可路径”

### 18. 官方 Boston App 会主动写入 `pc_mode_enable`

相关引用：

- [Constants.java:16](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/utils/Constants.java:16)
- [Constants.java:24](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/utils/Constants.java:24)
- [EasyCastSourceLebo.java:1018](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceLebo.java:1018)
- [EasyCastSourceByte.java:316](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceByte.java:316)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

关键点：

- EasyCast 常量定义了：
  - mirror mode = `0`
  - PC mode = `1`
- 两个官方 EasyCast source 实现都会写入 `Settings.Secure["pc_mode_enable"]`
- `TntManagerService.handleDisplayAdded(...)` 要求以下其一成立：
  - `pc_mode_enable == 1`
  - 或 TNT Anywhere 模式已激活

解释：

- 官方 App 并不是只靠创建显示器就完事。
- 它还会和系统协作，提前写入一个持久显示模式偏好，供后续 TNT 服务判定使用。
- 这是官方流程和普通第三方投屏流程之间最具体的差异之一。

### 19. 官方 Boston 控制流会先区分有线和无线，再决定如何启动 TNT

相关引用：

- [ConnectionReceiver.java:220](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/receiver/ConnectionReceiver.java:220)
- [ConnectionReceiver.java:240](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/receiver/ConnectionReceiver.java:240)
- [ConnectionReceiver.java:668](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/receiver/ConnectionReceiver.java:668)
- [SmtTntUtil.java:21](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:21)

关键点：

- Boston App 暴露了带特权的广播动作，例如：
  - `com.smartisanos.boston.action.START_TNT`
  - `com.smartisanos.boston.action.REBOOT_TNT`
  - `com.smartisanos.boston.action.SHUTDOWN_TNT`
- 它的 receiver 会先判断当前模式是有线还是无线
- 有线路径走 `DpCtrlUtils.startupDp()/shutdownDp()`
- 无线路径走 `EasyCastSwitcher`
- Settings 侧的 `SmtTntUtil` 也会带上 `displayType = wired|wifi` 去发这些 action

解释：

- 官方 TNT 控制本质上是一个多分支状态机，而不是单一的“启动桌面”API。
- Smartisan 很早就在控制流里区分了有线 DP 类路径和无线 EasyCast 类路径。
- 因此如果要复现官方行为，关键不是简单“创建一个显示器”，而是匹配对正确的分支。

### 20. `global_pc_mode_settings` 在官方 App 中也是被观察的结果状态，而不是根触发器

相关引用：

- [ConnectionService.java:248](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/service/ConnectionService.java:248)
- [ConnectionService.java:255](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/service/ConnectionService.java:255)
- [TntManagerService.java:1268](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1268)

关键点：

- `ConnectionService` 会注册一个针对 `global_pc_mode_settings` 的 content observer
- 当它变化时，服务会刷新 `CastHalWrapper` 状态并记录模式变化日志
- 同一个 key 正是由 `TntManagerService` 在真正进入 / 退出 TNT 时写入的

解释：

- 即便在官方 App 里，`global_pc_mode_settings` 也更像是下游状态信号。
- 这和前面系统侧结论是一致的：单独写这个 key 并不是真正的 TNT 进入机制。

### 21. Smartisan 还会在显示准入之外，赋予额外的“按包名特权”

相关引用：

- [TntPowerManagerServiceImpl.java:52](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/power/TntPowerManagerServiceImpl.java:52)
- [TntMediaProjectionManagerServiceImpl.java:19](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/media/projection/TntMediaProjectionManagerServiceImpl.java:19)
- [TntDisplayManagerServiceImpl.java:478](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:478)
- [SmtPCUtilsInner.java:463](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:463)

关键点：

- power manager 侧会把 `ownerPackageName == com.smartisanos.boston.phone` 标成特殊 Boston 显示
- media-projection manager 在 PC 模式下会对 `isInPCModeList(...)` 里的包放宽普通投屏限制
- display manager 会基于特殊包名来跟踪 TNT Anywhere 模式：
  - `com.smartisanos.smartfolder.aoa`
  - `com.smartisanos.tntanywhere`

解释：

- Smartisan 对官方 TNT 相关身份的特殊处理，分布在多个子系统里，不是只在一个地方写了一次白名单。
- 这也意味着：第三方包即便复现了某一个表层行为，也未必能完整伪装成官方路径。

## 完整调用链

当前已经确认的 TNT 激活调用链如下：

1. `SystemServer` 通过 `TntFeatureFactoryImpl` 创建 TNT manager service
2. `SystemServer` 把该服务注册为 binder service `smt_pcm`
3. framework 侧 `SmtPCUtils*` API 通过 `ISmtPCManager` 连接到 `smt_pcm`
4. `TntManagerService` 就是 `ISmtPCManager` 背后的真实 binder 实现
5. `DisplayManagerService.handleDisplayDeviceAddedLocked()` 接收到一个新的 display device
6. 如果 TNT 支持开启，`DisplayManagerService` 会把该设备继续转发给 `TntDisplayManagerServiceImpl`
7. `TntDisplayManagerServiceImpl.addVirtualDisplayLocked()` 检查新设备是否是有效 TNT 基础显示
8. 如果有效，服务记录这个基础显示，并创建 `smt.tnt.virtual.display`
9. `DisplayManagerService.addLogicalDisplayLocked()` 把这个 TNT 虚拟显示器重新映射到 `100000+`
10. `RootWindowContainer.onDisplayAdded()` 看到新的 logical display，并调用 `mService.mTNT.scheduleDisplayAdded(displayId, ...)`
11. `TntManagerService.scheduleDisplayAdded()` 只会真正处理 `100000+` 这一层 TNT 虚拟显示 ID
12. `TntManagerService.LocalHandler` 处理消息 `2`，并执行 `handleDisplayAdded(displayId)`
13. `handleDisplayAdded(...)` 检查 boot / provision / display-mode / TNT-Anywhere 状态，并决定是否允许进入 TNT
14. 如果条件通过，会触发 `TntActivityTaskManagerServiceImpl.enterPcMode(displayId)`
15. `TntActivityTaskManagerServiceImpl` 会调用：
    - `SmtPCUtilsInner.setIsPcMode(displayId, true)`
    - `mTntWindowManager.enterPcMode(display, true)`
16. `TntManagerService.enterPCModeLocked(displayId)` 完成真正的系统模式切换
17. 直到这之后，系统才会写入 `global_pc_mode_settings = 1`

关键引用：

- [SystemServer.java:1667](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/SystemServer.java:1667)
- [SystemServer.java:1671](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/SystemServer.java:1671)
- [SmtPCUtilsSmtBase.java:39](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:39)
- [TntManagerService.java:130](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:130)
- [DisplayManagerService.java:722](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:722)
- [DisplayManagerService.java:737](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:737)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [DisplayManagerService.java:852](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:852)
- [RootWindowContainer.java:2418](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/wm/RootWindowContainer.java:2418)
- [TntManagerService.java:1414](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1414)
- [TntManagerService.java:1435](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1435)
- [TntActivityTaskManagerServiceImpl.java:510](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:510)
- [TntManagerService.java:1253](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1253)

## 进入 TNT 所需条件

根据目前已经确认的代码，一次真正的 TNT 会话需要满足以下几类条件：

### A. 首先必须有一个显示器被识别为有效 TNT 基础显示

系统必须看到一个显示器，其 type / package 满足 Smartisan 的校验规则：

- `type == 2`
- `type == 3`
- `type == 5` 且包名在 Smartisan PC 模式白名单中
- `type == 4` 仅 overlay-display 测试模式

相关引用：

- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)
- [SmtPCUtils.java:252](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:252)

### B. 该显示器必须能通过 TNT 基础显示筛选

`TntDisplayManagerServiceImpl.addVirtualDisplayLocked()` 会拒绝以下显示器：

- `isValidExtDisplayType(...)` 不通过
- 它本身已经是 TNT 虚拟显示器
- 它是错误模式下的 overlay-display 测试设备
- 当前已经锁定了一个基础显示，且没有等待中的强制刷新

相关引用：

- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)

### C. 系统还必须创建出 TNT 虚拟显示器

即使已经存在有效基础显示，TNT 也不会立刻进入，除非：

- `smt.tnt.virtual.display` 被创建出来
- 对应 logical display 被重新映射到 `100000+`

相关引用：

- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntDisplayManagerServiceImpl.java:290](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:290)

### D. display-added 事件必须真正到达 `TntManagerService`

这个 TNT 虚拟显示器必须：

- 被 materialize 成 logical display
- 被投递到 `RootWindowContainer.onDisplayAdded()`
- 再被转发进 `scheduleDisplayAdded()`

相关引用：

- [DisplayManagerService.java:851](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:851)
- [RootWindowContainer.java:2418](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/wm/RootWindowContainer.java:2418)

### E. `TntManagerService.handleDisplayAdded()` 的门槛条件必须全部通过

在真正进入前，TNT 还要求：

- boot completed 或 locked-boot completed
- 该显示器已经被 `TntExtendDisplayManager` 跟踪
- 当前没有其他显示器已经处于 PC 模式
- `pc_mode_enable == 1`，或 TNT Anywhere 模式已激活
- 设备已完成 provision
- 有效的 home / 必需服务可用
- 没有 “switch home” 对话框阻塞当前切换

相关引用：

- [TntManagerService.java:1435](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1435)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)
- [TntManagerService.java:1439](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1439)

### F. framework / system 状态随后还必须真正切到 PC 模式

一旦入口被允许，TNT 还需要完成：

- ATMS / WMS 的 PC 模式进入
- framework `sDisplayIdInPcMode` 更新
- `enterPCModeLocked()` 全部走完

相关引用：

- [TntActivityTaskManagerServiceImpl.java:510](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:510)
- [TntActivityTaskManagerServiceImpl.java:519](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:519)
- [SmtPCUtilsInner.java:278](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:278)
- [TntManagerService.java:1253](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1253)

### G. 对于普通非 TNT-Anywhere 路径，`pc_mode_enable` 还必须提前一致

官方 Boston / EasyCast 栈会显式写入：

- `Settings.Secure["pc_mode_enable"] = 1` 表示 PC mode
- `Settings.Secure["pc_mode_enable"] = 0` 表示 mirror mode

而 TNT 入口门槛检查的是：

- `mDisplayMode == 1`
- 或 `getInTntAnywhereMode() == true`

相关引用：

- [EasyCastSourceLebo.java:1018](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceLebo.java:1018)
- [EasyCastSourceByte.java:316](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceByte.java:316)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

解释：

- 如果不是走特殊的 TNT Anywhere 路径，那么即使有一个有效显示器存在也仍然不够，持久显示模式状态也必须先声明“当前是 PC mode”。

## 当前最终判断

当前最有支撑的结论是：

1. `global_pc_mode_settings` 不是一个充分触发条件。
2. 普通第三方显示器本身也不够。
3. 系统首先期待看到一个被 Smartisan 认可的有效基础显示。
4. 然后系统会自己创建 TNT 虚拟显示器，并且只对这一层的显示器作出 TNT 进入响应。
5. 官方 Smartisan 无线 / Boston / TNT Anywhere 路径之所以能工作，部分原因就在于它们的显示类型和包名身份天然符合这套模型。
6. 官方 App 栈还会写入 `pc_mode_enable`，区分有线 / 无线控制分支，并在电源 / media-projection / display 服务里享受额外的按包名特殊处理。

这意味着，对于 `app-mirror` 来说，核心挑战不只是“打开 TNT”，而是“让系统相信当前已经存在一个有效且具备 TNT 资格的显示 / 会话”，从而让原生 TNT 服务栈自行把后续激活链走完。
