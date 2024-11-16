package com.gitee.connect_screen;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;
import android.widget.Toast;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
import com.displaylink.manager.display.MonitorInfo;
import com.gitee.connect_screen.job.Job;

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
        } catch (RuntimeException e) {
            currentJob = null;
            State.log("任务 " + job.getClass().getSimpleName() + " 启动失败");
            throw e;
        }
    }

    public static void onJobFinished(Job job) {
        if (currentJob == job) {
            currentJob = null;
        }
    }

    public static void resumeJob() {
        if (currentJob != null) {
            try {
                State.log("恢复任务 " + currentJob.getClass().getSimpleName());
                currentJob.start();
            } catch (RuntimeException e) {
                currentJob = null;
                State.log("任务 " + currentJob.getClass().getSimpleName() + " 恢复失败");
                throw e;
            }
        }
    }

    public static void log(String message) {
        logs.add(message);
        Log.i("ConnectScreen", message);
        if (currentActivity != null && currentActivity.get() != null) {
            currentActivity.get().updateLogs();
        }
    }

    // 新增方法：获取或创建 UsbState
    public static UsbState getOrCreateUsbState(String key) {
        return usbStates.computeIfAbsent(key, k -> new UsbState());
    }

    // 新增方法：获取 UsbState
    public static UsbState getUsbState(String key) {
        return usbStates.get(key);
    }

    // 新增方法：移除 UsbState
    public static void removeUsbState(String key) {
        usbStates.remove(key);
    }
}
