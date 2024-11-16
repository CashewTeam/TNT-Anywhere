package com.gitee.connect_screen;

import android.hardware.display.VirtualDisplay;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.display.DisplayManager;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
import com.displaylink.manager.display.MonitorInfo;
import com.displaylink.manager.display.DisplayMode;

public class UsbState {
    public UsbDevice device;
    public NativeDriver nativeDriver;
    public NativeDriverListener nativeDriverListener;
    public UsbDeviceConnection usbConnection;
    public long encoderId = 0;
    public MonitorInfo monitorInfo;
    public ImageReader imageReader;
    public VirtualDisplay virtualDisplay;
    public HandlerThread handlerThread;
    public Handler handler;
    public volatile int frameCounter = 0;
    public volatile int[] recentPostFrameResultCodes = new int[8];

    public void destroy() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        if (imageReader != null) {
            imageReader.close();
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (nativeDriver != null) { 
            nativeDriver.destroy();
        }
        if (usbConnection != null) {
            usbConnection.close();
        }
    }
}
