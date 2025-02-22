package com.gitee.connect_screen.job;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Display;

import com.gitee.connect_screen.FloatingButtonService;
import com.gitee.connect_screen.BridgePref;
import com.gitee.connect_screen.MirrorActivity;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.List;

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
