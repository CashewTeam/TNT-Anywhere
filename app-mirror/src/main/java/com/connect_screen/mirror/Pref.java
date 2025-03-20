package com.connect_screen.mirror;

import android.content.Context;
import android.content.SharedPreferences;

public class Pref {
    public static final String KEY_AUTO_ROTATE = "auto_rotate";
    public static final String KEY_AUTO_SCALE = "auto_scale";
    public static final String KEY_SINGLE_APP_MODE = "single_app_mode";
    public static final String KEY_SELECTED_APP_PACKAGE = "selected_app_package";
    public static final String KEY_SELECTED_APP_NAME = "selected_app_name";
    public static final String KEY_SINGLE_APP_DPI = "single_app_dpi";
    public static final String KEY_AUTO_HIDE_FLOATING_BACK_BUTTON = "floating_back_button";
    public static final String KEY_AUTO_SCREEN_OFF = "auto_screen_off";
    public static final String KEY_AUTO_BIND_INPUT = "auto_bind_input";
    public static final String KEY_AUTO_MOVE_IME = "auto_move_ime";
    public static final String KEY_DISABLE_USB_AUDIO = "disable_usb_audio";
    public static final String KEY_USE_TOUCHSCREEN = "use_touchscreen";
    public static final String KEY_AUTO_MATCH_ASPECT_RATIO = "auto_match_aspect_ratio";
    public static final String KEY_SHOW_FLOATING_IN_MIRROR_MODE = "floating_back_button_in_mirror";

    public static boolean getAutoRotate() {
        return getBoolean(Pref.KEY_AUTO_ROTATE, true);
    }

    public static boolean  getAutoScale() {
        return getBoolean(Pref.KEY_AUTO_SCALE, true);
    }

    public static boolean getSingleAppMode() {
        return getBoolean(Pref.KEY_SINGLE_APP_MODE, false);
    }

    public static int getSingleAppDpi() {
        return getInt(Pref.KEY_SINGLE_APP_DPI, 160);
    }

    private static int getInt(String key, int defaultValue) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return defaultValue;
        }
        return preferences.getInt(key, defaultValue);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return defaultValue;
        }
        return preferences.getBoolean(key, defaultValue);
    }

    public static SharedPreferences getPreferences() {
        Context context = State.getContext();
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE);

    }
}
