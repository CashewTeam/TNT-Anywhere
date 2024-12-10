package com.gitee.connect_screen.job;

import com.gitee.connect_screen.shizuku.ServiceUtils;

public class ChangeDPI implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    public final int displayId;
    public final int dpi;

    public ChangeDPI(int displayId, int dpi) {
        this.displayId = displayId;
        this.dpi = dpi;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        ServiceUtils.getWindowManager().setForcedDisplayDensityForUser(displayId, dpi, 0);
    }
}
