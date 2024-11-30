package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;

import com.gitee.connect_screen.LauncherActivity;
import com.gitee.connect_screen.State;

import rikka.shizuku.Shizuku;

public class AcquireShizukuAndStartLauncher implements Job {

    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    
    private final int displayId;

    public AcquireShizukuAndStartLauncher(int displayId) {
        this.displayId = displayId;
    }

    @Override
    public void start() throws YieldException {
        if (State.virtualDisplayIds.contains(displayId)) {
            acquireShizuku.start();
            if (!acquireShizuku.acquired) {
                return;
            }
        }
        Context context = State.currentActivity.get();
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
        context.startActivity(intent);
    }
}
