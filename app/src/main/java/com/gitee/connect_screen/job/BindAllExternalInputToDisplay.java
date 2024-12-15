package com.gitee.connect_screen.job;

import android.hardware.input.IInputManager;
import android.view.DisplayInfo;
import android.view.InputDevice;

import com.gitee.connect_screen.shizuku.ServiceUtils;

import java.util.Map;

public class BindAllExternalInputToDisplay implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;

    public BindAllExternalInputToDisplay(int displayId) {
        this.displayId = displayId;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        InputRouting.bindAllExternalInputToDisplay(displayId);
    }
}
