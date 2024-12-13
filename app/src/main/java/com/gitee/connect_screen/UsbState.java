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
import com.gitee.connect_screen.job.MirrorArgs;

public class UsbState {
    public UsbDevice device;
    public UsbDeviceConnection usbConnection;
    public UsbDevice displaylinkDevice2;
    public UsbDeviceConnection displaylinkConnection2;
    public NativeDriver nativeDriver;
    public NativeDriverListener nativeDriverListener;
    public long encoderId = 0;
    public MonitorInfo monitorInfo;
    public ImageReader imageReader;
    public MirrorArgs mirrorArgs = new MirrorArgs();
    private volatile VirtualDisplay virtualDisplay;
    public HandlerThread handlerThread;
    public Handler handler;
    public volatile int frameCounter = 0;
    public volatile int[] recentPostFrameResultCodes = new int[8];
    public ProjectionMode projectionMode;
    public int monitorWidth;
    public int monitorHeight;
    public int sourceWidth;
    public int sourceHeight;
    public int refreshRate;
    public boolean rotatesWithContent = true;

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
        State.virtualDisplayIds.put(virtualDisplay.getDisplay().getDisplayId(), device);
    }   

    public VirtualDisplay getVirtualDisplay() {
        return virtualDisplay;
    }   

    public void stopVirtualDisplay() {
        VirtualDisplay toStop = virtualDisplay;
        virtualDisplay = null;
        if (toStop != null) {
            State.virtualDisplayIds.remove(toStop.getDisplay().getDisplayId());
        }
        stopHandlerThread();
        stopImageReader();
        if (toStop != null) {
            toStop.release();
        }
    }

    public void destroy() {
        stopVirtualDisplay();
        if (nativeDriver != null) {
            nativeDriver.usbDeviceDetached(device.getDeviceName());
            if (displaylinkDevice2 != null) {
                nativeDriver.usbDeviceDetached(displaylinkDevice2.getDeviceName());
            }
            nativeDriver.destroy();
            nativeDriver = null;
        }
        if (usbConnection != null) {
            usbConnection.close();
        }
        if (displaylinkConnection2 != null) {
            displaylinkConnection2.close();
        }
        monitorInfo = null;
        encoderId = 0;
    }
}
