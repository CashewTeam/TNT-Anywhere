package com.displaylink.manager;

import android.content.Context;
import android.util.Log;

import com.displaylink.manager.display.MonitorInfo;
import com.gitee.connect_screen.State;


public class NativeDriverListener {
    public void onDisplayConnected(long encoderId) {
        Log.i("displaylink", "onDisplayConnected");
        State.currentActivity.get().runOnUiThread(() -> {
            State.log("Display已连接, Encoder ID: " + encoderId);
        });
    }

    public void onDisplayDisconnected(long encoderId) {
        State.log("onDisplayDisconnected");
    }

    public void onError(int i) {
        State.log("onError");
    }

    public void onFirmwareUpdateInfo(boolean z) {
        State.log("onFirmwareUpdateInfo");
    }

    public void onUpdateMonitorInfo(long encoderId, MonitorInfo monitorInfo) {
        Log.i("displaylink", "onUpdateMonitorInfo");
        State.currentActivity.get().runOnUiThread(() -> {
            State.log("onUpdateMonitorInfo, Encoder ID: " + encoderId);
        });
    }
}
