package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;

import com.gitee.connect_screen.BridgeActivity;
import com.gitee.connect_screen.FloatingButtonService;
import com.gitee.connect_screen.MediaProjectionService;
import com.gitee.connect_screen.MirrorActivity;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.TouchpadAccessibilityService;

public class ExitAll {
    public static void execute(Context context) {

        if (State.mediaProjectionInUse != null) {
            State.mediaProjectionInUse.stop();
            State.mediaProjectionInUse = null;
        }
        State.setMediaProjection(null);
        if (ListenOpenglAndPostFrame.instance != null) {
            ListenOpenglAndPostFrame.instance.release();
        }
        // 停止 MediaProjectionService
        context.stopService(new Intent(context, MediaProjectionService.class));

        // 停止 FloatingButtonService
        context.stopService(new Intent(context, FloatingButtonService.class));

        // 停止 TouchpadAccessibilityService
        Intent touchpadIntent = new Intent(context, TouchpadAccessibilityService.class);
        touchpadIntent.setAction(TouchpadAccessibilityService.class.getName());
        context.stopService(touchpadIntent);

        // 原有的清理代码
        if (MirrorActivity.getInstance() != null) {
            MirrorActivity.getInstance().finish();
        }
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
        State.displaylinkState.stopVirtualDisplay();
        State.displaylinkState.destroy();
        State.currentActivity.get().finish();
        
        // 退出应用进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
