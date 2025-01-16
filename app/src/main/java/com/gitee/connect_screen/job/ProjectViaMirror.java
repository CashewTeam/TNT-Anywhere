package com.gitee.connect_screen.job;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.view.Display;
import android.view.DisplayHidden;

import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.MirrorActivity;
import com.gitee.connect_screen.State;

import dev.rikka.tools.refine.Refine;

public class ProjectViaMirror implements Job {
    private static int TYPE_WIFI = 3;
    private final Display mirrorDisplay;
    private final VirtualDisplayArgs virtualDisplayArgs;
    private boolean mediaProjectionRequested;

    public ProjectViaMirror(Display mirrorDisplay, VirtualDisplayArgs virtualDisplayArgs) {
        this.mirrorDisplay = mirrorDisplay;
        this.virtualDisplayArgs = virtualDisplayArgs;
    }

    @Override
    public void start() throws YieldException {
        DisplayHidden displayHidden = Refine.unsafeCast(mirrorDisplay);
        if (displayHidden.getType() == TYPE_WIFI) {
            return;
        }
        if (requestMediaProjectionPermission(State.currentActivity.get())) {
            Context context = State.currentActivity.get();
            // 检查是否允许在该显示器上启动Activity
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            // 启动 MirrorActivity
            android.content.Intent intent = new android.content.Intent(context, MirrorActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("virtualDisplayArgs", virtualDisplayArgs);
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setLaunchDisplayId(mirrorDisplay.getDisplayId());
            if (!activityManager.isActivityStartAllowedOnDisplay(context, mirrorDisplay.getDisplayId(), intent)) {
                State.log("该显示器不允许启动Activity，displayId: " + mirrorDisplay.getDisplayId());
                return;
            }
            context.startActivity(intent, options.toBundle());
        }
        State.mirrorDisplayId = mirrorDisplay.getDisplayId();
    }

    private boolean requestMediaProjectionPermission(Context context) throws YieldException {
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.mediaProjection != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            if (!State.hasService) {
                throw new YieldException("等待服务启动");
            }
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        mediaProjectionRequested = true;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            State.currentActivity.get().startActivityForResult(captureIntent, MainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            throw new YieldException("等待用户投屏授权");
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }

}
