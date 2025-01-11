package com.gitee.connect_screen.job;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.PermissionManager;

import android.provider.Settings;

public class TermuxDisablePhantomProcess implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;

    public TermuxDisablePhantomProcess(int displayId) {
        this.displayId = displayId;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        InputRouting.bindAllExternalInputToDisplay(displayId);
        if (PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
            try {
                if (State.userService != null) {
                    State.userService.executeCommand("/system/bin/device_config set_sync_disabled_for_tests persistent");
                    State.userService.executeCommand("/system/bin/device_config put activity_manager max_phantom_processes 2147483647");
                }
                Settings.Global.putInt(State.currentActivity.get().getContentResolver(),
                        "settings_enable_monitor_phantom_procs", 0);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
