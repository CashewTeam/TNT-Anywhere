package com.easycast.source.job;

import android.content.Context;
import android.provider.Settings;

import com.easycast.source.Pref;
import com.easycast.source.State;
import com.topjohnwu.superuser.Shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class TntOverlayHelper {
    private static final String OVERLAY_PROP = "persist.easycast.show_overlay_display";
    private static final String OVERLAY_SETTING_KEY = "overlay_display_devices";
    private static final Object LOCK = new Object();

    private static boolean overlayOwnedByApp;
    private static String currentOverlayValue;

    private TntOverlayHelper() {
    }

    public static boolean hasRootAccess() {
        try {
            return Shell.getShell().isRoot();
        } catch (Throwable e) {
            State.log("[TNTOverlay] root check failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean ensureHeadlessOverlayDisplayFromPreferences() {
        return ensureHeadlessOverlayDisplay(
                Pref.getTntOverlayWidth(),
                Pref.getTntOverlayHeight(),
                Pref.getTntOverlayDpi());
    }

    public static boolean ensureHeadlessOverlayDisplay(int width, int height, int density) {
        if (!hasRootAccess()) {
            State.showErrorStatus("TNT headless overlay mode requires root access");
            return false;
        }

        String overlayValue = buildOverlayValue(width, height, density);
        synchronized (LOCK) {
            if (overlayOwnedByApp && overlayValue.equals(currentOverlayValue)) {
                State.log("[TNTOverlay] overlay display already active: " + overlayValue);
                return true;
            }
        }

        boolean success;
        try {
            success = Shell.getShell().newJob()
                    .add("setprop " + OVERLAY_PROP + " 1")
                    .add("if command -v resetprop >/dev/null 2>&1; then resetprop -n "
                            + OVERLAY_PROP + " 1; fi")
                    .add("settings put global " + OVERLAY_SETTING_KEY + " " + shellQuote(overlayValue))
                    .exec()
                    .isSuccess();
        } catch (Throwable e) {
            State.log("[TNTOverlay] enable overlay display failed: " + e.getMessage());
            return false;
        }

        if (!success) {
            State.log("[TNTOverlay] root commands failed while enabling overlay display");
            return false;
        }

        synchronized (LOCK) {
            overlayOwnedByApp = true;
            currentOverlayValue = overlayValue;
        }
        State.log("[TNTOverlay] enabled headless overlay display: " + overlayValue);
        return true;
    }

    public static boolean isOverlayOwnedByApp() {
        synchronized (LOCK) {
            if (overlayOwnedByApp) {
                return true;
            }
        }
        return hasOverlayDisplayConfig();
    }

    public static boolean isOverlayActiveWithConfig(int width, int height, int density) {
        String expected = buildOverlayValue(width, height, density);
        synchronized (LOCK) {
            if (overlayOwnedByApp && expected.equals(currentOverlayValue)) {
                return true;
            }
        }
        String configured = getOverlayDisplayConfig();
        return expected.equals(configured);
    }

    public static boolean hasOverlayDisplayConfig() {
        String configured = getOverlayDisplayConfig();
        return configured != null && !configured.trim().isEmpty();
    }

    public static boolean clearOverlayDisplay() {
        if (!hasRootAccess()) {
            State.log("[TNTOverlay] cannot clear overlay display because root is unavailable");
            return false;
        }

        synchronized (LOCK) {
            overlayOwnedByApp = false;
            currentOverlayValue = null;
        }

        try {
            boolean success = Shell.getShell().newJob()
                    .add("settings put global " + OVERLAY_SETTING_KEY + " \"\"")
                    .exec()
                    .isSuccess();
            State.log(success
                    ? "[TNTOverlay] cleared overlay display setting"
                    : "[TNTOverlay] failed to clear overlay display setting");
            return success;
        } catch (Throwable e) {
            State.log("[TNTOverlay] clear overlay display failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean clearOverlayDisplayIfOwned() {
        String ownedValue;
        synchronized (LOCK) {
            if (!overlayOwnedByApp) {
                return true;
            }
            ownedValue = currentOverlayValue;
        }
        String configured = getOverlayDisplayConfig();
        if (ownedValue != null && configured != null && !ownedValue.equals(configured)) {
            synchronized (LOCK) {
                overlayOwnedByApp = false;
                currentOverlayValue = null;
            }
            State.log("[TNTOverlay] skip clearing overlay display because setting changed outside app: "
                    + configured);
            return true;
        }
        return clearOverlayDisplay();
    }

    public static boolean isOverlayDebugPropertyEnabled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"getprop", OVERLAY_PROP});
            String value;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                value = reader.readLine();
            }
            process.waitFor();
            return "1".equals(value == null ? "" : value.trim());
        } catch (Throwable e) {
            State.log("[TNTOverlay] query overlay debug property failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean setOverlayDebugPropertyEnabled(boolean enabled) {
        if (!hasRootAccess()) {
            State.log("[TNTOverlay] cannot change overlay debug property because root is unavailable");
            return false;
        }
        String value = enabled ? "1" : "0";
        try {
            boolean success = Shell.getShell().newJob()
                    .add("setprop " + OVERLAY_PROP + " " + value)
                    .add("if command -v resetprop >/dev/null 2>&1; then resetprop -n "
                            + OVERLAY_PROP + " " + value + "; fi")
                    .exec()
                    .isSuccess();
            State.log(success
                    ? "[TNTOverlay] set " + OVERLAY_PROP + "=" + value
                    : "[TNTOverlay] failed to set " + OVERLAY_PROP + "=" + value);
            return success;
        } catch (Throwable e) {
            State.log("[TNTOverlay] set overlay debug property failed: " + e.getMessage());
            return false;
        }
    }

    public static void restoreOverlayDisplayIfOwned() {
        clearOverlayDisplayIfOwned();
    }

    public static String getConfiguredOverlayValue() {
        return buildOverlayValue(
                Pref.getTntOverlayWidth(),
                Pref.getTntOverlayHeight(),
                Pref.getTntOverlayDpi());
    }

    private static String getOverlayDisplayConfig() {
        try {
            Context context = State.getContext();
            if (context == null) {
                return "";
            }
            String value = Settings.Global.getString(context.getContentResolver(), OVERLAY_SETTING_KEY);
            return value == null ? "" : value.trim();
        } catch (Throwable e) {
            State.log("[TNTOverlay] query overlay setting failed: " + e.getMessage());
            return "";
        }
    }

    private static String buildOverlayValue(int width, int height, int density) {
        int safeWidth = clamp(width > 0 ? width : 1920, 100, 8192);
        int safeHeight = clamp(height > 0 ? height : 1080, 100, 8192);
        int safeDensity = clamp(density > 0 ? density : 216, 72, 640);
        return safeWidth + "x" + safeHeight + "/" + safeDensity;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
