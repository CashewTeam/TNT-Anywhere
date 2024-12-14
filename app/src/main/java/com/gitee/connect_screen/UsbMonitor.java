package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

public class UsbMonitor {
    public static void onUsbDeviceAttached(UsbDevice device) {
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
    public static void onUsbDeviceDetached(UsbDevice device) {
        if (device != null && device.getDeviceName().equals(State.displaylinkDeviceName)) {
            State.displaylinkDeviceName = null;
        }
    }
}
