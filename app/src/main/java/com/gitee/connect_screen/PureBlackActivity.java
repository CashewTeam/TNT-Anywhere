package com.gitee.connect_screen;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import dev.rikka.tools.refine.Refine;

public class PureBlackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (grantWriteSecureSettings()) {
            
        }

        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // 设置全屏
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
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
        
        // 点击时退出
        view.setOnClickListener(v -> finish());
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
                } catch(Throwable e2) {
                    State.log("授予权限失败: " + e2.getMessage());
                }
            }
        }
        return false;
    }
} 