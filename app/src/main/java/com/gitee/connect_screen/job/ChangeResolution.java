package com.gitee.connect_screen.job;

import android.app.Activity;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.gitee.connect_screen.State;

public class ChangeResolution implements Job {
    private final int displayId;
    private final int width;
    private final int height;
    private volatile boolean confirmed = false;
    private boolean requestedConfirmation = false;
    private Thread timeoutThread;
    private AlertDialog dialog;

    public ChangeResolution(int displayId, int width, int height) {
        this.displayId = displayId;
        this.width = width;
        this.height = height;
    }
    @Override
    public void start() throws YieldException {
        Context context = State.currentActivity.get();
        
        if (!requestedConfirmation) {
            requestedConfirmation = true;
            dialog = new AlertDialog.Builder(context)
            .setTitle("修改分辨率")
            .setMessage(String.format("是否将分辨率修改为 %dx%d?", width, height))
            .setPositiveButton("确定", (dialog, which) -> {
                ChangeResolution.this.confirmed = true;
                State.resumeJob();
            })
            .setNegativeButton("取消", (dialog, which) -> {
                State.resumeJob();
            })
            .show();
            timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    Activity activity = State.currentActivity.get();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (dialog != null && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                            State.resumeJob();
                        });
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            });
            timeoutThread.start();
            throw new YieldException("等待确认新的分辨率");
        }
        timeoutThread.interrupt();
        if (!confirmed) {
            State.log("恢复分辨率");
        }
    }

}
