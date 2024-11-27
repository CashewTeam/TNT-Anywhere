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
    private VirtualDisplay virtualDisplay;
    public HandlerThread handlerThread;
    public Handler handler;
    public volatile int frameCounter = 0;
    public volatile int[] recentPostFrameResultCodes = new int[8];
    public int overrideMonitorWidth;
    public int overrideMonitorHeight;
    public ProjectionMode projectionMode;

    public int getMonitorWidth() {
        return overrideMonitorWidth != 0 ? overrideMonitorWidth : monitorInfo.a[0].width;
    }

    public int getMonitorHeight() {
        return overrideMonitorHeight != 0 ? overrideMonitorHeight : monitorInfo.a[0].height;
    }   

    public DisplayMode getDisplayMode() {
        if (overrideMonitorWidth != 0 && overrideMonitorHeight != 0) {
            return new DisplayMode(overrideMonitorWidth, overrideMonitorHeight, monitorInfo.a[0].refreshRate);
        }   
        return monitorInfo.a[0];
    }

    public void stopHandlerThread() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        handler = null;
        handlerThread = null;
    }

    public void stopImageReader() {
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    public void createdVirtualDisplay(VirtualDisplay virtualDisplay) {
        this.virtualDisplay = virtualDisplay;
        State.virtualDisplayIds.add(virtualDisplay.getDisplay().getDisplayId());
    }   

    public VirtualDisplay getVirtualDisplay() {
        return virtualDisplay;
    }   

    public void stopVirtualDisplay() {
        if (virtualDisplay != null) {
            State.virtualDisplayIds.remove(virtualDisplay.getDisplay().getDisplayId());
        }
        stopHandlerThread();
        stopImageReader();
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    public void destroy() {
        stopVirtualDisplay();
        if (nativeDriver != null) { 
            nativeDriver.destroy();
        }
        if (usbConnection != null) {
            usbConnection.close();
        }
    }
}
