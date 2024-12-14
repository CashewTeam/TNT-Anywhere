package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;

public class DisplaylinkPref {
    public static ProjectionMode projectionMode;
    public static int monitorWidth;
    public static int monitorHeight;
    public static int sourceWidth;
    public static int sourceHeight;
    public static int refreshRate;
    public static boolean rotatesWithContent = true;
    public static void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("displaylink_settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("projection_mode", projectionMode.name());
        editor.putInt("monitor_width", monitorWidth);
        editor.putInt("monitor_height", monitorHeight);
        editor.putInt("source_width", sourceWidth);
        editor.putInt("source_height", sourceHeight);
        editor.putInt("refresh_rate", refreshRate);
        editor.putBoolean("rotates_with_content", rotatesWithContent);
        
        editor.apply();
    }
    public static void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("displaylink_settings", Context.MODE_PRIVATE);
        
        String modeName = prefs.getString("projection_mode", ProjectionMode.MIRROR.name());
        projectionMode = ProjectionMode.valueOf(modeName);
        
        monitorWidth = prefs.getInt("monitor_width", 1920);
        monitorHeight = prefs.getInt("monitor_height", 1080);
        sourceWidth = prefs.getInt("source_width", 1920);
        sourceHeight = prefs.getInt("source_height", 1080);
        refreshRate = prefs.getInt("refresh_rate", 60);
        rotatesWithContent = prefs.getBoolean("rotates_with_content", true);
    }
}
