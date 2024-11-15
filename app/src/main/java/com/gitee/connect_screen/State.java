package com.gitee.connect_screen;

import android.widget.Toast;

import com.gitee.connect_screen.job.Job;

import java.lang.ref.WeakReference;

public class State {
    // 弱引用保存当前的 MainActivity 实例
    public static WeakReference<MainActivity> currentActivity;
    private static Job currentJob;

    public static void startNewJob(Job job) {
        synchronized(State.class) { 
            if (currentJob != null) {
                if (currentActivity != null && currentActivity.get() != null) {
                    Toast.makeText(currentActivity.get(), "当前任务 " + currentJob.getClass().getSimpleName() + " 正在进行中", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            currentJob = job;
            try {
                currentJob.start();
            } catch(RuntimeException e) {
                currentJob = null;
                Toast.makeText(currentActivity.get(), "任务 " + job.getClass().getSimpleName() + " 启动失败", Toast.LENGTH_SHORT).show();
                throw e;
            }
        }   
    }

    public static void onJobFinished(Job job) {
        synchronized(State.class) { 
            if (currentJob == job) {
                currentJob = null;
            }
        }
    }

    public static void resumeJob() {
        synchronized(State.class) { 
            if (currentJob != null) {
                try {
                    currentJob.start();
                } catch(RuntimeException e) {
                    currentJob = null;
                    Toast.makeText(currentActivity.get(), "任务 " + currentJob.getClass().getSimpleName() + " 恢复失败", Toast.LENGTH_SHORT).show();
                    throw e;
                }
            }
        }
    }
};
