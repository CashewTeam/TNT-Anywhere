# Overlay Display 调试指南

## 目的

这份文档记录如何在当前 SmartisanOS / TNT 研究环境中，手动开启 Android 的 `overlay display` 测试显示器，并让 Smartisan TNT 逻辑接受这类 `TYPE_OVERLAY(4)` 显示器作为候选外接屏进行调试。

这个方法主要用于：

- 验证 TNT 是否能接受 `TYPE_OVERLAY` 显示器
- 验证 `isValidExtDisplayType(...)` 对 overlay 测试分支是否生效
- 在没有真实外接屏时，构造一个便于观察的测试显示器

## 代码依据

### 1. Smartisan 是否接受 overlay 显示器

Smartisan 的 TNT 外接屏判断位于：

- [SmtPCUtils.java:145](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:145)
- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)

核心逻辑：

- `persist.easycast.show_overlay_display` 为 `true` 时
- `TYPE_OVERLAY(4)` 才会被 `isValidExtDisplayType(...)` 接受

### 2. Android overlay display 的创建入口

系统 DisplayManager 侧监听：

- [OverlayDisplayAdapter.java:77](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:77)
- [OverlayDisplayAdapter.java:100](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:100)

它读取的配置项是：

- `Settings.Global["overlay_display_devices"]`

### 3. 系统设置里的开发者选项本质上也在写这个值

- [DevelopmentSettings.java:1498](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/DevelopmentSettings.java:1498)
- [arrays.xml:1930](E:/Sunshine-android-master/smartisanos/settings/app/src/main/res/values/arrays.xml:1930)

## 前提条件

- 设备已获取 Root
- 可以执行 `su`
- 建议能使用 `adb shell`，也可以在本机终端执行

注意：

- 仅设置 `overlay_display_devices` 还不够
- 仅设置 `persist.easycast.show_overlay_display` 也不够
- 两步都要做

## 调试步骤

### 第一步：开启 Smartisan overlay 接受开关

执行：

```sh
su
setprop persist.easycast.show_overlay_display 1
```

如果系统对 `persist.*` 属性不持久，改用：

```sh
su
resetprop -n persist.easycast.show_overlay_display 1
```

说明：

- 这个属性会影响 `SmtPCUtils.SHOW_OVERLAY_DISPLAY`
- 该值在代码里是静态读取
- 最稳妥的做法是设置后重启设备

推荐执行：

```sh
su
reboot
```

## 第二步：创建 overlay display

重启后，执行例如：

### 创建 720p overlay 显示器

```sh
su
settings put global overlay_display_devices 1280x720/213
```

### 创建 1080p overlay 显示器

```sh
su
settings put global overlay_display_devices 1920x1080/320
```

### 创建 4K overlay 显示器

```sh
su
settings put global overlay_display_devices 3840x2160/320
```

### 创建双屏 overlay

```sh
su
settings put global overlay_display_devices 1280x720/213;1920x1080/320
```

注意：

- 某些 shell 里分号会被当成命令分隔符
- 如果在 `adb shell` 或终端里直接执行双屏值，建议加引号

例如：

```sh
su
settings put global overlay_display_devices "1280x720/213;1920x1080/320"
```

## 常用预设值

这些值来自系统设置资源：

- [arrays.xml:1930](E:/Sunshine-android-master/smartisanos/settings/app/src/main/res/values/arrays.xml:1930)

可直接使用的值：

- 空字符串：关闭 overlay
- `720x480/142`
- `720x480/142,secure`
- `1280x720/213`
- `1280x720/213,secure`
- `1920x1080/320`
- `1920x1080/320,secure`
- `3840x2160/320`
- `3840x2160/320,secure`
- `1920x1080/320|3840x2160/640`
- `1920x1080/320|3840x2160/640,secure`
- `1280x720/213;1920x1080/320`

补充说明：

- `,secure` 表示 secure overlay
- `;` 表示创建多个 overlay 显示器
- `|` 表示同一个 overlay 的多组 mode

## 如何验证是否生效

### 方法 1：查看系统设置值

```sh
settings get global overlay_display_devices
getprop persist.easycast.show_overlay_display
```

期望看到：

- `overlay_display_devices` 返回你写入的模式字符串
- `persist.easycast.show_overlay_display` 返回 `1` 或 `true`

### 方法 2：看系统是否创建了 overlay 显示器

可以抓 DisplayManager 相关日志，重点看：

- `OverlayDisplayAdapter`
- `DisplayManagerService`
- `TntDisplayMSImpl`
- `TntManagerService`

其中 `OverlayDisplayAdapter` 里会打印类似：

- `Showing overlay display device #...`

代码位置：

- [OverlayDisplayAdapter.java:176](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:176)

### 方法 3：从 TNT 角度验证

如果整个链路打通，理论上会看到：

1. 系统创建 `TYPE_OVERLAY(4)` 显示器
2. 因为 `persist.easycast.show_overlay_display=true`，它通过 `isValidExtDisplayType(...)`
3. `TntDisplayManagerServiceImpl.addVirtualDisplayLocked(...)` 尝试把它当作 base display
4. TNT 进一步尝试创建 `smt.tnt.virtual.display`

## 关闭 / 清理方法

### 关闭 overlay display

```sh
su
settings put global overlay_display_devices ""
```

### 关闭 Smartisan overlay 接受开关

```sh
su
setprop persist.easycast.show_overlay_display 0
```

如果使用的是 Magisk 属性：

```sh
su
resetprop -n persist.easycast.show_overlay_display 0
```

建议关闭后重启一次：

```sh
su
reboot
```

## 推荐调试顺序

建议按下面顺序做：

1. 设置 `persist.easycast.show_overlay_display=1`
2. 重启设备
3. 设置 `overlay_display_devices=1920x1080/320`
4. 观察系统是否出现新的 overlay 显示器
5. 观察 TNT 是否把它作为候选 base display
6. 再继续看是否创建出 `100000+` 的 `smt.tnt.virtual.display`

## 当前结论

基于目前代码分析，overlay 测试显示器只是一个“被 Smartisan 允许纳入外接屏判定”的特殊测试入口：

- 它能帮助验证 TNT 对 `TYPE_OVERLAY` 的兼容逻辑
- 但它不等同于官方的 Miracast / TNT Anywhere 无线链路
- 即使 overlay 被接受，也不代表一定能完整拉起官方 TNT 无线桌面流程

更准确地说，它是一个很有价值的调试抓手，但不是官方无线 TNT 的等价替代物。

## 实机验证更新

当前项目环境中，这套方法已经通过实机测试成功：

- 通过 ADB / Root 打开 `persist.easycast.show_overlay_display`
- 通过 `overlay_display_devices` 创建 overlay 显示器
- 成功无头启动 TNT

这说明该方案不是停留在代码推断层面，而是已经被实际验证为可行。

它的优势在于：

- 直接复用了 Android 官方的 overlay display 调试接口
- 不需要额外伪造物理外接显示器身份
- 不需要额外使用非官方显示链路去欺骗 TNT 服务

因此，在当前阶段，这已经是一个质量很高、很干净的 TNT 无头启动实验路径。
