package com.easycast.source.job;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Build;
import android.view.Display;
import android.util.DisplayMetrics;

import com.easycast.source.BuildConfig;
import com.easycast.source.Pref;
import com.easycast.source.State;

import java.lang.reflect.Method;

public final class TntDebugVirtualDisplayHelper {
    private static final Object LOCK = new Object();
    public static final String DISPLAY_NAME = "tntanywhere.base.display";
    private static final String VIRTUAL_DISPLAY_PACKAGE_PROPERTY = "persist.sys.virtual_display_pkg";
    private static final long REAL_TNT_DISPLAY_TIMEOUT_MS = 2500;
    private static final long REAL_TNT_DISPLAY_POLL_MS = 100;

    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;
    private static String currentConfig;

    private TntDebugVirtualDisplayHelper() {
    }

    public static boolean ensureVirtualDisplayFromPreferences() {
        return ensureVirtualDisplay(
                Pref.getTntOverlayWidth(),
                Pref.getTntOverlayHeight(),
                Pref.getTntOverlayDpi());
    }

    public static boolean ensureVirtualDisplay(int width, int height, int density) {
        Context context = State.getContext();
        if (context == null) {
            State.showErrorStatus("Cannot create TNT debug virtual display without an active context");
            return false;
        }

        int safeWidth = normalizeWidth(width);
        int safeHeight = normalizeHeight(height);
        int safeDensity = normalizeDensity(density);
        String nextConfig = buildConfig(safeWidth, safeHeight, safeDensity);

        synchronized (LOCK) {
            if (virtualDisplay != null && nextConfig.equals(currentConfig)) {
                State.log("[TNTDebugVD] virtual display already active: " + nextConfig);
                State.refreshMainActivity();
                return true;
            }
            releaseLocked();
        }

        try {
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                State.showErrorStatus("Cannot access DisplayManager");
                return false;
            }
            dumpDisplays(displayManager, "before-create");

            boolean useAndroid10TntFix = isAndroid10TntFixEnabled();
            if (useAndroid10TntFix
                    && isBaseDisplayPresent(context)
                    && TntDisplaySelector.hasSelectableExternalDisplay(context)) {
                State.log("[TNTDebugVD] real TNT desktop display already exists; reuse it");
                State.refreshMainActivity();
                return true;
            }

            if (useAndroid10TntFix && !ensureVirtualDisplayOwnerProperty()) {
                return false;
            }

            VirtualDisplay nextDisplay = null;
            ImageReader nextReader = null;
            int[] flagCandidates = useAndroid10TntFix
                    ? new int[]{
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            }
                    : new int[]{
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            };
            Throwable lastError = null;
            for (int flags : flagCandidates) {
                ImageReader candidateReader = ImageReader.newInstance(
                        safeWidth,
                        safeHeight,
                        PixelFormat.RGBA_8888,
                        2);
                try {
                    VirtualDisplay candidateDisplay = displayManager.createVirtualDisplay(
                            DISPLAY_NAME,
                            safeWidth,
                            safeHeight,
                            safeDensity,
                            candidateReader.getSurface(),
                            flags);
                    if (candidateDisplay != null && candidateDisplay.getDisplay() != null) {
                        nextDisplay = candidateDisplay;
                        nextReader = candidateReader;
                        State.log("[TNTDebugVD] createVirtualDisplay succeeded with flags=" + flags);
                        break;
                    }
                    candidateReader.close();
                    State.log("[TNTDebugVD] createVirtualDisplay returned null with flags=" + flags);
                } catch (Throwable e) {
                    candidateReader.close();
                    lastError = e;
                    State.log("[TNTDebugVD] createVirtualDisplay attempt failed with flags="
                            + flags + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
                }
            }
            if (nextDisplay == null || nextReader == null) {
                if (lastError != null) {
                    State.log("[TNTDebugVD] no virtual display profile succeeded");
                }
                return false;
            }

            Display display = nextDisplay.getDisplay();
            synchronized (LOCK) {
                virtualDisplay = nextDisplay;
                imageReader = nextReader;
                currentConfig = nextConfig;
            }
            State.log("[TNTDebugVD] created displayId=" + display.getDisplayId()
                    + " name=" + (display == null ? DISPLAY_NAME : display.getName())
                    + " config=" + nextConfig
                    + " sdk=" + Build.VERSION.SDK_INT
                    + (useAndroid10TntFix ? " owner=" + BuildConfig.APPLICATION_ID : ""));
            dumpDisplays(displayManager, "after-create");
            if (useAndroid10TntFix && !waitForRealTntDisplay(context)) {
                State.log("[TNTDebugVD] base display was created but no real TNT desktop display appeared; release base display");
                synchronized (LOCK) {
                    releaseLocked();
                }
                dumpDisplays(displayManager, "after-release-no-real-tnt");
                State.showErrorStatus("Smartisan did not create the real TNT desktop display. Reboot the phone once, then start TNT again.");
                State.refreshMainActivity();
                return false;
            }
            State.refreshMainActivity();
            return true;
        } catch (Throwable e) {
            State.log("[TNTDebugVD] createVirtualDisplay failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }
    }

    public static boolean isActive() {
        synchronized (LOCK) {
            return virtualDisplay != null;
        }
    }

    public static boolean isActiveWithConfig(int width, int height, int density) {
        String targetConfig = buildConfig(
                normalizeWidth(width),
                normalizeHeight(height),
                normalizeDensity(density));
        synchronized (LOCK) {
            return virtualDisplay != null && targetConfig.equals(currentConfig);
        }
    }

    public static boolean isBaseDisplayPresent(Context context) {
        if (context == null) {
            return false;
        }
        try {
            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return false;
            }
            for (Display display : displayManager.getDisplays()) {
                if (display != null
                        && display.getDisplayId() < 100000
                        && DISPLAY_NAME.equals(display.getName())) {
                    return true;
                }
            }
        } catch (Throwable e) {
            State.log("[TNTDebugVD] query base display failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        return false;
    }

    public static boolean clearVirtualDisplay() {
        Context context = State.getContext();
        DisplayManager displayManager = context == null
                ? null
                : (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            dumpDisplays(displayManager, "before-release");
        }
        synchronized (LOCK) {
            if (virtualDisplay == null && imageReader == null) {
                State.refreshMainActivity();
                return true;
            }
            releaseLocked();
        }
        State.log("[TNTDebugVD] released debug virtual display");
        if (displayManager != null) {
            dumpDisplays(displayManager, "after-release");
        }
        State.refreshMainActivity();
        return true;
    }

    private static void releaseLocked() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Throwable e) {
                State.log("[TNTDebugVD] virtual display release failed: " + e.getMessage());
            }
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Throwable e) {
                State.log("[TNTDebugVD] image reader close failed: " + e.getMessage());
            }
            imageReader = null;
        }
        currentConfig = null;
    }

    private static boolean ensureVirtualDisplayOwnerProperty() {
        if (!State.isUserServiceAlive()) {
            State.showErrorStatus("TNT mode needs Shizuku user service before preparing the Smartisan display whitelist.");
            State.log("[TNTDebugVD] cannot prepare " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY
                    + " because Shizuku user service is not alive");
            return false;
        }

        String expectedPackageName = BuildConfig.APPLICATION_ID;
        String currentPackageName = readVirtualDisplayPackageProperty();
        if (expectedPackageName.equals(currentPackageName)) {
            State.log("[TNTDebugVD] " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY
                    + " already matches " + expectedPackageName);
            return true;
        }

        try {
            String result = State.userService.executeShellCommand(
                    "setprop " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY + " " + expectedPackageName
                            + " && getprop " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY);
            String updatedPackageName = firstNonEmptyLine(result);
            State.log("[TNTDebugVD] set " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY
                    + " from " + currentPackageName + " to " + updatedPackageName
                    + " raw=" + sanitizeShellOutput(result));
            if (expectedPackageName.equals(updatedPackageName)) {
                State.showErrorStatus("TNT display whitelist updated. Reboot the phone once, then start TNT again.");
            } else {
                State.showErrorStatus("Failed to update Smartisan TNT display whitelist.");
            }
        } catch (Throwable e) {
            State.log("[TNTDebugVD] set " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY + " failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
            State.showErrorStatus("Failed to update Smartisan TNT display whitelist.");
        }
        return false;
    }

    private static String readVirtualDisplayPackageProperty() {
        try {
            return firstNonEmptyLine(State.userService.executeShellCommand(
                    "getprop " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY));
        } catch (Throwable e) {
            State.log("[TNTDebugVD] get " + VIRTUAL_DISPLAY_PACKAGE_PROPERTY + " failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
            return "";
        }
    }

    private static String firstNonEmptyLine(String value) {
        if (value == null) {
            return "";
        }
        String[] lines = value.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("__EXIT_CODE=")) {
                continue;
            }
            return trimmed;
        }
        return "";
    }

    private static String sanitizeShellOutput(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', '|').replace('\r', '|');
    }

    private static boolean waitForRealTntDisplay(Context context) {
        long deadline = System.currentTimeMillis() + REAL_TNT_DISPLAY_TIMEOUT_MS;
        while (System.currentTimeMillis() <= deadline) {
            if (TntDisplaySelector.hasSelectableExternalDisplay(context)) {
                State.log("[TNTDebugVD] real TNT desktop display is selectable");
                return true;
            }
            try {
                Thread.sleep(REAL_TNT_DISPLAY_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        TntDisplaySelector.logDisplays(context, "real-tnt-timeout");
        return false;
    }

    private static boolean isAndroid10TntFixEnabled() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int normalizeWidth(int width) {
        return clamp(width > 0 ? width : 1920, 100, 8192);
    }

    private static int normalizeHeight(int height) {
        return clamp(height > 0 ? height : 1080, 100, 8192);
    }

    private static int normalizeDensity(int density) {
        return clamp(density > 0 ? density : 216, 72, 640);
    }

    private static String buildConfig(int width, int height, int density) {
        return width + "x" + height + "/" + density;
    }

    private static void dumpDisplays(DisplayManager displayManager, String reason) {
        try {
            Display[] displays = displayManager.getDisplays();
            StringBuilder builder = new StringBuilder("[TNTDebugVD] display snapshot ")
                    .append(reason)
                    .append(" count=")
                    .append(displays == null ? 0 : displays.length);
            if (displays != null) {
                for (Display display : displays) {
                    if (display == null) {
                        continue;
                    }
                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getRealMetrics(metrics);
                    builder.append(" {id=").append(display.getDisplayId())
                            .append(", name=").append(display.getName())
                            .append(", type=").append(getDisplayType(display))
                            .append(", flags=").append(display.getFlags())
                            .append(", owner=").append(getOwnerPackageName(display))
                            .append(", size=").append(metrics.widthPixels).append("x").append(metrics.heightPixels)
                            .append("}");
                }
            }
            State.log(builder.toString());
        } catch (Throwable e) {
            State.log("[TNTDebugVD] display snapshot failed for " + reason + ": "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static String getOwnerPackageName(Display display) {
        try {
            Method method = Display.class.getMethod("getOwnerPackageName");
            Object value = method.invoke(display);
            return value instanceof String ? (String) value : "null";
        } catch (Throwable e) {
            return "unavailable";
        }
    }

    private static String getDisplayType(Display display) {
        try {
            Method method = Display.class.getMethod("getType");
            Object value = method.invoke(display);
            return String.valueOf(value);
        } catch (Throwable e) {
            return "unavailable";
        }
    }
}
