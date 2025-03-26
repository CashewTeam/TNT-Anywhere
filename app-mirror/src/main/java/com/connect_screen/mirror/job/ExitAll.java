package com.connect_screen.mirror.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.connect_screen.mirror.BuildConfig;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;

public class ExitAll {
    public static void execute(Context context, boolean restart) {
        SunshineServer.exitServer();
        CreateVirtualDisplay.restoreAspectRatio();
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

        if (context != null) {
            context.stopService(new Intent(context, SunshineService.class));
            context.stopService(new Intent(context, TouchpadAccessibilityService.class));
        }

        // 退出应用进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
