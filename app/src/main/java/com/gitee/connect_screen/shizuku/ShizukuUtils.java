package com.gitee.connect_screen.shizuku;

import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

public class ShizukuUtils {
    public static boolean hasPermission() {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean hasShizukuStarted() {
        try {
            Shizuku.checkSelfPermission();
            return true;
        } catch(Exception e) {
            return false;
        }
    }
}
