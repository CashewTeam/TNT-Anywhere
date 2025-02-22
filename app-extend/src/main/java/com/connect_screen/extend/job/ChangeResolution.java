package com.connect_screen.extend.job;

import android.app.Activity;
import android.graphics.Point;
import android.view.IWindowManager;

import androidx.appcompat.app.AlertDialog;

import com.connect_screen.extend.State;
import com.connect_screen.extend.shizuku.ServiceUtils;

public class ChangeResolution implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;
    private final int width;
    private final int height;
    private int oldWidth;
    private int oldHeight;
    private volatile boolean confirmed = false;
    private boolean sleep1 = false;
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
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        if (!sleep1) {
            sleep1 = true;
            IWindowManager wm = ServiceUtils.getWindowManager();
            Point baseSize = new Point();
            wm.getBaseDisplaySize(displayId, baseSize);
            oldWidth = baseSize.x;
            oldHeight = baseSize.y;
            wm.setForcedDisplaySize(displayId, width, height);
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Activity activity = State.currentActivity.get();
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            State.resumeJob();
                        });
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }).start();
            throw new YieldException("2000ms后弹窗确认");
        }
        if (!requestedConfirmation) {
            requestedConfirmation = true;
            dialog = new AlertDialog.Builder(State.currentActivity.get())
            .setTitle("修改分辨率")
            .setMessage(String.format("是否将分辨率从 %dx%d 修改为 %dx%d?", oldWidth, oldHeight, width, height))
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
            dialog.dismiss();
            ServiceUtils.getWindowManager().setForcedDisplaySize(displayId, oldWidth, oldHeight);
        }
    }

}
