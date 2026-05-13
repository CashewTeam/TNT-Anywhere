package com.connect_screen.mirror;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

public final class UiCompat {
    private UiCompat() {
    }

    public static void tintSwitch(Context context, SwitchCompat switchCompat) {
        if (context == null || switchCompat == null) {
            return;
        }
        int accent = ContextCompat.getColor(context, R.color.ui_accent);
        int disabled = 0xFFD0D4D8;
        int unchecked = 0xFF9EA4AA;
        int[][] states = new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        switchCompat.setThumbTintList(new ColorStateList(states, new int[]{disabled, accent, unchecked}));
        switchCompat.setTrackTintList(new ColorStateList(states, new int[]{0x223F454A, 0x66FF7A1A, 0x553F454A}));
        switchCompat.setTextColor(ContextCompat.getColor(context, R.color.ui_text_primary));
    }
}
