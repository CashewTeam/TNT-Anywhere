package com.displaylink.manager;

import android.content.Context;
import android.util.Log;

import com.displaylink.manager.display.MonitorInfo;


public class NativeDriverListener {
    public Context context;

    public NativeDriverListener(Context context) {
    }

    public void onDisplayConnected(long encoderId) {
        Log.i("displaylink", "onDisplayConnected");
    }

    public void onDisplayDisconnected(long encoderId) {
        Log.i("displaylink", "onDisplayDisconnected");
    }

    public void onError(int i) {
        Log.i("displaylink", "onError");
    }

    public void onFirmwareUpdateInfo(boolean z) {
        Log.i("displaylink", "onFirmwareUpdateInfo");
    }

    public void onUpdateMonitorInfo(long encoderId, MonitorInfo monitorInfo) {
    }
}
