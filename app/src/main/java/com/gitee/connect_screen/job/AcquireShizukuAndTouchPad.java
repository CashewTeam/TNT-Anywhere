package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.TouchpadActivity;
import com.gitee.connect_screen.job.Job;

public class AcquireShizukuAndTouchPad implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;

    public AcquireShizukuAndTouchPad(int displayId) {
        this.displayId = displayId;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        Context context = State.currentActivity.get();
        Intent intent = new Intent(context, TouchpadActivity.class);
        intent.putExtra("display_id", displayId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
