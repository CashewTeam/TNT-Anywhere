package com.gitee.connect_screen;

import android.util.Log;
import android.widget.Toast;

import com.gitee.connect_screen.job.Job;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class State {
    // 弱引用保存当前的 MainActivity 实例
    public static WeakReference<MainActivity> currentActivity;
    private static Job currentJob;
    public static List<String> logs = new ArrayList<>();

    public static void startNewJob(Job job) {
        if (currentJob != null) {
            if (currentActivity != null && currentActivity.get() != null) {
                State.log("当前任务 " + currentJob.getClass().getSimpleName() + " 正在进行中");
            }
            return;
        }
        currentJob = job;
        try {
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
};
