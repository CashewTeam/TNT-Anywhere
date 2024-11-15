package com.gitee.connect_screen.job;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.State;

public class RequestUsbPermission implements Job {
    private final UsbDevice device;
    private boolean requested = false;

    public RequestUsbPermission(UsbDevice device) {
        this.device = device;
    }

    public void start() {
        // 请求USB权限
        Context context = State.currentActivity.get();
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // 检查是否已经有权限
        if (usbManager.hasPermission(device)) {
            Toast.makeText(context, "已经拥有USB设备权限", Toast.LENGTH_SHORT).show();
        } else if (requested) {
            Toast.makeText(context, "未授予USB设备权限", Toast.LENGTH_SHORT).show();
        } else {
            requested = true;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, pendingIntent);
            return;
        }
        State.onJobFinished(this);
    }
}