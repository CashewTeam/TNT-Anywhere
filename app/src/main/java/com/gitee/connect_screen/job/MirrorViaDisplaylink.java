package com.gitee.connect_screen.job;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.State;

public class MirrorViaDisplaylink implements Job {
    private final UsbDevice device;
    private boolean requested = false;

    public MirrorViaDisplaylink(UsbDevice device) {
        this.device = device;
    }

    public void start() {
        // 请求USB权限
        Context context = State.currentActivity.get();
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // 检查是否已经有权限
        if (usbManager.hasPermission(device)) {
            State.log("已经拥有USB设备权限: " + device.getDeviceName());
        } else if (requested) {
            State.log("未授予USB设备权限: " + device.getDeviceName());
        } else {
            requested = true;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, pendingIntent);
            return;
        }
        State.onJobFinished(this);
    }
}