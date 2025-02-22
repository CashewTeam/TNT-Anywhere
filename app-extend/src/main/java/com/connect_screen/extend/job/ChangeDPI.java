package com.connect_screen.extend.job;

import com.connect_screen.extend.State;
import com.connect_screen.extend.shizuku.ServiceUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.IWindowManager;

public class ChangeDPI implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    public final int displayId;
    public final int dpi;
    private int oldDpi;
    private volatile boolean confirmed = false;
    private boolean sleep1 = false;
    private boolean requestedConfirmation = false;
    private Thread timeoutThread;
    private AlertDialog dialog;

    public ChangeDPI(int displayId, int dpi, int oldDpi) {
        this.displayId = displayId;
        this.dpi = dpi;
        this.oldDpi = oldDpi;
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
            wm.setForcedDisplayDensityForUser(displayId, dpi, 0);
            
            new Thread(() -> {
                try {
                    Thread.sleep(500);
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
            throw new YieldException("500ms后弹窗确认");
        }

        if (!requestedConfirmation) {
            requestedConfirmation = true;
            dialog = new AlertDialog.Builder(State.currentActivity.get())
                .setTitle("修改DPI")
                .setMessage(String.format("是否将DPI从 %d 修改为 %d?", oldDpi, dpi))
                .setPositiveButton("确定", (dialog, which) -> {
                    ChangeDPI.this.confirmed = true;
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
            throw new YieldException("等待确认新的DPI");
        }

        timeoutThread.interrupt();
        if (!confirmed) {
            State.log("恢复DPI");
            dialog.dismiss();
            ServiceUtils.getWindowManager().setForcedDisplayDensityForUser(displayId, oldDpi, 0);
        }
    }
}
