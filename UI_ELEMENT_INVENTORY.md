# 当前 UI 元素清单

> 说明：这份文档按“页面 / 子页面 / 弹窗 / 可复用条目”整理，优先覆盖当前布局 XML 和字符串资源里的静态文案，方便后续做 UI 重构映射。
> 动态运行态文案、日志区内容、以及代码里临时拼接的提示语未全部展开。

## 1. 主页 / 主控制页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 主页 | `app-mirror/src/main/res/layout/activity_main.xml` | `versionTitle` / TextView | `TNT Anywhere` | 顶部标题 | 版本页签感较强 |
| 主页 | `activity_main.xml` | `settingsBtn` / Button | `设置` | 进入设置页 |  |
| 主页 | `activity_main.xml` | `screenOffBtn` / Button | `熄屏` | 控制系统级熄屏 | 运行态可能显示/置灰 |
| 主页 | `activity_main.xml` | `touchScreenBtn` / Button | `触摸屏` | 进入触摸屏模式 |  |
| 主页 | `activity_main.xml` | `exitBtn` / Button | `退出` | 结束当前服务 / 退出 |  |
| 主页 | `activity_main.xml` | `tntModeCheckbox` / Switch | `串流 TNT 屏幕` | 切换 TNT 串流模式开关 | 核心入口 |
| 主页 | `activity_main.xml` | `tntDesktopBtn` / Button | `开启 TNT` | 启动 TNT 主链路 | 目前主页主按钮 |
| 主页 | `activity_main.xml` | `runtimeControlsHint` / TextView | `已启动服务后，可继续使用熄屏和触摸控制。` | 提示运行中控制能力 | 文案会随状态变化 |
| 主页 | `activity_main.xml` | `mirrorStatus` / TextView | `串流未启动` | 当前串流状态提示 | 动态状态文本 |
| 主页 | `activity_main.xml` | `streamingDebugPanel` / TextView/容器 | `Encoder config` 等运行态调试信息 | 编码与链路状态面板 | 运行态内容动态刷新 |
| 主页 | `activity_main.xml` | `logRecyclerView` / RecyclerView | 日志列表 | 运行日志区域 | 主要供调试 |

## 2. 设置页 / 主设置页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 设置页 | `app-mirror/src/main/res/layout/activity_mirror_settings.xml` | `initializationGuideButton` / Button | `打开初始化配置向导` | 打开首次初始化向导 |  |
| 设置页 | `activity_mirror_settings.xml` | `shizukuStatus` / TextView | `Shizuku 状态：` | 显示 Shizuku 状态 | 后接动态状态 |
| 设置页 | `activity_mirror_settings.xml` | `shizukuPermissionBtn` / Button | `授权` | 请求 Shizuku 权限 |  |
| 设置页 | `activity_mirror_settings.xml` | `accessibilityStatus` / TextView | `无障碍状态：` | 显示无障碍服务状态 |  |
| 设置页 | `activity_mirror_settings.xml` | `overlayStatus` / TextView | `悬浮窗状态：` | 显示悬浮窗权限状态 |  |
| 设置页 | `activity_mirror_settings.xml` | `viewRecentHandshakeButton` / Button | `查看最近握手信息` | 查看最近一次 Moonlight 握手摘要 | 调试入口 |
| 设置页 | `activity_mirror_settings.xml` | `viewRecentControlInputButton` / Button | `查看最近控制输入信息` | 查看最近一次控制输入统计 | 调试入口 |
| 设置页 | `activity_mirror_settings.xml` | `editTntOverlaySettingsButton` / Button | `编辑 TNT Overlay 参数` | 编辑 TNT overlay 调试参数 | 备用调试入口 |
| 设置页 | `activity_mirror_settings.xml` | `autoRotateCheckbox` / Switch | `自动旋转` | 镜像模式自动旋转 |  |
| 设置页 | `activity_mirror_settings.xml` | `autoScaleCheckbox` / Switch | `自动缩放去黑边` | 镜像画面自动缩放 |  |
| 设置页 | `activity_mirror_settings.xml` | `autoScreenOffCheckbox` / Switch | `自动熄屏` | 连接时自动控制熄屏 |  |
| 设置页 | `activity_mirror_settings.xml` | `useBlackImageCheckbox` / Switch | `使用黑色图片模拟熄屏` | 用黑图替代真实熄屏 |  |
| 设置页 | `activity_mirror_settings.xml` | `disableUsbAudioCheckbox` / Switch | `禁用 USB 外接屏音频输出` | 关闭 USB 音频输出 |  |
| 设置页 | `activity_mirror_settings.xml` | `autoMatchAspectRatioCheckbox` / Switch | `自动调整主屏宽高比以适配扩展屏` | 兼容扩展屏比例 |  |
| 设置页 | `activity_mirror_settings.xml` | `showFloatingInMirrorModeCheckbox` / Switch | `镜像模式下也显示悬浮返回键` | 镜像模式显示浮动返回键 |  |
| 设置页 | `activity_mirror_settings.xml` | `preventAutoLockCheckbox` / Switch | `阻止自动锁屏` | 保持系统不自动锁屏 |  |
| 设置页 | `activity_mirror_settings.xml` | `disableRemoteSubmixCheckbox` / Switch | `Moonlight 无声音时尝试不使用 REMOTE_SUBMIX` | 音频路由兼容 |  |
| 设置页 | `activity_mirror_settings.xml` | `smartisanCpusetBoostCheckbox` / Switch | `Smartisan 后台串流性能模式` | SmartisanOS 后台性能提权 | 当前正式选项 |
| 设置页 | `activity_mirror_settings.xml` | `useAndroidCursorOverlayCheckbox` / Switch | `显示安卓端叠加光标（兼容模式）` | 显示叠加光标 | 兼容模式 |
| 设置页 | `activity_mirror_settings.xml` | `mapMouseToTouchCheckbox` / Switch | `将鼠标事件映射为触控（兼容模式）` | 鼠标输入映射触控 | 兼容模式 |
| 设置页 | `activity_mirror_settings.xml` | `disableAccessibilityCheckbox` / Switch | `自动隐藏悬浮返回键` | 触发条件下隐藏悬浮返回键 |  |
| 设置页 | `activity_mirror_settings.xml` | `adaptTntResolutionToClientCheckbox` / Switch | `适应客户端分辨率` | TNT 模式下按客户端分辨率启动 | 默认开启 |
| 设置页 | `activity_mirror_settings.xml` | `autoCloseTntOnClientDisconnectCheckbox` / Switch | `自动关闭 TNT` | 客户端断开时自动结束 TNT | 默认开启 |
| 设置页 | `activity_mirror_settings.xml` | `autoConnectClientCheckbox` / Switch | `自动连接 Moonlight 客户端` | 检测到连接条件后自动连线 |  |
| 设置页 | `activity_mirror_settings.xml` | `connectClientButton` / Button | `连接` | 主动发起连接 |  |
| 设置页 | `activity_mirror_settings.xml` | `clientConnectionContainer` / 容器 | `Moonlight` 连接区域 | 承载客户端连接控件 | 文案动态 |
| 设置页 | `activity_mirror_settings.xml` | `backupTntRootStatus` / TextView | 备用启动状态 | 显示旧 root 方案状态 | 备用方式 |
| 设置页 | `activity_mirror_settings.xml` | `backupTntDebugStatus` / TextView | 调试状态 | 显示旧调试链路状态 | 备用方式 |
| 设置页 | `activity_mirror_settings.xml` | `checkBackupTntRootButton` / Button | `检查 Root` | 检查备用 root 条件 | 备用方式 |
| 设置页 | `activity_mirror_settings.xml` | `backupTntOverlayButton` / Button | `启动旧 Overlay TNT` | 旧 overlay 启动入口 | 备用方式 |
| 设置页 | `activity_mirror_settings.xml` | `backupTntDebugSwitch` / Switch | `TNT 调试开关` | 旧调试开关 | 备用方式 |

## 3. 编码器设置区

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 编码设置 | `activity_mirror_settings.xml` | `encoderCodecSpinner` / Spinner | `H.264 / HEVC` | 编码器选择 | 动态列表 |
| 编码设置 | `activity_mirror_settings.xml` | `encoderBitrateModeSpinner` / Spinner | 码率模式 | 码率控制方式 | 动态列表 |
| 编码设置 | `activity_mirror_settings.xml` | `encoderBitratePercentEditText` / EditText | 码率百分比 | 目标码率比例 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderComplexityEditText` / EditText | 复杂度 | 编码复杂度 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderIFrameIntervalEditText` / EditText | I 帧间隔 | I 帧刷新间隔 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderMaxFpsEditText` / EditText | 最大帧率 | 编码上限帧率 |  |
| 编码设置 | `activity_mirror_settings.xml` | `streamFecPercentEditText` / EditText | FEC 百分比 | 前向纠错比例 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderLowLatencyCheckbox` / Switch | 低延迟 | 低延迟编码模式 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderDisableBFramesCheckbox` / Switch | 禁用 B 帧 | 降低延迟 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderRealtimePriorityCheckbox` / Switch | 实时优先级 | 提升编码线程优先级 |  |
| 编码设置 | `activity_mirror_settings.xml` | `encoderDynamicFrameRateCheckbox` / Switch | 动态帧率 | 画面变化时动态提帧 |  |
| 编码设置 | `activity_mirror_settings.xml` | `currentEncoderSettingsText` / TextView | 当前编码设置 | 当前参数摘要 | 动态文本 |
| 编码设置 | `activity_mirror_settings.xml` | `saveEncoderSettingsButton` / Button | 保存 | 保存编码设置 |  |
| 编码设置 | `activity_mirror_settings.xml` | `resetEncoderSettingsButton` / Button | 重置 | 恢复默认值 |  |

## 4. 屏幕设置 / 显示器管理页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 屏幕设置 | `app-mirror/src/main/res/layout/activity_screen_settings.xml` | `title` / TextView | `屏幕设置` | 页面标题 |  |
| 屏幕设置 | `activity_screen_settings.xml` | `warningText` / TextView | `实验性功能：仅建议调试外接屏时使用。不同 ROM 的显示接口兼容性不同，错误参数可能造成画面异常。内置屏幕设置按钮已禁用，只允许查看。` | 实验性说明 | 长提示 |
| 屏幕设置 | `activity_screen_settings.xml` | `requestShizukuButton` / Button | `Shizuku 授权` | 请求 Shizuku 权限 |  |
| 屏幕设置 | `activity_screen_settings.xml` | `openCastSettingsButton` / Button | `系统投屏` | 打开系统投屏设置 |  |
| 屏幕设置 | `activity_screen_settings.xml` | `refreshDisplaysButton` / Button | `刷新` | 刷新显示器列表 |  |
| 屏幕设置 | `activity_screen_settings.xml` | `displayListContainer` / 容器 | 显示器列表 | 承载显示器条目 | 动态内容 |

## 5. About / 说明页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 关于页 | `app-mirror/src/main/res/layout/activity_about.xml` | `header` / TextView | `TNT Anywhere 是运行在 Android 设备上的 Sunshine Host，可将手机镜像或 TNT 外接显示器画面串流到 Moonlight 客户端。` | 项目简介 | 主页核心介绍 |
| 关于页 | `activity_about.xml` | `versionText` / TextView | `版本：` | 版本号展示 | 动态拼接 |
| 关于页 | `activity_about.xml` | `qqLink` / TextView | `原项目 QQ 群：安卓屏连` | 外部链接 |  |
| 关于页 | `activity_about.xml` | `websiteLink` / TextView | `原项目用户手册：connect-screen.com` | 外部链接 |  |
| 关于页 | `activity_about.xml` | `xiaohongshuLink` / TextView | `原项目小红书：安卓屏连` | 外部链接 |  |
| 关于页 | `activity_about.xml` | `bilibiliLink` / TextView | `原项目 B 站：安卓屏连` | 外部链接 |  |
| 关于页 | `activity_about.xml` | `douyinLink` / TextView | `原项目抖音：安卓屏连` | 外部链接 |  |
| 关于页 | `activity_about.xml` | `youtubeLink` / TextView | `原项目 YouTube：安卓屏连` | 外部链接 |  |
| 关于页 | `activity_about.xml` | `aboutContent` / TextView | 说明内容 | 详细介绍 | 动态/长文案 |

## 6. 触摸与控制页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 触摸板页 | `app-mirror/src/main/res/layout/activity_touchpad.xml` | `切换模式` / Button | `切换模式` | 切换触摸板输入模式 |  |
| 触摸板页 | `activity_touchpad.xml` | `退出` / Button | `退出` | 返回 / 关闭页面 |  |
| 触摸板页 | `activity_touchpad.xml` | `floating_back_image` / ImageView | 返回按钮图标 | 悬浮返回键 | 样式化图标 |
| 触摸板页 | `activity_touchpad.xml` | `back_button` / Button | `返回按钮` | 返回键 | 引用字符串资源 |
| 触摸板页 | `activity_touchpad.xml` | `home_button` / Button | `主页按钮` | Home 键 | 引用字符串资源 |
| 触摸板页 | `activity_touchpad.xml` | `go_dark_button` / Button | `切换暗色模式` | 切换暗色模式 | 引用字符串资源 |
| 触摸屏页 | `app-mirror/src/main/res/layout/activity_touchscreen.xml` | `exitText` / TextView | `长按退出` | 触摸屏模式退出提示 |  |
| 触摸屏页 | `activity_touchscreen.xml` | `blackImageView` / ImageView | 黑屏覆盖层 | 用于模拟黑屏 | 无文案 |

## 7. 首页嵌入页 / Fragment

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 首页 Fragment | `app-mirror/src/main/res/layout/fragment_home.xml` | `shizukuStatusPrefix` / TextView | `Shizuku 权限状态: ` | 状态前缀 |  |
| 首页 Fragment | `fragment_home.xml` | `shizukuStatus` / TextView | 动态状态 | Shizuku 状态显示 | 动态文本 |
| 首页 Fragment | `fragment_home.xml` | `shizukuPermissionBtn` / Button | `授权` | 请求授权 | 默认隐藏 |
| 首页 Fragment | `fragment_home.xml` | `exitBtn` / Button | `退出` | 退出应用 |  |
| 首页 Fragment | `fragment_home.xml` | `displayDeviceBtn` / Button | `屏幕` | 进入屏幕页 |  |
| 首页 Fragment | `fragment_home.xml` | `displaylinkBtn` / Button | `Displaylink` | 打开 DisplayLink 页 |  |
| 首页 Fragment | `fragment_home.xml` | `simulateScreenOffBtn` / Button | `模拟熄屏` | 模拟系统熄屏 |  |
| 首页 Fragment | `fragment_home.xml` | `touchpadBtn` / Button | `触控板` | 打开触摸板页 |  |
| 首页 Fragment | `fragment_home.xml` | `inputDeviceBtn` / Button | `设置` | 打开设置页 |  |
| 首页 Fragment | `fragment_home.xml` | `shizukuBtn` / Button | `Shizuku` | 打开 Shizuku 相关页 |  |
| 首页 Fragment | `fragment_home.xml` | `aboutBtn` / Button | `关于` | 打开关于页 |  |

## 8. Launcher / 应用启动页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 启动页 | `app-mirror/src/main/res/layout/activity_launcher.xml` | `searchBox` / EditText | `搜索应用` | 搜索应用 |  |
| 启动页 | `activity_launcher.xml` | `切换模式` / Button | `切换模式` | 切换启动模式 |  |
| 启动页 | `activity_launcher.xml` | `退出` / Button | `退出` | 退出 |  |
| 启动页 | `activity_launcher.xml` | `触控板` / Button | `触控板` | 打开触控板 |  |
| 启动页 | `activity_launcher.xml` | `模拟熄屏` / Button | `模拟熄屏` | 模拟熄屏 |  |
| 启动页 | `activity_launcher.xml` | `appListRecyclerView` / RecyclerView | 应用列表 | 展示可投屏应用 | 动态内容 |

## 9. Shizuku 子页面

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| Shizuku 页面 | `app-mirror/src/main/res/layout/fragment_shizuku.xml` | `statusText` / TextView | `Shizuku 权限状态: ` | 状态提示 |  |
| Shizuku 页面 | `fragment_shizuku.xml` | `wiredActivationRadioButton` / RadioButton | `有线激活` | 有线激活方式 |  |
| Shizuku 页面 | `fragment_shizuku.xml` | `wirelessActivationRadioButton` / RadioButton | `无线激活` | 无线激活方式 |  |
| Shizuku 页面 | `fragment_shizuku.xml` | `installShizukuButton` / Button | `安装 shizuku` | 安装/引导 Shizuku |  |

## 10. 显示器列表 / 详情页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 显示器列表 | `app-mirror/src/main/res/layout/fragment_display_list.xml` | `tipText` / TextView | `请选择单应用投屏的显示器，注意手机内置屏幕列在这里只能查看参数，无法被单应用投屏` | 页面说明 | 长提示 |
| 显示器列表 | `fragment_display_list.xml` | `wirelessCastHintButton` / Button | `安卓系统设置里的无线投屏也是可以投单应用的` | 说明性按钮/提示 |  |
| 显示器详情 | `app-mirror/src/main/res/layout/fragment_display_detail.xml` | `detail_text` / TextView | 详情信息 | 显示器详细参数 | 动态文本 |
| 显示器详情 | `fragment_display_detail.xml` | `shizuku_status` / TextView | `Shizuku 状态` | Shizuku 状态 | 动态文本 |
| 显示器详情 | `fragment_display_detail.xml` | `resolution_text` + `modify_resolution_button` | `分辨率 / 修改` | 修改分辨率 |  |
| 显示器详情 | `fragment_display_detail.xml` | `dpi_text` + `modify_dpi_button` | `DPI / 修改` | 修改 DPI |  |
| 显示器详情 | `fragment_display_detail.xml` | `user_rotation_text` + `modify_rotation_button` | `旋转 / 修改` | 修改屏幕旋转 |  |
| 显示器详情 | `fragment_display_detail.xml` | `supported_modes_toggle` / Button | `支持的显示模式 ▼` | 展开支持模式 |  |
| 显示器详情 | `fragment_display_detail.xml` | `supported_modes_text` / TextView | 支持模式列表 | 展开后显示支持模式 | 默认隐藏 |
| 显示器详情 | `fragment_display_detail.xml` | `start_launcher_button` / Button | `投屏单个应用` | 单应用投屏 |  |
| 显示器详情 | `fragment_display_detail.xml` | `autoOpenLastAppCheckbox` / CheckBox | `自动打开上次应用` | 自动恢复上次应用 |  |
| 显示器详情 | `fragment_display_detail.xml` | `start_x11_button` / Button | `投屏 Termux X11` | 启动 Termux X11 投屏 |  |
| 显示器详情 | `fragment_display_detail.xml` | `touchpad_button` / Button | `触控板` | 打开触摸板 |  |
| 显示器详情 | `fragment_display_detail.xml` | `floating_button_toggle` / Button | `展示悬浮返回键` | 显示悬浮返回键 |  |
| 显示器详情 | `fragment_display_detail.xml` | `forceLandscapeCheckbox` / CheckBox | `附带强制横屏效果` | 强制横屏 |  |
| 显示器详情 | `fragment_display_detail.xml` | `goto_displaylink_button` / Button | `由 Displaylink 创建` | 由 DisplayLink 创建 |  |
| 显示器详情 | `fragment_display_detail.xml` | `imePolicyButton` / Button | 无文本 | 输入法策略设置 |  |
| 显示器详情 | `fragment_display_detail.xml` | `bridge_button` / Button | `桥接(让竖屏应用自动旋转，覆盖刘海造成的黑边)` | 桥接功能 | 多行文案 |

## 11. 输入设备详情页

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 输入设备详情 | `app-mirror/src/main/res/layout/fragment_input_device_detail.xml` | `tvDeviceName` / TextView | 设备名 | 显示输入设备名称 | 动态文本 |
| 输入设备详情 | `fragment_input_device_detail.xml` | `tvDeviceDetails` / TextView | 设备详情 | 显示输入设备详情 | 动态文本 |
| 输入设备详情 | `fragment_input_device_detail.xml` | `spinnerDisplays` / Spinner | 显示器列表 | 绑定目标显示器 | 动态列表 |
| 输入设备详情 | `fragment_input_device_detail.xml` | `bindButton` / Button | `绑定` | 绑定到显示器 |  |

## 12. DisplayLink 页面

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| DisplayLink 页 | `app-mirror/src/main/res/layout/fragment_displaylink.xml` | `detailTitle` / TextView | `DisplayLink设备详情` | 页面标题 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `detailContent` / TextView | 说明内容 | 页面说明 | 动态文本 |
| DisplayLink 页 | `fragment_displaylink.xml` | `resolutionPresetSpinner` / Spinner | `显示器尺寸` | 预设分辨率选择 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `displayWidthInput` / EditText | `宽度` | 宽度输入 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `monitorHeightInput` / EditText | `高度` | 高度输入 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `dpiInput` / EditText | `DPI (默认160)` | DPI 输入 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `frameRateInput` / EditText | `帧率 (默认60)` | 帧率输入 | 仅部分情况下显示 |
| DisplayLink 页 | `fragment_displaylink.xml` | `rotatesWithContentCheckbox` / CheckBox | `跟随内容旋转` | 跟随内容旋转 | 默认隐藏 |
| DisplayLink 页 | `fragment_displaylink.xml` | `skipMediaProjectionPermissionCheckbox` / CheckBox | `跳过询问媒体投影权限` | 跳过权限询问 | 默认隐藏 |
| DisplayLink 页 | `fragment_displaylink.xml` | `autoOpenLastAppCheckbox` / CheckBox | `自动打开上次应用` | 自动恢复上次应用 | 默认隐藏 |
| DisplayLink 页 | `fragment_displaylink.xml` | `mirrorViaDisplaylinkButton` / Button | `Displaylink 单应用投屏` | 单应用投屏 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `launch_app_button` / Button | `选择单个应用` | 选择目标应用 |  |
| DisplayLink 页 | `fragment_displaylink.xml` | `view_virtual_display_button` / Button | `查看虚拟显示器` | 查看虚拟显示器 |  |

## 13. 关键弹窗 / 对话框

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| TNT Overlay 配置 | `app-mirror/src/main/res/layout/dialog_tnt_overlay_settings.xml` | 说明文本 | `This controls the headless TNT overlay display resolution used by the TNT button on the home page.` | TNT overlay 分辨率说明 | 英文 |
| TNT Overlay 配置 | `dialog_tnt_overlay_settings.xml` | `tntOverlayPresetSpinner` | `Preset` | 预设分辨率 |  |
| TNT Overlay 配置 | `dialog_tnt_overlay_settings.xml` | `tntOverlayWidthEditText` | `Width` / `1920` | 宽度输入 |  |
| TNT Overlay 配置 | `dialog_tnt_overlay_settings.xml` | `tntOverlayHeightEditText` | `Height` / `1080` | 高度输入 |  |
| TNT Overlay 配置 | `dialog_tnt_overlay_settings.xml` | `tntOverlayDpiEditText` | `DPI` / `216 or 320` | DPI 输入 |  |
| 分辨率修改 | `app-mirror/src/main/res/layout/dialog_edit_resolution.xml` | 说明文本 | `部分手机修改后画面可能会拉伸...` | 修改分辨率警告 | 长提示 |
| 分辨率修改 | `dialog_edit_resolution.xml` | `width_input` | `宽度：` | 宽度输入 |  |
| 分辨率修改 | `dialog_edit_resolution.xml` | `height_input` | `高度：` | 高度输入 |  |
| DPI 修改 | `app-mirror/src/main/res/layout/dialog_edit_dpi.xml` | `dpi_input` | `输入DPI值` | DPI 输入 |  |
| 旋转修改 | `app-mirror/src/main/res/layout/dialog_edit_rotation.xml` | `rotation_spinner` | 旋转选项 | 旋转设置 | 下拉框 |
| 分辨率设置 | `app-mirror/src/main/res/layout/dialog_resolution_settings.xml` | 说明文本 | `这个分辨率只是控制 displaylink 芯片的扩展坞输出分辨率...` | 分辨率用途说明 | 长提示 |
| 分辨率设置 | `dialog_resolution_settings.xml` | `resolutionPresetSpinner` | `预设分辨率` | 预设分辨率 |  |
| 分辨率设置 | `dialog_resolution_settings.xml` | `widthEditText` | `宽度:` / `宽度` | 宽度输入 |  |
| 分辨率设置 | `dialog_resolution_settings.xml` | `heightEditText` | `高度:` / `高度` | 高度输入 |  |
| 分辨率设置 | `dialog_resolution_settings.xml` | `refreshRateEditText` | `刷新率:` / `Hz` | 刷新率输入 |  |
| 手动客户端输入 | `app-mirror/src/main/res/layout/dialog_manual_client_input.xml` | `ipEditText` | `自启版会显示自己的ip` | IP 输入 |  |
| 手动客户端输入 | `dialog_manual_client_input.xml` | `portEditText` | `例如: 42515` | 端口输入 |  |
| 桥接设置 | `app-mirror/src/main/res/layout/dialog_bridge.xml` | 说明文本 | `桥接功能会创建一个虚拟显示器来做为应用投屏的宿主...` | 桥接功能说明 | 长提示 |
| 桥接设置 | `dialog_bridge.xml` | `rotatesWithContentCheckbox` | `跟随内容旋转` | 跟随内容旋转 |  |
| 桥接设置 | `dialog_bridge.xml` | `skipMediaProjectionPermissionCheckbox` | `跳过询问媒体投影权限` | 跳过投影权限询问 |  |
| 桥接设置 | `dialog_bridge.xml` | `autoBridgeCheckbox` | `下次自动开始桥接` | 记住桥接状态 |  |

## 14. 可复用条目 / 列表项

| 页面 | 文件 | 元素 ID / 类型 | 当前文案 | 作用 / 说明 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 显示器条目 | `app-mirror/src/main/res/layout/item_display.xml` | `display_id` / TextView | 显示器 ID | 显示器条目主标题 | 动态文本 |
| 显示器条目 | `item_display.xml` | `display_name` / TextView | 显示器名 | 显示器名称 | 动态文本 |
| 显示器条目 | `item_display.xml` | `btn_view_detail` / Button | `查看` | 查看显示器详情 |  |
| 应用条目 | `app-mirror/src/main/res/layout/item_app.xml` | `btn_launch` / Button | `投屏` | 对应用发起投屏 |  |
| 应用条目 | `item_app.xml` | `btn_launch_to_default_display` / Button | `回手机` | 将应用拉回手机屏幕 |  |
| 应用条目 | `item_app.xml` | `app_icon` / ImageView | 应用图标 | 显示应用图标 | 无文案 |
| 应用条目 | `item_app.xml` | `text1` / TextView | 应用名 | 主标题 | 动态文本 |
| 应用条目 | `item_app.xml` | `text2` / TextView | 应用详情 | 副标题 | 动态文本 |
| 输入设备条目 | `app-mirror/src/main/res/layout/item_input_device.xml` | `tvDeviceName` / TextView | 设备名 | 输入设备名称 | 动态文本 |
| 输入设备条目 | `item_input_device.xml` | `btnView` / Button | `查看` | 查看设备详情 |  |

## 15. 备注

| 项目 | 内容 |
| --- | --- |
| 语言风格 | 当前 UI 同时存在中文、英文和中英混排文案，重构时建议统一分层处理 |
| 文案来源 | 本表以 XML 静态文案为主，部分动态状态文本需要结合 Java 逻辑继续补齐 |
| 优先级建议 | 主页、设置页、显示器详情页、DisplayLink 页是重构时最容易影响用户感知的核心页面 |
