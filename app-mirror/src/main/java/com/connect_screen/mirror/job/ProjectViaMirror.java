package com.connect_screen.mirror.job;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.DisplayHidden;
import android.view.IWindowManager;

import com.connect_screen.mirror.MirrorActivity;
import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.MirrorSettingsActivity;
import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ServiceUtils;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import dev.rikka.tools.refine.Refine;

public class ProjectViaMirror implements Job {
    private static int TYPE_WIFI = 3;
    private Thread waitThread;
    private final Display mirrorDisplay;
    private boolean mediaProjectionRequested;

    public ProjectViaMirror(Display mirrorDisplay) {
        this.mirrorDisplay = mirrorDisplay;
    }

    @Override
    public void start() throws YieldException {
        if (waitThread != null) {
            waitThread.interrupt();
            waitThread = null;
        }
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE);
        boolean singleAppMode = Pref.getSingleAppMode();
        boolean useTouchscreen = preferences.getBoolean(Pref.KEY_USE_TOUCHSCREEN, true);
        if (singleAppMode && (!ShizukuUtils.hasPermission() || !useTouchscreen)) {
            String selectedAppPackage = Pref.getSelectedAppPackage();
            ServiceUtils.launchPackage(context, selectedAppPackage, mirrorDisplay.getDisplayId());
            State.refreshMainActivity();
            CreateVirtualDisplay.powerOffScreen();
            int targetDisplayId = mirrorDisplay.getDisplayId();
            if (ShizukuUtils.hasPermission()) {
                int singleAppDpi = Pref.getSingleAppDpi();
                IWindowManager wm = ServiceUtils.getWindowManager();
                wm.setForcedDisplayDensityForUser(targetDisplayId, singleAppDpi, 0);
                InputRouting.moveImeToExternal(targetDisplayId);
            }
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {

                }

                @Override
                public void onDisplayRemoved(int i) {
                    if (i == targetDisplayId) {
                        CreateVirtualDisplay.powerOnScreen();
                        ExitAll.execute(null, false);
                    }
                }

                @Override
                public void onDisplayChanged(int i) {

                }
            }, null);
            return;
        }
        if (requestMediaProjectionPermission(State.currentActivity.get(), singleAppMode)) {
            // 检查是否允许在该显示器上启动Activity
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            // 启动 MirrorActivity
            Intent intent = new Intent(context, MirrorActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(mirrorDisplay.getDisplayId());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!activityManager.isActivityStartAllowedOnDisplay(context, mirrorDisplay.getDisplayId(), intent)) {
                    Log.d("ProjectViaMirror", "该显示器不允许启动Activity，displayId: " + mirrorDisplay.getDisplayId());
                    return;
                }
            }
            context.startActivity(intent, options.toBundle());
            State.mirrorDisplayId = mirrorDisplay.getDisplayId();
        }
    }

    private boolean requestMediaProjectionPermission(Context context, boolean singleAppMode) throws YieldException {
        DisplayHidden displayHidden = Refine.unsafeCast(mirrorDisplay);
        if (displayHidden.getType() == TYPE_WIFI) {
            if (ShizukuUtils.hasPermission() && singleAppMode) {
                return true;
            }   
            return false;
        }
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        mediaProjectionRequested = true;
        MirrorMainActivity mirrorMainActivity = State.currentActivity.get();
        if (mirrorMainActivity == null) {
            return false;
        }
        mirrorMainActivity.startMediaProjectionService();
        throw new YieldException("等待用户投屏授权");
    }

}
