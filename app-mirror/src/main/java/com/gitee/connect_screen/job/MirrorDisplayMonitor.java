package com.gitee.connect_screen.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.gitee.connect_screen.State;

public class MirrorDisplayMonitor {
    private static boolean registered = false;
    public static void init(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            handleNewDisplay(display);
        }
        if (registered) {
            return;
        }
        registered = true;
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                State.log("新增显示器，displayId: " + displayId);
                Display display = displayManager.getDisplay(displayId);
                if (display != null) {
                    handleNewDisplay(display);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                State.log("移除显示器，displayId: " + displayId);
            }

            @Override
            public void onDisplayChanged(int displayId) {
            }
        }, null);
    }

    private static void handleNewDisplay(Display display) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return;
        }
        if (display.getDisplayId() == State.getDisplaylinkVirtualDisplayId()) {
            return;
        }
        if (display.getDisplayId() == State.getBridgeVirtualDisplayId()) {
            return;
        } 
        if (display.getDisplayId() == State.getMirrorVirtualDisplayId()) {
            return;
        }
        Context context = State.currentActivity.get();
        if (context == null) {
            return;
        }
        State.startNewJob(new ProjectViaMirror(display));
    }
}
