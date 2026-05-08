package com.connect_screen.mirror;

import static com.connect_screen.mirror.job.AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.connect_screen.mirror.job.FetchLogAndShare;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        
        TextView header = findViewById(R.id.header);
        header.setText("TNT Shaker 是运行在 Android 设备上的 Sunshine Host。它通过 Shizuku、VirtualDisplay / MediaCodec 和 Moonlight 使用的 NVIDIA GameStream 协议，将手机镜像画面或 TNT 外接显示器画面串流到 Moonlight 客户端，并把键鼠、触摸等输入回流到设备。");

        TextView aboutContent = findViewById(R.id.aboutContent);
        aboutContent.setText(
                "项目作用\n"
                        + "TNT Shaker 面向 SmartisanOS 8.5.3 / Android 11 这类设备，把手机变成可被 Moonlight 连接的串流主机。它支持本机镜像模式和 TNT 模式：前者串流手机主屏，后者串流外接显示器画面。\n\n"
                        + "架构概要\n"
                        + "1. UI 与任务层：负责设置、状态、日志和 ProjectViaMoonlight 等投屏任务调度。\n"
                        + "2. 系统权限层：通过 Shizuku / UserService 调用系统级显示、输入和电源控制能力。\n"
                        + "3. 采集编码层：参考 scrcpy 的 Android 11 采集思路，使用 VirtualDisplay、MediaProjection / SurfaceControl 和 MediaCodec 获取并编码画面。\n"
                        + "4. 串流协议层：native 侧实现 Sunshine 风格的 NVHTTP / RTSP / RTP 链路，与 Moonlight 客户端建立会话并发送音视频。\n"
                        + "5. 输入回流层：接收 Moonlight 的鼠标、键盘、触摸和滚轮事件，再映射回 Android 目标 display。\n\n"
                        + "开源与参考\n"
                        + "- 使用并改造 Sunshine 源码：https://github.com/LizardByte/Sunshine\n"
                        + "- 参考 scrcpy 的 Android 屏幕采集/编码思路：https://github.com/Genymobile/scrcpy\n"
                        + "- 使用 moonlight-common-c 相关协议实现。\n\n"
                        + "原项目来源\n"
                        + "TNT Shaker 继承自原项目“安卓屏连”的思路。原项目目标是让 Android 手机通过有线或无线方式连接屏幕、电脑和外接设备，补足部分厂商弱化掉的投屏、桌面模式和多显示能力。当前项目在此基础上聚焦 Moonlight / Sunshine Host 链路，面向 TNT 外接显示器与手机镜像串流。\n\n"
                        + "原项目链接\n"
                        + "- 用户手册：https://connect-screen.com/\n"
                        + "- 小红书：安卓屏连\n"
                        + "- B 站：安卓屏连\n"
                        + "- 抖音：安卓屏连\n"
                        + "- YouTube：https://www.youtube.com/@connect-screen\n\n"
                        + "DisplayLink 声明\n"
                        + "本应用保留了 DisplayLink® 相关兼容能力。DisplayLink® 是 Synaptics Incorporated 的注册商标，相关驱动程序的所有权利属于 Synaptics Incorporated。本应用与 Synaptics Incorporated 没有官方关联。");

        TextView xiaohongshuLink = findViewById(R.id.xiaohongshuLink);
        xiaohongshuLink.setOnClickListener(v -> openUrl("https://www.xiaohongshu.com/user/profile/602cc4c0000000000100be64"));

        TextView bilibiliLink = findViewById(R.id.bilibiliLink);
        bilibiliLink.setOnClickListener(v -> openUrl("https://space.bilibili.com/494726825"));

        TextView douyinLink = findViewById(R.id.douyinLink);
        douyinLink.setOnClickListener(v -> openUrl("https://www.douyin.com/user/MS4wLjABAAAAolJRQWuFI6KZwaBUvPfzDejygnorK2K-CY_6b1OuWQM"));

        TextView youtubeLink = findViewById(R.id.youtubeLink);
        youtubeLink.setOnClickListener(v -> openUrl("https://www.youtube.com/@connect-screen"));

        TextView qqLink = findViewById(R.id.qqLink);
        qqLink.setText("原项目 QQ 群：安卓屏连");
        qqLink.setOnClickListener(v -> joinQQGroup());

        TextView websiteLink = findViewById(R.id.websiteLink);
        websiteLink.setText("原项目用户手册：connect-screen.com");
        websiteLink.setOnClickListener(v -> openUrl("https://connect-screen.com"));

        TextView versionText = findViewById(R.id.versionText);
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            String androidVersion = android.os.Build.VERSION.RELEASE;
            versionText.setText("TNT Shaker " + versionName + " (Android " + androidVersion + ")");
        } catch (Exception e) {
            versionText.setText("版本：未知");
        }

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!ShizukuUtils.hasShizukuStarted()) {
                    State.log("shizuku not started");
                    return false;
                }
                if (!ShizukuUtils.hasPermission()) {
                    State.log("ask shizuku permission");
                    Toast.makeText(AboutActivity.this, "导出故障日志需要 shizuku 权限", Toast.LENGTH_SHORT).show();
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
                    return false;
                }
                State.startNewJob(new FetchLogAndShare(AboutActivity.this));
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        header.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    public void joinQQGroup() {
        String key = "ngIy53SQRlz6tAO0UEkmALBjvGKkDYrq";
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + key));
        try {
            startActivity(intent);
        } catch (Exception e) {
            openUrl("https://connect-screen.com");
        }
    }
} 
