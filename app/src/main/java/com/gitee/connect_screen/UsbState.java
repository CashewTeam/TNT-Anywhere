package com.gitee.connect_screen;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
import com.displaylink.manager.display.MonitorInfo;

public class UsbState {
    public UsbDevice device;
    public NativeDriver nativeDriver;
    public NativeDriverListener nativeDriverListener;
    public UsbDeviceConnection usbConnection;
    public long encoderId = 0;
    public MonitorInfo monitorInfo;
}
