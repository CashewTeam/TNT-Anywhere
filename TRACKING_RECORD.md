# TNT-Anywhere Android 8.1 适配 — 黑屏问题排查记录

## 环境
- 设备: 坚果 Pro2S (OE106), SmartisanOS, Android 8.1.0, API 27, SDM710
- 项目: TNT-Anywhere (基于 Moonlight 串流方案)
- ADB: adb connect 192.168.50.228:5555
- 权限: Shizuku user service (root), 有 root 权限

## 问题现象
Moonlight 能连接，码率和帧率显示正常变动（7000~11000kbps, 57~59fps），
但 Moonlight 端始终黑屏。连接后几秒到十几秒会自动断开。

## 已排查的尝试（全部失败）

### 1. SurfaceControl.setDisplayLayerStack()
- 方法: createExternalMirrorApi30() — 先 SurfaceControl.createDisplay()，再 setDisplaySurface()、setDisplayProjection()、setDisplayLayerStack(layerStack)
- 结果: 返回成功（result=0），但 SurfaceFlinger 不产帧，编码器拿到黑帧
- 原因: SmartisanOS 8.1 的 SurfaceFlinger 不支持对 VIRTUAL 类型显示的 layerStack 镜像

### 2. MediaProjection.createVirtualDisplay()
- 方法: 用 MediaProjection 创建捕获虚拟显示，传入编码器 Surface
- 结果: 成功创建虚拟显示，仅能捕获 display 0（手机屏幕），不能指定捕获其他显示
- 原因: API 27 的 MediaProjection 不支持指定 displayId

### 3. DisplayManager.createVirtualDisplay()（Camera 进程）
- 方法: 从 app 进程直接调用 DisplayManager.createVirtualDisplay()
- 结果: SecurityException: Requires CAPTURE_VIDEO_OUTPUT permission
- 原因: app 没有系统级权限，CAPTURE_VIDEO_OUTPUT 是 signature 级别

### 4. CreateVirtualDisplay via Shizuku（with app package）
- 方法: 通过 Shizuku IDisplayManager.createVirtualDisplay() 用 app 包名创建
- 结果: SecurityException: packageName must match the calling uid
- 原因: Shizuku 以 shell 身份运行（uid=2000），app 包名对应 uid=10245，UID 不匹配

### 5. CreateVirtualDisplay via Shizuku（with com.android.shell）
- 方法: 用 com.android.shell 包名创建，同时设置 persist.sys.virtual_display_pkg
- 结果: isValidExtDisplayType 检查需要 VIRTUAL_DISPLAY_PKG 匹配，com.android.shell 不匹配
- 原因: TntManagerService 识别虚拟显示需要 package 匹配 VIRTUAL_DISPLAY_PKG

### 6. SurfaceControl.screenshot() via startDisplayScreenshotMirror
- 方法: UserService 的后台线程轮询 SurfaceControl.screenshot()
- 结果: No supported SurfaceControl screenshot API — DisplayCaptureArgs$Builder 在 API 27 不存在
- 原因: 
  a) getDisplayToken() 返回的是物理显示 token，不是目标虚拟显示的 token
  b) 即使修复 token，SurfaceControl.screenshot() 对虚拟显示也不支持
  c) DisplayCaptureArgs$Builder 是 API 28+ 的类

## 已成功的部分

### 1. TNT 自动启动 ✅
- 设置 Settings.Secure.pc_mode_enable=1 → TntManagerService.handleDisplayAdded 检测到显示 → enterPCMode
- 虚拟触控板通知出现，com.smartisanos.desktop.Desktop 窗口出现在虚拟显示上

### 2. 虚拟显示识别 ✅
- 设置 persist.sys.virtual_display_pkg=com.smartisanos.tntanywhere
- isValidExtDisplayType(5, pkg) 通过 package 匹配检查
- TntDisplaySelector 识别 tntanywhere.base.display

### 3. Moonlight 连接和编码 ✅
- Moonlight 连接成功，编码器正常运行（60fps, High Profile H.264）
- 问题不是编码/网络，而是输入 Surface 的内容是黑的

## 官方无线 TNT 链路分析（关键线索）

### 显示器拓扑
```
Display 0 (type=BUILT_IN, id=0, layerStack=0) → 手机屏幕
Display 1 (type=WIFI, id=1, layerStack=1) → WiFi 显示（Z7000）
```

### WiFi 显示上的窗口
- com.smartisanos.desktop/Desktop (TNT 桌面)

### TntManagerService 流程
```
SmtPCManagerService.handleDisplayAdded(displayId=1)
  → TntManagerService.handleDisplayAdded(id=1, settings=1)
  → TntActivityManagerServiceImpl.enterPCMode(displayId=1)
  → 启动 Desktop/SystemUI/Launcher 到 display 1
```

### 视频编码链路
```
WiFi Display (display 1, layerStack=1)
  → WFDMM (WiFi Display Media Manager)
    → WFDMMSRCVCAP (采集帧, ~20 FPS)
    → WFDV4L2ENC (V4L2 硬件编码器, H.264)
    → 通过 Miracast 协议发送
```

### 关键发现
1. isValidExtDisplayType() 的守门逻辑：
   - type == 2 (HDMI) → true
   - type == 3 (WIFI) → true
   - type == 5 (VIRTUAL) → true 仅当 VIRTUAL_DISPLAY_PKG 匹配

2. 官方无线 TNT 用 WIFI 显示（type=3），系统原生支持，SurfaceFlinger 对其有完整的捕获和渲染支持

3. 我们创建的是 VIRTUAL 显示（type=5），虽然通过 package 匹配让 TntManagerService 识别了（进入 PC 模式），
   但 SurfaceFlinger 层面对其有特殊限制：
   - setDisplayLayerStack 不产帧
   - screenshot API 不支持
   - 标准 MediaProjection 无法捕获

## 方案5测试结果（已验证）

### 测试方法
1. 在 ProjectViaMoonlight.mirrorDisplay() 中，API<28 非默认显示时扫描 WiFi 显示（type=3）
2. 找到 WiFi 显示后用 createExternalMirror 镜像其 layerStack
3. 在 TntDebugVirtualDisplayHelper 中检测到 WiFi 显示时跳过创建虚拟显示
4. 不重置 pc_mode_enable（它是 TNT 桌面启动的必要条件）

### 测试结果
- WiFi 显示（Z7000, type=3）可以与我们的 app 共存 ✅
- 不创建虚拟显示 + 不碰 pc_mode_enable = WiFi 显示存活 ✅
- createExternalMirror 返回 0（成功）但 Moonlight 黑屏 ❌
- **setDisplayLayerStack 镜像对任何类型的源显示都不产帧** ❌

### 根本原因
createExternalMirrorApi30() 创建的是一个新的虚拟显示（type=5），然后用 setDisplayLayerStack 设置其 layerStack。
SurfaceFlinger 对**虚拟显示**的 setDisplayLayerStack 镜像不产帧——不管 layerStack 指向什么类型的源显示（type=3 WiFi 或 type=5 VIRTUAL）。
这是 API 27 SmartisanOS 的系统层面限制。

### 关键发现
1. **pc_mode_enable 绝对不能重置** — 它是 TntManagerService 进入 PC 模式、启动 TNT 桌面的必要条件
   - 重置为 0 → TntManagerService 退出 PC 模式 → TNT 桌面消失 → WiFi 显示上无内容
2. **pc_mode_enable=1 会干扰 WFD** — 当新显示出现时，TntManagerService 重新处理显示会杀掉 WFD 会话
   - 但如果我们的 app 不创建新显示，就不会触发这个问题
3. **WiFi 显示 (type=3) 与我们的 app 可以共存** — 前提是不创建虚拟显示
4. **setDisplayLayerStack 路径彻底走不通** — API 27 SmartisanOS 的 SurfaceFlinger 限制

### 当前代码状态（已安装在设备上）
1. ProjectViaMoonlight.java — findWifiDisplayId() 扫描 type==3 显示，找到就用 createExternalMirror 镜像
2. TntDebugVirtualDisplayHelper.java — findExistingWifiDisplayId() 检测到 WiFi 显示时跳过虚拟显示创建
3. StartSunshineService.java — 不做任何修改（不碰 pc_mode_enable）

## 下一步方向：Hook WFDMM

### 为什么选这个方向
- setDisplayLayerStack 路径已确认走不通（系统层面限制）
- SurfaceControl.screenshot() 之前尝试失败（API 27 不支持 DisplayCaptureArgs）
- MediaProjection 不支持指定 displayId（API 27 限制）
- **WFDMM 是 WiFi 显示的编码管线，它已经在捕获和编码 WiFi 显示的帧**
- 如果能 Hook WFDMM，就可以直接拿到编码前的帧数据

### WFDMM 架构（从 logcat 分析）
`
WiFi Display (type=3)
  → WFDMM (WiFi Display Media Manager, Qualcomm native 组件)
    → WFDMMSRCVCAP (帧采集, ~20 FPS)
    → WFDMMSRCVCAP → WFDMMSRCVENC (帧编码)
      → WFDV4L2ENC (V4L2 硬件编码器, H.264)
    → 通过 Miracast 协议发送到接收端
`

### Hook WFDMM 的可能路径
1. **Hook WFDMM 的帧采集接口** — 在 WFDMMSRCVCAP 采集帧时拦截，将帧数据同时发送到我们的编码器
2. **Hook WFDV4L2ENC** — 在 H.246 编码前拦截原始帧
3. **创建虚拟 WFDMM 会话** — 用 root 权限调用 WFDMM 的内部接口，创建一个额外的捕获会话
4. **LD_PRELOAD 注入** — 用 root 权限通过 LD_PRELOAD 注入 so 库，Hook WFDMM 的函数

### 需要逆向的组件
- /system/lib64/libwfdcommon.so (或类似名称)
- /system/lib64/libmm-wfdclient.so
- WFDMM 相关的 native 库
- 关键函数：帧采集、帧编码、会话管理

### 难度评估
- 高难度，需要逆向 Qualcomm 的 native 库
- 但这是目前唯一可行的方向
- 有 root 权限，可以做 LD_PRELOAD 注入和内存修改

## 文件修改历史

### 已提交的修改
1. TntDebugVirtualDisplayHelper.java — 自动设置 pc_mode_enable=1 和 persist.sys.virtual_display_pkg
2. TntDisplaySelector.java — API<28 时优先选择 tntanywhere.base.display
3. ProjectViaMoonlight.java — API<28 非默认显示走 startDisplayScreenshotMirror（当前方案）
4. UserService.java — 改用 SurfaceControl.getDisplayToken(displayId) 获取正确 display token
5. SurfaceControl.java — 添加 getDisplayToken(long) 方法

### 当前未解决的问题
- ProjectViaMoonlight.java 中的 SmartisanOS 块需要改为查找 WiFi 显示并镜像它
- 替换内容：去掉 startDisplayScreenshotMirror 调用，改为遍历 DisplayManager.getDisplays() 找 type==3 的显示
## 五个可能的尝试方向（来自分析讨论）

### 方向 1：直接改 isValidExtDisplayType() 的判断逻辑
- 用 root 权限 Hook 或修改 system_server 中的 SmtPCUtils.isValidExtDisplayType()
- 问题：framework 类运行在 system_server，直接修改困难

### 方向 2：修改 persist.sys.virtual_display_pkg 的比较逻辑
- 已经设置了 persist.sys.virtual_display_pkg，TntManagerService 已识别 VIRTUAL 显示
- 问题：问题不在识别层，在 SurfaceFlinger 层的帧捕获

### 方向 3：绕过 SurfaceFlinger，直接 Hook WFDMM
- WFDMM 是系统 native 组件（Qualcomm 的 WiFi Display Media Manager）
- 如果能找到它的接口，可以创建虚拟 WFDMM 会话捕获 VIRTUAL 显示
- 难度高，需要逆向 native 库

### 方向 4：用 root 修改 SurfaceFlinger 行为
- setDisplayLayerStack 对 VIRTUAL 显示不产帧，可能是 SurfaceFlinger 的限制
- 可用 LD_PRELOAD 注入 so 库修改 SurfaceFlinger 行为
- 或直接 patch SurfaceFlinger 二进制

### 方向 5（最实际）：利用现有的 WiFi 显示或创建虚拟 WiFi 显示
- 方案 5a：保持官方无线 TNT 连接，用 setDisplayLayerStack 镜像 WiFi 显示（当前尝试方向）
- 方案 5b：用 root 创建虚拟 Miracast 接收器，让系统创建一个 WIFI 显示
  - 需要 WiFi Direct P2P 组 + WFD 协议握手
  - 问题：比较重，需要完整 Miracast 协议栈
- 方案 5c：root 下直接调用 WifiDisplayAdapter 的隐藏 API 创建 WIFI 显示
  - 跳过 Miracast 握手，直接创建 type=3 的显示
  - 需要找到 WifiDisplayAdapter 的创建接口

## 2026-07-31 黑屏修复结论（已真机验证）

### 根因
- SmartisanOS 框架在开机时把 `persist.sys.virtual_display_pkg` 缓存进静态字段
  `android.app.SmtPCUtils.VIRTUAL_DISPLAY_PKG`，运行时改属性不生效。
- 之前设备开机缓存的是 `com.taowen.arctrl.debug`，因此 TNT 拒绝
  `com.smartisanos.tntanywhere` 创建的虚拟屏，`SmtPCManagerService.handleDisplayAdded`
  之后不会进入 PC 模式，编码器 Surface 始终没有镜像帧。

### 修复
- 重启设备，让开机缓存变为 `com.smartisanos.tntanywhere`。
- `TntDebugVirtualDisplayHelper` 反射读取框架缓存的 whitelist，与当前包名不一致时
  明确提示“需要重启手机后生效”，避免静默黑屏。
- Moonlight 视频源（`SunshineServer.onVideoInputSurface`）在 API<28 一步把 encoder
  Surface 建成 `tntanywhere.base.display`，不再先建 ImageReader 基座再重绑，避免
  `TntManagerService` 进入 PC 模式后被拆屏的竞态。
- native `sunshine.cpp` 在 API<28 先启动 MediaCodec，再把 input Surface 交给 Java，
  失败时通过 `notifyVideoSourceFailure` 结束出帧循环。

### 验证
- Moonlight 客户端实连成功，画面正常。
- logcat 持续输出 `Frame #`（约 60fps），`dumpsys display` 显示
  `tntanywhere.base.display`（1280x720，owner `com.smartisanos.tntanywhere`）处于活动状态。
