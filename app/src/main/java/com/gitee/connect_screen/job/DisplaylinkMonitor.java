package com.gitee.connect_screen.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.Display;
import android.view.InputDevice;

import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.State;

public class DisplaylinkMonitor {
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
