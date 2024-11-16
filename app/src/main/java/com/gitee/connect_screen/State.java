package com.gitee.connect_screen;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import com.gitee.connect_screen.job.Job;
import com.gitee.connect_screen.job.YieldException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class State {
    // 弱引用保存当前的 MainActivity 实例
    public static WeakReference<MainActivity> currentActivity;
    private static Job currentJob;
    public static List<String> logs = new ArrayList<>();
    public static Map<String, UsbState> usbStates = new HashMap<>();

    public static void startNewJob(Job job) {
        if (currentJob != null) {
            if (currentActivity != null && currentActivity.get() != null) {
                State.log("当前任务 " + currentJob.getClass().getSimpleName() + " 正在进行中");
            }
            return;
        }
        currentJob = job;
        try {
            State.log("开始任务 " + job.getClass().getSimpleName());
            currentJob.start();
            State.log("任务 " + job.getClass().getSimpleName() + " 完成");
            currentJob = null;
        } catch (YieldException e) {
            State.log("任务 " + job.getClass().getSimpleName() + " 暂停, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("任务 " + job.getClass().getSimpleName() + " 启动失败");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("堆栈跟踪: " + stackTrace);
            currentJob = null;
        }
    }

    public static void resumeJob() {
        if (currentJob == null) {
            return;
        }
        try {
            State.log("恢复任务 " + currentJob.getClass().getSimpleName());
            currentJob.start();
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 完成");
            currentJob = null;
        } catch (YieldException e) {
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 暂停, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 恢复失败");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("堆栈跟踪: " + stackTrace);
            currentJob = null;
        }
    }

    public static void log(String message) {
        logs.add(message);
        Log.i("ConnectScreen", message);
        if (currentActivity != null && currentActivity.get() != null) {
            currentActivity.get().updateLogs();
        }
    }

    public static UsbState getOrCreateUsbState(UsbDevice device) {
        UsbState usbState = usbStates.computeIfAbsent(device.getDeviceName(), k -> new UsbState());
        usbState.device = device;
        return usbState;
    }

    public static UsbState getUsbState(String deviceName) {
        return usbStates.get(deviceName);
    }

    public static void removeUsbState(String deviceName) {
        UsbState usbState = usbStates.get(deviceName);
        if (usbState != null) {
            usbState.destroy();
        }
        usbStates.remove(deviceName);
    }
}
