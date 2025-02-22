package com.connect_screen.mirror.job;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.gitee.connect_screen.ProjectionMode;
import com.gitee.connect_screen.State;

public class DisplaylinkMonitor {

    private static final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                DisplaylinkMonitor.onUsbDeviceDetached(device);
            }
        }
    };

    // 添加一个新的广播接收器来处理 USB 设备连接
    private static final BroadcastReceiver usbAttachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                DisplaylinkMonitor.onUsbDeviceAttached(device);
                if (device != null && device.getVendorId() == 6121) {
                    State.log("USB 设备已连接: " + device.getDeviceName());
                    if (device.getDeviceName().equals(State.displaylinkDeviceName)) {
                        State.log("识别为 Displaylink: " + device.getDeviceName());
                        State.startNewJob(new ProjectViaDisplaylink(device, State.displaylinkState.virtualDisplayArgs, ProjectionMode.MIRROR));
                    } else {
                        State.log("已有其他 Displaylink: " + State.displaylinkDeviceName);
                    }
                }
            }
        }
    };

    public static void init(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            handleDisplaylink(usbDevice);
        }
        // 注册 USB 设备断开广播接收器
        IntentFilter detachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbDetachedReceiver, detachedFilter, null, null, Context.RECEIVER_EXPORTED);

        // 注册 USB 设备连接广播接收器
        IntentFilter attachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        context.registerReceiver(usbAttachedReceiver, attachedFilter, null, null, Context.RECEIVER_EXPORTED);
    }
    public static void handleDisplaylink(UsbDevice device) {
        if (device == null) {
            return;
        }
        if (State.displaylinkDeviceName != null) {
            Activity mainActivity = State.currentActivity.get();
            if (mainActivity != null) {
                UsbManager usbManager = (UsbManager) mainActivity.getSystemService(Context.USB_SERVICE);
                if (usbManager.getDeviceList().get(State.displaylinkDeviceName) == null) {
                    State.displaylinkState.destroy();
                    State.displaylinkDeviceName = null;
                    State.displaylinkState.device = null;
                }
            }
        }
        if (device.getVendorId() == 6121 && State.displaylinkDeviceName == null) {
            State.displaylinkDeviceName = device.getDeviceName();
            State.displaylinkState.device = device;
            State.log("发现 Displaylink 设备：" + device.getProductName());
        }
    }
    public static void onUsbDeviceAttached(UsbDevice device) {
        if (device == null) {
            return;
        }
        handleDisplaylink(device);
    }

    public static void onUsbDeviceDetached(UsbDevice device) {
        if (device != null && device.getDeviceName().equals(State.displaylinkDeviceName)) {
            State.log("Displaylink设备断开：" + device.getProductName());
            State.displaylinkState.destroy();
            State.displaylinkDeviceName = null;
            State.displaylinkState.device = null;
            State.breadcrumbManager.refreshCurrentFragment();
        }
    }

}
