package com.gitee.connect_screen;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;

import java.lang.ref.WeakReference;

public class ProjectionManager {
    private static ProjectionManager instance;
    private final Context context;
    private MediaProjectionManager projectionManager;
    private MediaProjectionService mediaProjectionService;
    private ServiceConnection serviceConnection;
    private boolean isServiceBound = false;
    private WeakReference<Activity> currentActivity;
    private ProjectionCallback projectionCallback;
    private int REQUEST_MEDIA_PROJECTION;

    // 回调接口
    public interface ProjectionCallback {
        void onProjectionReady();
        void onProjectionError(String error);
    }

    private ProjectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.projectionManager = (MediaProjectionManager)
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    public static synchronized ProjectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProjectionManager(context);
        }
        return instance;
    }

    public void startProjection(Activity activity, ProjectionCallback callback) {
        this.currentActivity = new WeakReference<>(activity);
        this.projectionCallback = callback;
        
        // 先绑定服务
        bindMediaProjectionService();
    }

    private void bindMediaProjectionService() {
        if (isServiceBound) {
            requestProjectionPermission();
            return;
        }

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mediaProjectionService = ((MediaProjectionService.LocalBinder) service).getService();
                isServiceBound = true;
                requestProjectionPermission();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isServiceBound = false;
                mediaProjectionService = null;
                if (projectionCallback != null) {
                    projectionCallback.onProjectionError("服务连接断开");
                }
            }
        };

        Intent intent = new Intent(context, MediaProjectionService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void requestProjectionPermission() {
        Activity activity = currentActivity.get();
        if (activity == null) {
            if (projectionCallback != null) {
                projectionCallback.onProjectionError("Activity已销毁");
            }
            return;
        }

        Intent intent = projectionManager.createScreenCaptureIntent();
        activity.startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                setupProjection(resultCode, data);
            } else {
                if (projectionCallback != null) {
                    projectionCallback.onProjectionError("用户拒绝录屏权限");
                }
            }
        }
    }

    private void setupProjection(int resultCode, Intent data) {
        if (!isServiceBound || mediaProjectionService == null) {
            if (projectionCallback != null) {
                projectionCallback.onProjectionError("服务未连接");
            }
            return;
        }

        // 在这里处理投屏业务逻辑
        // ...

        if (projectionCallback != null) {
            projectionCallback.onProjectionReady();
        }
    }

    public void release() {
        if (isServiceBound && context != null && serviceConnection != null) {
            context.unbindService(serviceConnection);
            isServiceBound = false;
        }
        currentActivity = null;
        projectionCallback = null;
    }
} 