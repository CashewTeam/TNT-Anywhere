package com.displaylink.manager;

import android.app.Activity;
import android.util.Log;

import com.displaylink.manager.display.MonitorInfo;
import com.connect_screen.extend.DisplaylinkPref;
import com.connect_screen.extend.MainActivity;
import com.connect_screen.extend.ProjectionMode;
import com.connect_screen.extend.State;
import com.connect_screen.extend.DisplaylinkState;
import com.connect_screen.extend.job.ProjectViaDisplaylink;
import com.connect_screen.extend.job.VirtualDisplayArgs;

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
            DisplaylinkState displaylinkState = State.displaylinkState;
            if (displaylinkState == null) {
                State.log("Display已断开, 但找不到 USB 设备");
            } else {
                State.log("Display已断开, 关闭 usb 对应的状态");
                displaylinkState.encoderId = 0;
                displaylinkState.monitorInfo = null;
            }
        });
    }

    public void onError(int i) {
        Log.i("displaylink", "onError: " + i);
        State.currentActivity.get().runOnUiThread(() -> {
            State.log("Displaylink 报告故障码：" + i);
        });
    }

    public void onFirmwareUpdateInfo(boolean z) {
        Log.i("displaylink", "onFirmwareUpdateInfo");
    }

    public void onUpdateMonitorInfo(long encoderId, MonitorInfo monitorInfo) {
        Log.i("displaylink", "onUpdateMonitorInfo");
        Activity context = State.currentActivity.get();
        if (context == null) {
            return;
        }
        context.runOnUiThread(() -> {
            State.log("onUpdateMonitorInfo: " + monitorInfo.toString());
            DisplaylinkState displaylinkState = State.displaylinkState;
            if (displaylinkState != null) {
                boolean wasNoMonitor = displaylinkState.monitorInfo == null;
                displaylinkState.encoderId = encoderId;
                displaylinkState.monitorInfo = monitorInfo;
                if (!State.isJobRunning() && wasNoMonitor) {
                    boolean useSingleApp = State.currentActivity.get() instanceof MainActivity;
                    DisplaylinkPref.load(context);
                    State.displaylinkState.virtualDisplayArgs = new VirtualDisplayArgs("DisplayLink", DisplaylinkPref.monitorWidth, DisplaylinkPref.monitorHeight, DisplaylinkPref.refreshRate, DisplaylinkPref.dpi, DisplaylinkPref.rotatesWithContent);
                    State.startNewJob(new ProjectViaDisplaylink(displaylinkState.device, displaylinkState.virtualDisplayArgs, useSingleApp ? ProjectionMode.SINGLE_APP : ProjectionMode.MIRROR));
                }
            }
        });
    }
}
