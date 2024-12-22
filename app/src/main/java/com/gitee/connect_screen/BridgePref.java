package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;

public class BridgePref {

    public static boolean rotatesWithContent;
    public static boolean skipMediaProjectionPermission;
    public static boolean autoBridge;

    public static void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("bridge_settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("rotates_with_content", rotatesWithContent);
        editor.putBoolean("skip_media_projection_permission", skipMediaProjectionPermission);
        editor.putBoolean("auto_bridge", autoBridge);

        editor.apply();
    }
    public static void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("bridge_settings", Context.MODE_PRIVATE);
        rotatesWithContent = prefs.getBoolean("rotates_with_content", true);
        skipMediaProjectionPermission = prefs.getBoolean("skip_media_projection_permission", false);
        autoBridge = prefs.getBoolean("auto_bridge", false);
    }
}
