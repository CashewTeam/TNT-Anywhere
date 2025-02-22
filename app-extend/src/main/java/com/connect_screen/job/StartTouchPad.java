package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.TouchpadAccessibilityService;
import com.gitee.connect_screen.TouchpadActivity;

public class StartTouchPad implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;
    private final Context context;

    public StartTouchPad(int displayId, Context context) {
        this.displayId = displayId;
        this.context = context;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        Intent intent = new Intent(context, TouchpadActivity.class);
        intent.putExtra("display_id", displayId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        TouchpadAccessibilityService.startServiceByShizuku(context);
    }
}
