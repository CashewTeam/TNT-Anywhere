# 安卓屏连

### 介绍

安卓屏连让安卓手机通过有线和无线的方式连接屏幕或者电脑，增强投屏时的细节体验。

* 一些手机把usb3硬件阉割成了usb2（比如红米），安卓屏连可以通过增购displaylink扩展坞弥补usb2无法满屏投屏的缺憾。
* 一些手机操作系统软件上阉割了安卓原生的桌面模式（比如小米14），安卓屏连可以通过adb权限把双屏异显的体验做好。
* 很多手机都有投屏到电脑的配套软件，但是很难投屏到竞争对手的设备上，也只能用手机长条形的宽高比镜像模式投屏。

安卓屏连把手机厂商阉割掉的接屏幕的功能想办法加回来。

### 自媒体账号

* 小红书：[安卓屏连](https://www.xiaohongshu.com/user/profile/602cc4c0000000000100be64)
* b站：[安卓屏连](https://space.bilibili.com/494726825)
* 抖音：[安卓屏连](https://www.douyin.com/user/MS4wLjABAAAAolJRQWuFI6KZwaBUvPfzDejygnorK2K-CY_6b1OuWQM)
* Youtube: [安卓屏连](https://www.youtube.com/@connect-screen)

### 安装方式

通过 QQ 加入群聊 577902537 获取 android apk 安装包

### TODO

* [ ] usb2 手机通过 displaylink 接显示器双屏镜像
  * [x] 基础的双屏镜像
  * [x] 横屏裁切到 16:9
  * [ ] 手机竖屏，显示器旋转成横屏
  * [ ] 检测到横屏左右黑边，自动裁切
  * [ ] 触摸屏通过无障碍api转发
* [ ] usb2 手机通过 displaylink 接显示器双屏异显
  * [ ] 悬浮到应用上，提供按钮推到显示器
  * [ ] 推过的应用保存到启动器列表
  * [ ] 竖屏应用旋转成横屏展示
  * [ ] 触摸屏通过adb转发
  * [ ] 手柄通过adb转发
  * [ ] 键盘通过adb转发
  * [ ] 鼠标通过adb转发
* [x] usb3 手机通过 typec 接显示器双屏镜像：镜像模式没有什么可以做的
* [ ] usb3 手机通过 typec 接显示器双屏异显
* [ ] usb3 手机通过 typec 接ar眼镜
* [ ] 手机通过 scrcpy 有线投屏到pc
* [ ] 手机通过 scrcpy 无线投屏到pc
* [ ] 手机通过 scrcpy 有线投屏到安卓平板
* [ ] 手机通过 scrcpy 无线投屏到安卓平板
* [ ] 手机通过 scrcpy 无线投屏到ipad
* [ ] 手机通过有线串流投屏到浏览器
* [ ] 手机通过无线串流投屏到浏览器

### 本应用不是 DisplayLink 官方应用

本应用使用了DisplayLink®的驱动程序(.so文件)用于支持DisplayLink®设备的连接功能。DisplayLink®是Synaptics Incorporated的注册商标。我们仅将其驱动程序用于实现与DisplayLink®设备的兼容性，未对驱动程序进行任何修改。

- DisplayLink®驱动程序的所有权利均属于Synaptics Incorporated
- 本应用仅将DisplayLink®驱动用于其预期用途，即支持DisplayLink®设备的连接
- 用户在使用DisplayLink®相关功能时应遵守Synaptics Incorporated的相关许可条款
- 本应用与Synaptics Incorporated没有任何官方关联，不代表或暗示与Synaptics Incorporated存在任何合作关系

如有任何与DisplayLink®相关的法律问题，请直接联系Synaptics Incorporated：www.synaptics.com