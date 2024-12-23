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
        if (isHid(device) && State.lastSingleAppDisplay > 0) {
            MainActivity mainActivity = State.currentActivity.get();
            if (mainActivity != null) {
                InputManager inputManager = (InputManager) mainActivity.getSystemService(Context.INPUT_SERVICE);
                DisplayManager displayManager = (DisplayManager) mainActivity.getSystemService(Context.DISPLAY_SERVICE);
                Display display = displayManager.getDisplay(State.lastSingleAppDisplay);
                if (display != null) {
                    State.log("usb attached to bind input to display");
                    InputDevice inputDevice = InputRouting.findInputDevice(inputManager, device);
                    State.startNewJob(new BindInputToDisplay(inputDevice, display));
                }
            }
        }
    }

    private static boolean isHid(UsbDevice device) {
        if (device.getInterfaceCount() > 0) {
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                if (device.getInterface(i).getInterfaceClass() == 3) { // HID class is 3
                    return true;
                }
            }
        }
        return false;
    }

    public static void onUsbDeviceDetached(UsbDevice device) {
        if (device != null && device.getDeviceName().equals(State.displaylinkDeviceName)) {
            State.displaylinkDeviceName = null;
        }
    }
}
