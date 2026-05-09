package com.connect_screen.mirror.job;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Build;
import android.view.Display;
import android.util.DisplayMetrics;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;

import java.lang.reflect.Method;

public final class TntDebugVirtualDisplayHelper {
    private static final Object LOCK = new Object();
    public static final String DISPLAY_NAME = "tntanywhere.base.display";

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

            VirtualDisplay nextDisplay = null;
            ImageReader nextReader = null;
            int[] flagCandidates = new int[]{
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
                    + " name=" + display.getName()
                    + " config=" + nextConfig
                    + " sdk=" + Build.VERSION.SDK_INT);
            dumpDisplays(displayManager, "after-create");
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
                if (display != null && DISPLAY_NAME.equals(display.getName())) {
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
