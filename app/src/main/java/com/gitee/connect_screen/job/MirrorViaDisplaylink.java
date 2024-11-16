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
import com.gitee.connect_screen.UsbState;

public class MirrorViaDisplaylink implements Job {
    private boolean requested = false;
    private final String deviceName;

    public MirrorViaDisplaylink(UsbDevice device) {
        this.deviceName = device.getDeviceName();
        State.getOrCreateUsbState(device);
    }

    public void start() throws YieldException {
        Context context = State.currentActivity.get();
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        UsbState usbState = State.getUsbState(deviceName);

        if (usbState == null) {
            State.log("USB 设备状态不存在，跳过任务");
            return;
        }
        UsbDevice device = usbState.device;

        if (usbManager.hasPermission(device)) {
            State.log("已经拥有USB设备权限: " + device.getDeviceName());
        } else if (requested) {
            State.log("未授予USB设备权限: " + device.getDeviceName());
            return;
        } else {
            requested = true;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, pendingIntent);
            throw new YieldException("等待用户USB授权");
        }

        if (usbState.usbConnection == null) {
            usbState.usbConnection = usbManager.openDevice(device);
            if (usbState.usbConnection == null) {
                throw new RuntimeException("无法打开 USB 设备连接");
            } else {
                State.log("成功打开 USB 设备连接");
            }
        } else {
            State.log("USB 设备连接已存在");
        }
        int fileDescriptor = usbState.usbConnection.getFileDescriptor();
        byte[] rawDescriptors = usbState.usbConnection.getRawDescriptors();
        String deviceName = device.getDeviceName();

        if (usbState.nativeDriver == null) {
            usbState.nativeDriver = new NativeDriver();
            usbState.nativeDriverListener = new NativeDriverListener(deviceName);
            usbState.nativeDriver.destroy();
            int resultCode = usbState.nativeDriver.create(usbState.nativeDriverListener, context.getFilesDir().toString(), false);
            if (resultCode != 0) {
                throw new RuntimeException("创建NativeDriver失败: " + resultCode);
            } else {
                State.log("创建NativeDriver成功");
            }
            usbState.nativeDriver.usbDeviceDetached(deviceName);
            resultCode = usbState.nativeDriver.usbDeviceAttached(deviceName, fileDescriptor, rawDescriptors, rawDescriptors.length);
            if (resultCode != 0) {
                throw new RuntimeException("附加USB设备失败: " + resultCode);
            } else {
                State.log("附加USB设备成功");
            }
        } else {
            State.log("NativeDriver 已经存在，跳过重复创建");
        }

        if (usbState.monitorInfo == null) {
            throw new YieldException("未找到显示器信息, 等待连接ing");
        }
        State.log("ready go: " + usbState.monitorInfo.toString());
    }
}