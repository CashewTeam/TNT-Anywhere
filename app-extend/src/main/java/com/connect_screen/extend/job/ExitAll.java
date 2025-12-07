package com.connect_screen.extend.job;

import android.content.Context;
import android.content.Intent;

import com.connect_screen.extend.BridgeActivity;
import com.connect_screen.extend.FloatingButtonService;
import com.connect_screen.extend.MediaProjectionService;
import com.connect_screen.extend.State;
import com.connect_screen.extend.TouchpadAccessibilityService;

public class ExitAll {
    public static void execute(Context context) {
        if (State.mediaProjectionInUse != null) {
            State.mediaProjectionInUse.stop();
            State.mediaProjectionInUse = null;
        }
        State.setMediaProjection(null);
        // 停止 MediaProjectionService
        context.stopService(new Intent(context, MediaProjectionService.class));

        // 停止 FloatingButtonService
        context.stopService(new Intent(context, FloatingButtonService.class));

        // 停止 TouchpadAccessibilityService
        Intent touchpadIntent = new Intent(context, TouchpadAccessibilityService.class);
        touchpadIntent.setAction(TouchpadAccessibilityService.class.getName());
        context.stopService(touchpadIntent);

        if (BridgeActivity.getInstance() != null) {
            BridgeActivity.getInstance().finish();
        }
        if (State.bridgeVirtualDisplay != null) {
            State.bridgeVirtualDisplay.release();
            State.bridgeVirtualDisplay = null;
        }
        if (State.mirrorVirtualDisplay != null) {
            State.mirrorVirtualDisplay.release();
            State.mirrorVirtualDisplay = null;
        }
        State.currentActivity.get().finish();
        
        // 退出应用进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
