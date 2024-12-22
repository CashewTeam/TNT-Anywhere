package com.gitee.connect_screen.job;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjectionManager;

import com.gitee.connect_screen.BridgeActivity;
import com.gitee.connect_screen.BridgePref;
import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

public class ProjectViaBridge implements Job {
    private final static int REMOVE_CONTENT_MODE_DESTROY = 2;
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int bridgeDisplayId;
    private final VirtualDisplayArgs virtualDisplayArgs;
    private boolean mediaProjectionRequested;

    public ProjectViaBridge(int bridgeDisplayId, VirtualDisplayArgs virtualDisplayArgs) {
        this.bridgeDisplayId = bridgeDisplayId;
        this.virtualDisplayArgs = virtualDisplayArgs;
    }

    @Override
    public void start() throws YieldException {
        if (!ShizukuUtils.hasPermission()) {
            acquireShizuku.start();
            if (!acquireShizuku.acquired) {
                return;
            }
        }
        ServiceUtils.getWindowManager().setRemoveContentMode(bridgeDisplayId, REMOVE_CONTENT_MODE_DESTROY);
        if (requestMediaProjectionPermission(State.currentActivity.get())) {
            Context context = State.currentActivity.get();
            Intent intent = new Intent(context, BridgeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("virtualDisplayArgs", virtualDisplayArgs);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(bridgeDisplayId);
            context.startActivity(intent, options.toBundle());
        }
    }

    private boolean requestMediaProjectionPermission(Context context) throws YieldException {
        if (BridgeActivity.virtualDisplay != null) {
            return true;
        }
        if (BridgePref.skipMediaProjectionPermission) {
            // 无需 media projection 授权
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
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            State.currentActivity.get().startActivityForResult(captureIntent, MainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            throw new YieldException("等待用户投屏授权");
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }

}
