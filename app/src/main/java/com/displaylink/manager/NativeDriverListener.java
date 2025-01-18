package com.displaylink.manager;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.displaylink.manager.display.MonitorInfo;
import com.gitee.connect_screen.DisplaylinkPref;
import com.gitee.connect_screen.ProjectionMode;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.DisplaylinkState;
import com.gitee.connect_screen.job.ProjectViaDisplaylink;
import com.gitee.connect_screen.job.VirtualDisplayArgs;

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
                    DisplaylinkPref.load(context);
                    State.displaylinkState.virtualDisplayArgs = new VirtualDisplayArgs("DisplayLink", DisplaylinkPref.monitorWidth, DisplaylinkPref.monitorHeight, DisplaylinkPref.monitorWidth, DisplaylinkPref.refreshRate, DisplaylinkPref.dpi, DisplaylinkPref.rotatesWithContent);
                    State.startNewJob(new ProjectViaDisplaylink(displaylinkState.device, displaylinkState.virtualDisplayArgs, ProjectionMode.MIRROR));
                }
            }
        });
    }
}
