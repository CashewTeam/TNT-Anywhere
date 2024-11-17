package com.gitee.connect_screen;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MediaProjectionService", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && State.getUsbState(device.getDeviceName()) != null) {
                    State.log("USB 设备已断开: " + device.getDeviceName());
                    State.removeUsbState(device.getDeviceName());
                    State.resumeJob();
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        State.hasService = true;
        State.log("MediaProjectionService onCreate");
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // 注册 USB 设备断开广播接收器
        IntentFilter detachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachedReceiver, detachedFilter, null, null, Context.RECEIVER_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        State.log("MediaProjectionService onStartCommand");
        if (intent != null && intent.hasExtra("data")) {
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent data = intent.getParcelableExtra("data");
            State.mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK, data);
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
        State.hasService = false;
        State.log("MediaProjectionService onDestroy");
        unregisterReceiver(usbDetachedReceiver);
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