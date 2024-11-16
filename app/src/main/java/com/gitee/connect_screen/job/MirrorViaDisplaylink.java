package com.gitee.connect_screen.job;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
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
        
        if (State.usbConnection == null) {
            State.usbConnection = usbManager.openDevice(device);
            if (State.usbConnection == null) {
                throw new RuntimeException("无法打开 USB 设备连接");
            } else {
                State.log("成功打开 USB 设备连接");
            }
        } else {
            State.log("USB 设备连接已存在");
        }
        int fileDescriptor = State.usbConnection.getFileDescriptor();
        byte[] rawDescriptors = State.usbConnection.getRawDescriptors();
        String deviceName = device.getDeviceName();
       
        if (State.nativeDriver == null) {
            State.nativeDriver = new NativeDriver();
            State.nativeDriverListener = new NativeDriverListener();
            int resultCode = State.nativeDriver.create(State.nativeDriverListener, context.getFilesDir().toString(), false);
            if (resultCode != 0) {
                throw new RuntimeException("创建NativeDriver失败: " + resultCode);
            } else {
                State.log("创建NativeDriver成功");
            }
        } else {
            State.log("NativeDriver 已经存在，跳过重复创建");
        }
        State.nativeDriver.usbDeviceDetached(deviceName);
        int resultCode = State.nativeDriver.usbDeviceAttached(deviceName, fileDescriptor, rawDescriptors, rawDescriptors.length);
        if (resultCode != 0) {
            throw new RuntimeException("usbDeviceAttached 失败: " + resultCode);
        } else {
            State.log("usbDeviceAttached 成功");
        }
        State.onJobFinished(this);
    }
}