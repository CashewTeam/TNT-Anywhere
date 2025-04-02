package com.connect_screen.mirror.job;

import android.util.Log;

public class SunshineKeyboard {
    private static String TAG = "SunshineKeyboard";
    public static void handleKeyboardEvent(int modcode, boolean release, int flags) {
        Log.d(TAG, "handleKeyboardEvent: " + modcode);
    }
}
