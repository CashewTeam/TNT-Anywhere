package com.gitee.connect_screen;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import android.provider.Settings;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import dev.rikka.tools.refine.Refine;

import java.util.HashSet;
import java.util.Set;

public class PureBlackActivity extends AppCompatActivity {
    // 添加 Set 来存储外部设备 ID
    private final Set<Integer> externalDeviceIds = new HashSet<>();
    private final boolean hasShizukuPermission = ShizukuUtils.hasPermission();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        if (grantWriteSecureSettings()) {
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    "com.gitee.connect_screen/.TouchpadAccessibilityService");
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            startService(serviceIntent);
        }

        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 设置全屏
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        // 支持刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }

        // 设置状态栏和导航栏透明
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // 设置纯黑背景
        View view = new View(this);
        view.setBackgroundColor(Color.BLACK);
        setContentView(view);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);

        // 修改触摸监听器
        view.setOnTouchListener((v, event) -> {
            if (isExternalDevice(event)) {
                Display targetDisplay = displayManager.getDisplay(State.lastSingleAppDisplay);
                if (targetDisplay == null)
                    return true;

                // 获取原始坐标
                float x = event.getX();
                float y = event.getY();
                State.log(String.format("原始坐标: (%.2f, %.2f)", x, y));

                // 计算缩放比例
                float targetWidth = targetDisplay.getWidth();
                float targetHeight = targetDisplay.getHeight();
                float sourceDisplayWidth = getWindow().getDecorView().getWidth();
                float sourceDisplayHeight = getWindow().getDecorView().getHeight();
                float relativeX = x / sourceDisplayWidth;
                float relativeY = y / sourceDisplayHeight;
                State.log(String.format("relative: (%.2f, %.2f)", relativeX, relativeY));

                // 应用缩放
                x = relativeX * targetWidth;
                y = relativeY * targetHeight;
                State.log(String.format("缩放后坐标: (%.2f, %.2f)", x, y));

                // 设置调整后的坐标
                event.setLocation(x, y);

                MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
                motionEventHidden.setDisplayId(State.lastSingleAppDisplay);
                ServiceUtils.getInputManager().injectInputEvent(event, 0);
                return true;
            }
            finish();
            return true;
        });
    }

    private boolean grantWriteSecureSettings() {
        if (hasShizukuPermission) {
            IPermissionManager permissionManager = ServiceUtils.getPermissionManager();
            UserHandle userHandle = Process.myUserHandle();
            UserHandleHidden userHandleHidden = Refine.unsafeCast(userHandle);
            String packageName = getPackageName();
            try {
                permissionManager.grantRuntimePermission(
                        packageName,
                        "android.permission.WRITE_SECURE_SETTINGS",
                        "0", userHandleHidden.getIdentifier());
                State.log("成功授予 WRITE_SECURE_SETTINGS 权限");
                return true;
            } catch (Throwable e) {
                try {
                    permissionManager.grantRuntimePermission(
                            packageName,
                            "android.permission.WRITE_SECURE_SETTINGS",
                            userHandleHidden.getIdentifier());
                    State.log("成功授予 WRITE_SECURE_SETTINGS 权限");
                    return true;
                } catch (Throwable e2) {
                    State.log("授予权限失败: " + e2.getMessage());
                }
            }
        }
        return false;
    }

    private boolean isExternalDevice(MotionEvent event) {
        if (!hasShizukuPermission) {
            return false;
        }
        int deviceId = event.getDeviceId();
        if (externalDeviceIds.contains(deviceId)) {
            return true;
        }
        InputDevice device = InputDevice.getDevice(deviceId);
        if (device != null) {
            if (device.isExternal()) {
                externalDeviceIds.add(deviceId);
                return true;
            }
        }
        return false;
    }
}