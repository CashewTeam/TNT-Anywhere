package com.displaylink.manager;

import android.util.Log;

import com.displaylink.manager.display.MonitorInfo;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;
import com.gitee.connect_screen.job.MirrorViaDisplaylink;

public class NativeDriverListener {
    private final String usbDeviceName;

    public NativeDriverListener(String usbDeviceName) {
        this.usbDeviceName = usbDeviceName;
    }

    public void onDisplayConnected(long encoderId) {
        Log.i("displaylink", "onDisplayConnected");
        State.currentActivity.get().runOnUiThread(() -> {
            State.log("Display已连接, Encoder ID: " + encoderId);
        });
    }

    public void onDisplayDisconnected(long encoderId) {
        Log.i("displaylink", "onDisplayDisconnected");
        State.currentActivity.get().runOnUiThread(() -> {
            UsbState usbState = State.getUsbState(usbDeviceName);
            if (usbState == null) {
                State.log("Display已断开, 但找不到 USB 设备");
            } else {
                State.log("Display已断开, 关闭 usb 对应的状态");
                usbState.stopVirtualDisplay();
                usbState.encoderId = 0;
                usbState.monitorInfo = null;
                State.resumeJob();
            }
        });
    }

    public void onError(int i) {
        Log.i("displaylink", "onError: " + i);
    }

    public void onFirmwareUpdateInfo(boolean z) {
        Log.i("displaylink", "onFirmwareUpdateInfo");
    }

    public void onUpdateMonitorInfo(long encoderId, MonitorInfo monitorInfo) {
        Log.i("displaylink", "onUpdateMonitorInfo");
        State.currentActivity.get().runOnUiThread(() -> {
            State.log("onUpdateMonitorInfo: " + monitorInfo.toString());
            UsbState usbState = State.getUsbState(usbDeviceName);
            if (usbState != null) {
                boolean wasNoMonitor = usbState.monitorInfo == null;
                usbState.encoderId = encoderId;
                usbState.monitorInfo = monitorInfo;
                if (!State.isJobRunning() && wasNoMonitor) {
                    State.startNewJob(new MirrorViaDisplaylink(usbState.device, usbState.virtualDisplayArgs));
                }
            }
        });
    }
}
