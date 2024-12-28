package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.view.Display;

import com.gitee.connect_screen.FloatingButtonService;
import com.gitee.connect_screen.BridgePref;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

public class DisplayMonitor {
    private static boolean registered = false;
    public static void init(DisplayManager displayManager) {
        if (registered) {
            return;
        }
        registered = true;
        for (Display display : displayManager.getDisplays()) {
            handleNewDisplay(display);
        }
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
                if (State.floatingButtonService != null) {
                    State.floatingButtonService.onDisplayRemoved(displayId);
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // 显示器状态变化时的处理
                if (State.floatingButtonService != null) {
                    State.floatingButtonService.onDisplayChanged(displayId);
                }
            }
        }, null);
    }

    private static void handleNewDisplay(Display display) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return;
        }
        Context context = State.currentActivity.get();
        if (context == null) {
            return;
        }
        handleAutoOpenLastApp(context, display);
        handleFloatingButton(context, display);
    }

    private static void handleFloatingButton(Context context, Display display) {
        SharedPreferences appPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        if (appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false)) {
            if (FloatingButtonService.startFloating(context, display.getDisplayId(), true)) {
                FloatingButtonService.startFloating(context, display.getDisplayId(), false);
            } else {
                appPreferences.edit().putBoolean("FLOATING_BUTTON_" + display.getName(), false).apply();
            }
        }
    }

    private static void handleAutoOpenLastApp(Context context, Display display) {
        SharedPreferences appPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        boolean autoBridge = appPreferences.getBoolean("AUTO_BRIDGE_" + display.getName(), false);
        if (ShizukuUtils.hasPermission() && (autoBridge || display.getDisplayId() == State.bridgeDisplayId)) {
            BridgePref.load(context);
            new Handler().postDelayed(() -> {
                State.startNewJob(new ProjectViaBridge(display.getDisplayId(), new VirtualDisplayArgs("桥接屏幕", display.getWidth(), display.getHeight(), display.getWidth(), (int) display.getRefreshRate(), BridgePref.rotatesWithContent)));
            }, 500);
            return;
        }
        boolean autoOpen = appPreferences.getBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), false);
        if (!autoOpen) {
            return;
        }
        String lastPackageName = appPreferences.getString("LAST_PACKAGE_NAME", null);
        if (lastPackageName == null) {
            return;
        }
        State.log("尝试自动打开显示器 " + display.getName() + " 上投屏的应用 " + lastPackageName);
        new Handler().postDelayed(() -> {
            ServiceUtils.launchPackage(context, lastPackageName, display.getDisplayId());
        }, 500);
    }
}
