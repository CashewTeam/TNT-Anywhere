package com.connect_screen.mirror.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import com.connect_screen.mirror.BuildConfig;
import com.connect_screen.mirror.PureBlackActivity;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public class ExitAll {
    public static void execute(Context context, boolean restart) {
        boolean wasSunshineStarted = SunshineServer.exitServer();
        CreateVirtualDisplay.restoreAspectRatio();
        CreateVirtualDisplay.powerOnScreen();
        if (State.mediaProjectionInUse != null) {
            State.mediaProjectionInUse.stop();
            State.mediaProjectionInUse = null;
        }
        State.setMediaProjection(null);
        // 重启应用
        if (restart) {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            // Required for API 34 and later
            // Ref: https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
            mainIntent.setPackage(context.getPackageName());
            context.startActivity(mainIntent);
        }

        if (State.mirrorVirtualDisplay != null) {
            State.mirrorVirtualDisplay.release();
        }

        State.displaylinkState.destroy();

        if (!wasSunshineStarted && !ShizukuUtils.hasPermission() && TouchpadAccessibilityService.getInstance() != null) {
            if (State.currentActivity.get() != null) {
                State.currentActivity.get().finish();
            }
            return;
        }

        if (context != null) {
            context.stopService(new Intent(context, SunshineService.class));
        }
        // 退出应用进程
        System.exit(0);
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch(Throwable e) {
            // ignore
        }
    }
}
