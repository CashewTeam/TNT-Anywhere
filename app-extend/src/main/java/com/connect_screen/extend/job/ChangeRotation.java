package com.connect_screen.extend.job;

import android.view.IWindowManager;

import com.connect_screen.extend.State;
import com.connect_screen.extend.shizuku.ServiceUtils;

public class ChangeRotation implements Job {
    private final static int FIXED_TO_USER_ROTATION_DEFAULT = 0;
    private final static int FIXED_TO_USER_ROTATION_ENABLED = 2;
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    public final int displayId;
    public final int rotation;

    public ChangeRotation(int displayId, int rotation) {
        this.displayId = displayId;
        this.rotation = rotation;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        IWindowManager windowManager = ServiceUtils.getWindowManager();
        if (rotation == -1) {
            try {
                windowManager.setIgnoreOrientationRequest(displayId, false);
            } catch(Throwable e) {
                State.log("failed to setIgnoreOrientationRequest" + e.getMessage());
            }
            try {
                windowManager.setFixedToUserRotation(displayId, FIXED_TO_USER_ROTATION_DEFAULT);
            } catch(Throwable e) {
                State.log("failed to setFixedToUserRotation" + e.getMessage());
            }
            try {
                windowManager.thawDisplayRotation(displayId, "WindowManagerShellCommand#free");
                State.log("旋转设置已应用");
            } catch (Error e) {
                try {
                    windowManager.thawDisplayRotation(displayId);
                    State.log("旋转设置已应用");
                } catch(Throwable e2) {
                    State.log("设置旋转失败：" + e2.getMessage());
                }
            }
        } else {
            try {
                windowManager.setIgnoreOrientationRequest(displayId, true);
            } catch(Throwable e) {
                State.log("failed to setIgnoreOrientationRequest" + e.getMessage());
            }
            try {
                windowManager.setFixedToUserRotation(displayId, FIXED_TO_USER_ROTATION_ENABLED);
            } catch(Throwable e) {
                State.log("failed to setFixedToUserRotation" + e.getMessage());
            }
            try {
                windowManager.freezeDisplayRotation(displayId, rotation, "WindowManagerShellCommand#lock");
                State.log("旋转设置已应用");
            } catch (Error e) {
                try {
                    windowManager.freezeDisplayRotation(displayId, rotation);
                    State.log("旋转设置已应用");
                } catch(Throwable e2) {
                    State.log("设置旋转失败：" + e2.getMessage());
                }
            }
        }
    }
}
