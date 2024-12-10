package com.gitee.connect_screen.job;

import android.view.Surface;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.ServiceUtils;

public class ChangeRotation implements Job {
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
        try {
            ServiceUtils.getWindowManager().freezeDisplayRotation(displayId, rotation, "WindowManagerShellCommand#lock");
            State.log("旋转设置已应用");
        } catch (Exception e) {
            State.log("设置旋转失败：" + e.getMessage());
        }
    }
}
