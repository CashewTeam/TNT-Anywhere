package com.gitee.connect_screen;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Surface;

public class MediaProjectionService extends Service {

    private final IBinder binder = new LocalBinder();
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "MediaProjectionServiceChannel";

    public class LocalBinder extends Binder {
        MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        State.log("MediaProjectionService onCreate");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        State.log("MediaProjectionService onStartCommand");
        Intent mediaProjectionData = intent.getParcelableExtra("mediaProjectionData");
        if (mediaProjectionData == null) {
            State.log("mediaProjectionData 为空");
        } else {
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            State.mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK, mediaProjectionData);
            State.mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    State.log("MediaProjection 停止");
                    State.mediaProjection = null;
                }
            }, null);
            State.resumeJob();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        State.log("MediaProjectionService onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Projection Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification() {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Projection Service")
                .setContentText("Running in the background")
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setPriority(Notification.PRIORITY_DEFAULT);
        return builder.build();
    }
} 