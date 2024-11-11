package com.gitee.connect_screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.view.Surface;

import java.util.HashMap;
import java.util.Map;

public class MediaProjectionService extends Service {
    private static final String CHANNEL_ID = "MediaProjection";
    private static final int NOTIFICATION_ID = 1;
    
    private MediaProjection mediaProjection;
    private final Map<String, VirtualDisplay> virtualDisplays = new HashMap<>();
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕投影")
                .setContentText("正在进行屏幕投影...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_NOT_STICKY;
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public VirtualDisplay createVirtualDisplay(String displayId, int width, int height, int dpi, Surface surface) {
        if (mediaProjection == null) {
            return null;
        }

        VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
            displayId,
            width, 
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            null
        );

        if (virtualDisplay != null) {
            virtualDisplays.put(displayId, virtualDisplay);
        }
        
        return virtualDisplay;
    }

    public void releaseVirtualDisplay(String displayId) {
        VirtualDisplay display = virtualDisplays.get(displayId);
        if (display != null) {
            display.release();
            virtualDisplays.remove(displayId);
        }
    }

    public void releaseAllVirtualDisplays() {
        for (VirtualDisplay display : virtualDisplays.values()) {
            display.release();
        }
        virtualDisplays.clear();
    }

    @Override
    public void onDestroy() {
        releaseAllVirtualDisplays();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "屏幕投影服务",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("用于显示屏幕投影状态的通知");
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
} 