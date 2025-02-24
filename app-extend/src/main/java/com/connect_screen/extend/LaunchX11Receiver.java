package com.connect_screen.extend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.connect_screen.extend.job.TermuxDisablePhantomProcess;
import com.connect_screen.extend.shizuku.ServiceUtils;

public class LaunchX11Receiver extends BroadcastReceiver {
    private static final String PREF_NAME = "x11_launch_prefs";
    private static final String KEY_LAST_DISPLAY_NAME = "last_display_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.connect_screen.extend.LAUNCH_X11".equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String targetDisplayName = prefs.getString(KEY_LAST_DISPLAY_NAME, "");
            int displayId = getDisplayIdByName(context, targetDisplayName);
            ServiceUtils.launchActivity(context, com.termux.x11.MainActivity.class, displayId);
        }
    }

    public static void launchX11AtDisplay(Context context, Display display) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_DISPLAY_NAME, display.getName())
                .apply();
        
        int displayId = display.getDisplayId();
        ServiceUtils.launchActivity(context, com.termux.x11.MainActivity.class, displayId);
        State.startNewJob(new TermuxDisablePhantomProcess(displayId));
    }

    private int getDisplayIdByName(Context context, String displayName) {
        DisplayManager displayManager =
            (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        
        Display[] displays = displayManager.getDisplays();
        for (Display display : displays) {
            if (display.getName().equals(displayName)) {
                return display.getDisplayId();
            }
        }
        // 如果找不到指定的显示器，返回默认显示器ID
        return Display.DEFAULT_DISPLAY;
    }
}
