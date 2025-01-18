package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;

public class DisplaylinkPref {
    public static int monitorWidth;
    public static int monitorHeight;
    public static int refreshRate;
    public static int dpi;
    public static boolean rotatesWithContent = true;
    public static boolean skipMediaProjectionPermission = false;
    public static boolean autoOpenLastApp = false;

    public static void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("displaylink_settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putInt("monitor_width", monitorWidth);
        editor.putInt("monitor_height", monitorHeight);
        editor.putInt("refresh_rate", refreshRate);
        editor.putInt("dpi", dpi);
        editor.putBoolean("rotates_with_content", rotatesWithContent);
        editor.putBoolean("skip_media_projection_permission", skipMediaProjectionPermission);
        editor.putBoolean("auto_open_last_app", autoOpenLastApp);

        editor.apply();
    }
    public static void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("displaylink_settings", Context.MODE_PRIVATE);
        
        String modeName = prefs.getString("projection_mode", ProjectionMode.MIRROR.name());

        monitorWidth = prefs.getInt("monitor_width", 1920);
        monitorHeight = prefs.getInt("monitor_height", 1080);
        refreshRate = prefs.getInt("refresh_rate", 60);
        dpi = prefs.getInt("dpi", 160);
        rotatesWithContent = prefs.getBoolean("rotates_with_content", true);
        skipMediaProjectionPermission = prefs.getBoolean("skip_media_projection_permission", false);
        autoOpenLastApp = prefs.getBoolean("auto_open_last_app", false);
    }
}
