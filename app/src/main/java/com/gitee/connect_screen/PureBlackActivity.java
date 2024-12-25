package com.gitee.connect_screen;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

import android.content.Intent;
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

    // 添加坐标调整方法
    private static float[] adjustTouchCoordinates(float x, float y,
            int targetRotation,
            int targetWidth, int targetHeight,
            int sourceWidth, int sourceHeight) {
        // 打印原始坐标
        State.log(String.format("原始坐标: x=%.2f, y=%.2f", x, y));
        
        // 计算缩放比例
        float scaleX = (float) targetWidth / sourceWidth;
        float scaleY = (float) targetHeight / sourceHeight;

        // 应用缩放
        x *= scaleX;
        y *= scaleY;

        // 根据目标旋转角度调整坐标
        float[] result = new float[2];
        switch (targetRotation) {
            case Surface.ROTATION_0:
                result[0] = x;
                result[1] = y;
                break;
            case Surface.ROTATION_90:
                result[0] = y;
                result[1] = targetWidth - x;
                break;
            case Surface.ROTATION_180:
                result[0] = targetWidth - x;
                result[1] = targetHeight - y;
                break;
            case Surface.ROTATION_270:
                result[0] = targetHeight - y;
                result[1] = x;
                break;
        }
        
        // 打印调整后的坐标和旋转角度
        State.log(String.format("旋转角度: %d", targetRotation));
        State.log(String.format("调整后坐标: x=%.2f, y=%.2f", result[0], result[1]));
        
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 强制横屏
        setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);

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

        // 修改触摸监听器
        view.setOnTouchListener((v, event) -> {
            if (isExternalDevice(event)) {
                DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                Display targetDisplay = displayManager.getDisplay(State.lastSingleAppDisplay);
                if (targetDisplay == null)
                    return true;

                // 添加源显示器和目标显示器的旋转角度日志
                State.log(String.format("源显示器旋转角度: %d", getWindowManager().getDefaultDisplay().getRotation()));
                State.log(String.format("目标显示器旋转角度: %d", targetDisplay.getRotation()));

                // 添加源显示器和目标显示器尺寸的日志
                State.log(String.format("源显示器尺寸: %dx%d",
                    getWindow().getDecorView().getWidth(),
                    getWindow().getDecorView().getHeight()));
                State.log(String.format("目标显示器尺寸: %dx%d",
                    targetDisplay.getWidth(),
                    targetDisplay.getHeight()));

                // 获取原始坐标
                float x = event.getX();
                float y = event.getY();

                // 只需要目标显示器的旋转角度
                float[] adjustedCoords = adjustTouchCoordinates(x, y,
                        targetDisplay.getRotation(),
                        targetDisplay.getWidth(), targetDisplay.getHeight(),
                        getWindow().getDecorView().getWidth(),
                        getWindow().getDecorView().getHeight());

                // 设置调整后的坐标
                event.setLocation(adjustedCoords[0], adjustedCoords[1]);

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
        if (ShizukuUtils.hasPermission()) {
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

    // 添加新的辅助方法
    private boolean isExternalDevice(MotionEvent event) {
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