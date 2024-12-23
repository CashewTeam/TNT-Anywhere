package com.gitee.connect_screen.job;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.Display;
import android.view.InputDevice;

import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.State;

public class DisplaylinkMonitor {

    private static final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && State.getUsbState(device.getDeviceName()) != null) {
                    State.log("USB 设备已断开: " + device.getDeviceName());
                    State.removeUsbState(device.getDeviceName());
                    State.resumeJob();
                }
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
                        State.startNewJob(new ProjectViaDisplaylink(device, State.getOrCreateUsbState(device).virtualDisplayArgs));
                    } else {
                        State.log("已有其他 Displaylink: " + State.displaylinkDeviceName);
                    }
                }
            }
        }
    };

    public static void init(Context context) {
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
            MainActivity mainActivity = State.currentActivity.get();
            if (mainActivity != null) {
                UsbManager usbManager = (UsbManager) mainActivity.getSystemService(Context.USB_SERVICE);
                if (usbManager.getDeviceList().get(State.displaylinkDeviceName) == null) {
                    State.displaylinkDeviceName = null;
                }
            }
        }
        if (device.getVendorId() == 6121 && State.displaylinkDeviceName == null) {
            State.displaylinkDeviceName = device.getDeviceName();
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
            State.displaylinkDeviceName = null;
        }
    }

}
