package com.easycast.source;

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
    public static final String KEY_DISABLE_USB_AUDIO = "disable_usb_audio";
    public static final String KEY_USE_TOUCHSCREEN = "use_touchscreen";
    public static final String KEY_AUTO_MATCH_ASPECT_RATIO = "auto_match_aspect_ratio";
    public static final String KEY_SHOW_FLOATING_IN_MIRROR_MODE = "floating_back_button_in_mirror";
    public static final String KEY_DISPLAYLINK_WIDTH = "displaylink_width";
    public static final String KEY_DISPLAYLINK_HEIGHT = "displaylink_height";
    public static final String KEY_DISPLAYLINK_REFRESH_RATE = "displaylink_refresh_rate";
    public static final String KEY_SELECTED_CLIENT = "selected_client";
    public static final String KEY_AUTO_CONNECT_CLIENT = "auto_connect_client";
    public static final String KEY_DISABLE_ACCESSIBILITY = "disable_accessibility";
    public static final String KEY_USE_BLACK_IMAGE = "use_black_image";
    public static final String KEY_PREVENT_AUTO_LOCK = "prevent_auto_lock";
    public static final String KEY_SKIP_EXTERNAL_ACTIVITY = "skip_external_activity";
    public static final String KEY_USE_ANDROID_CURSOR_OVERLAY = "use_android_cursor_overlay";
    public static final String KEY_MAP_MOUSE_TO_TOUCH = "map_mouse_to_touch";
    public static final String KEY_TNT_OVERLAY_WIDTH = "tnt_overlay_width";
    public static final String KEY_TNT_OVERLAY_HEIGHT = "tnt_overlay_height";
    public static final String KEY_TNT_OVERLAY_DPI = "tnt_overlay_dpi";
    public static final String KEY_AUTO_CLOSE_TNT_ON_CLIENT_DISCONNECT = "auto_close_tnt_on_client_disconnect";
    public static final String KEY_ADAPT_TNT_RESOLUTION_TO_CLIENT = "adapt_tnt_resolution_to_client";
    public static final String KEY_SMARTISAN_CPUSET_BOOST = "smartisan_cpuset_boost";
    public static final String KEY_USE_TNT_OVERLAY_BACKEND = "use_tnt_overlay_backend";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_ENCODER_CODEC = "encoder_codec";
    public static final String KEY_ENCODER_BITRATE_PERCENT = "encoder_bitrate_percent";
    public static final String KEY_ENCODER_BITRATE_MODE = "encoder_bitrate_mode";
    public static final String KEY_ENCODER_COMPLEXITY = "encoder_complexity";
    public static final String KEY_ENCODER_I_FRAME_INTERVAL = "encoder_i_frame_interval";
    public static final String KEY_ENCODER_MAX_FPS = "encoder_max_fps";
    public static final String KEY_ENCODER_LOW_LATENCY = "encoder_low_latency";
    public static final String KEY_ENCODER_DISABLE_B_FRAMES = "encoder_disable_b_frames";
    public static final String KEY_ENCODER_REALTIME_PRIORITY = "encoder_realtime_priority";
    public static final String KEY_ENCODER_DYNAMIC_FRAME_RATE = "encoder_dynamic_frame_rate";
    public static final String KEY_STREAM_FEC_PERCENT = "stream_fec_percent";
    public static final String KEY_INITIAL_SETUP_COMPLETE = "initial_setup_complete";
    public static final int ENCODER_CODEC_H264 = 0;
    public static final int ENCODER_CODEC_H265 = 1;
    public static boolean doNotAutoStartMoonlight;

    public static boolean getAutoRotate() {
        return getBoolean(KEY_AUTO_ROTATE, true);
    }

    public static boolean  getAutoScale() {
        return getBoolean(KEY_AUTO_SCALE, true);
    }

    public static boolean getSingleAppMode() {
        return getBoolean(KEY_SINGLE_APP_MODE, false);
    }

    public static boolean getAutoHideFloatingBackButton() {
        return getBoolean(KEY_AUTO_HIDE_FLOATING_BACK_BUTTON, false);
    }

    public static boolean getAutoScreenOff() {
        return getBoolean(KEY_AUTO_SCREEN_OFF, true);
    }

    public static boolean getAutoBindInput() {
        return getBoolean(KEY_AUTO_BIND_INPUT, true);
    }

    public static boolean getDisableUsbAudio() {
        return getBoolean(KEY_DISABLE_USB_AUDIO, false);
    }

    public static boolean getAutoMatchAspectRatio() {
        return getBoolean(KEY_AUTO_MATCH_ASPECT_RATIO, false);
    }

    public static boolean getUseTouchscreen() {
        return getBoolean(KEY_USE_TOUCHSCREEN, true);
    }

    public static boolean getShowFloatingInMirrorMode() {
        return getBoolean(KEY_SHOW_FLOATING_IN_MIRROR_MODE, false);
    }

    public static boolean getAutoConnectClient() {
        return getBoolean(KEY_AUTO_CONNECT_CLIENT, false);
    }

    public static int getSingleAppDpi() {
        return getInt(KEY_SINGLE_APP_DPI, 160);
    }

    public static int getDisplaylinkWidth() {
        return 1920;
    }

    public static int getDisplaylinkHeight() {
        return 1080;
    }

    public static int getDisplaylinkRefreshRate() {
        return 60;
    }

    public static String getSelectedAppPackage() {
        return getString(KEY_SELECTED_APP_PACKAGE, "");
    }

    public static String getSelectedClient() {
        return getString(KEY_SELECTED_CLIENT, "");
    }

    public static boolean getDisableAccessibility() {
        return getBoolean(KEY_DISABLE_ACCESSIBILITY, true);
    }

    public static boolean getUseBlackImage() {
        return getBoolean(KEY_USE_BLACK_IMAGE, true);
    }

    public static boolean getPreventAutoLock() {
        return getBoolean(KEY_PREVENT_AUTO_LOCK, false);
    }

    public static boolean getSkipExternalActivity() {
        return getBoolean(KEY_SKIP_EXTERNAL_ACTIVITY, true);
    }

    public static boolean getUseAndroidCursorOverlay() {
        return getBoolean(KEY_USE_ANDROID_CURSOR_OVERLAY, false);
    }

    public static boolean getMapMouseToTouch() {
        return getBoolean(KEY_MAP_MOUSE_TO_TOUCH, false);
    }

    public static int getTntOverlayWidth() {
        return getInt(KEY_TNT_OVERLAY_WIDTH, 1920);
    }

    public static int getTntOverlayHeight() {
        return getInt(KEY_TNT_OVERLAY_HEIGHT, 1080);
    }

    public static int getTntOverlayDpi() {
        return getInt(KEY_TNT_OVERLAY_DPI, 216);
    }

    public static boolean getAutoCloseTntOnClientDisconnect() {
        return getBoolean(KEY_AUTO_CLOSE_TNT_ON_CLIENT_DISCONNECT, true);
    }

    public static boolean getAdaptTntResolutionToClient() {
        return getBoolean(KEY_ADAPT_TNT_RESOLUTION_TO_CLIENT, true);
    }

    public static boolean getSmartisanCpusetBoost() {
        return getBoolean(KEY_SMARTISAN_CPUSET_BOOST, true);
    }

    public static boolean getUseTntOverlayBackend() {
        return getBoolean(KEY_USE_TNT_OVERLAY_BACKEND, false);
    }

    public static boolean getDarkMode() {
        return getBoolean(KEY_DARK_MODE, false);
    }

    public static int getEncoderCodec() {
        return getInt(KEY_ENCODER_CODEC, ENCODER_CODEC_H264);
    }

    public static int getEncoderBitratePercent() {
        return getInt(KEY_ENCODER_BITRATE_PERCENT, 100);
    }

    public static int getEncoderBitrateMode() {
        return getInt(KEY_ENCODER_BITRATE_MODE, 2);
    }

    public static int getEncoderComplexity() {
        return getInt(KEY_ENCODER_COMPLEXITY, 5);
    }

    public static int getEncoderIFrameInterval() {
        return getInt(KEY_ENCODER_I_FRAME_INTERVAL, 3);
    }

    public static int getEncoderMaxFps() {
        return getInt(KEY_ENCODER_MAX_FPS, 60);
    }

    public static boolean getEncoderLowLatency() {
        return getBoolean(KEY_ENCODER_LOW_LATENCY, true);
    }

    public static boolean getEncoderDisableBFrames() {
        return getBoolean(KEY_ENCODER_DISABLE_B_FRAMES, true);
    }

    public static boolean getEncoderRealtimePriority() {
        return getBoolean(KEY_ENCODER_REALTIME_PRIORITY, true);
    }

    public static boolean getEncoderDynamicFrameRate() {
        return getBoolean(KEY_ENCODER_DYNAMIC_FRAME_RATE, false);
    }

    public static int getStreamFecPercent() {
        return getInt(KEY_STREAM_FEC_PERCENT, 0);
    }

    public static boolean isInitialSetupComplete() {
        return getBoolean(KEY_INITIAL_SETUP_COMPLETE, false);
    }

    public static void setInitialSetupComplete(boolean complete) {
        SharedPreferences preferences = getPreferences();
        if (preferences != null) {
            preferences.edit().putBoolean(KEY_INITIAL_SETUP_COMPLETE, complete).apply();
        }
    }

    private static String getString(String key, String defaultValue) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return defaultValue;
        }
        return preferences.getString(key, defaultValue);
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
