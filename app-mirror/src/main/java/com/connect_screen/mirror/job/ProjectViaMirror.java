package com.connect_screen.mirror.job;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.view.Display;
import android.view.DisplayHidden;
import android.view.IWindowManager;

import com.connect_screen.mirror.MediaProjectionService;
import com.connect_screen.mirror.MirrorActivity;
import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.MirrorSettingsFragment;
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
        Context context = State.currentActivity.get();
        SharedPreferences preferences = context.getSharedPreferences(MirrorSettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        boolean singleAppMode = preferences.getBoolean(MirrorSettingsFragment.KEY_SINGLE_APP_MODE, false);
        if (singleAppMode) {
            int singleAppDpi = preferences.getInt(MirrorSettingsFragment.KEY_SINGLE_APP_DPI, 160);
            if (ShizukuUtils.hasPermission()) {
                IWindowManager wm = ServiceUtils.getWindowManager();
                wm.setForcedDisplayDensityForUser(mirrorDisplay.getDisplayId(), singleAppDpi, 0);
            }
            String selectedAppPackage = preferences.getString(MirrorSettingsFragment.KEY_SELECTED_APP_PACKAGE, "");
            ServiceUtils.launchPackage(context, selectedAppPackage, mirrorDisplay.getDisplayId());
            InputRouting.bindAllExternalInputToDisplay(mirrorDisplay.getDisplayId());
            return;
        }
        DisplayHidden displayHidden = Refine.unsafeCast(mirrorDisplay);
        if (displayHidden.getType() == TYPE_WIFI) {
            return;
        }
        if (requestMediaProjectionPermission(State.currentActivity.get())) {
            // 检查是否允许在该显示器上启动Activity
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            // 启动 MirrorActivity
            android.content.Intent intent = new android.content.Intent(context, MirrorActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setLaunchDisplayId(mirrorDisplay.getDisplayId());
            if (!activityManager.isActivityStartAllowedOnDisplay(context, mirrorDisplay.getDisplayId(), intent)) {
                State.log("该显示器不允许启动Activity，displayId: " + mirrorDisplay.getDisplayId());
                return;
            }
            context.startActivity(intent, options.toBundle());
            State.mirrorDisplayId = mirrorDisplay.getDisplayId();
        }
    }

    private boolean requestMediaProjectionPermission(Context context) throws YieldException {
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (MediaProjectionService.isStarting && MediaProjectionService.instance == null) {
            throw new YieldException("等待服务启动");
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        MediaProjectionService.isStarting = true;
        mediaProjectionRequested = true;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            State.currentActivity.get().startActivityForResult(captureIntent, MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            throw new YieldException("等待用户投屏授权");
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }

}
