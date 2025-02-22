package com.connect_screen.mirror.job;

import android.content.Context;
import android.content.Intent;

import com.connect_screen.mirror.MediaProjectionService;
import com.connect_screen.mirror.State;

public class ExitAll {
    public static void execute(Context context) {
        if (State.mediaProjectionInUse != null) {
            State.mediaProjectionInUse.stop();
            State.mediaProjectionInUse = null;
        }
        State.setMediaProjection(null);
        // 停止 MediaProjectionService
        context.stopService(new Intent(context, MediaProjectionService.class));

        if (State.bridgeVirtualDisplay != null) {
            State.bridgeVirtualDisplay.release();
            State.bridgeVirtualDisplay = null;
        }
        if (State.mirrorVirtualDisplay != null) {
            State.mirrorVirtualDisplay.release();
            State.mirrorVirtualDisplay = null;
        }
        State.displaylinkState.stopVirtualDisplay();
        State.displaylinkState.destroy();
        State.currentActivity.get().finish();
        
        // 退出应用进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
