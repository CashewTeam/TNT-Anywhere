package com.connect_screen.mirror.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import android.os.Build;

public final class TntDisplaySelector {
    private static final int TNT_PC_DISPLAY_ID_MIN = 100000;

    public boolean ensureSelected() {
        return selectForCurrentMode(State.getContext(), true);
    }

    public boolean ensureExistingExternalSelected() {
        Context context = State.getContext();
        if (context == null) {
            return false;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return false;
        }
        int displayId = findLargestPhysicalExternalDisplayId(displayManager);
        return displayId > Display.DEFAULT_DISPLAY
                && selectDisplay(context, displayId, false, "Existing TNT");
    }

    public static boolean selectForCurrentMode(Context context, boolean showError) {
        if (Pref.getSkipExternalActivity()) {
            return selectLargestDisplay(context, showError);
        }
        return selectDisplay(context, Display.DEFAULT_DISPLAY, showError, "Mirror");
    }

    public static boolean hasExternalDisplay(Context context) {
        if (context == null) {
            return false;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return false;
        }
        return findLargestDisplayId(displayManager) > Display.DEFAULT_DISPLAY;
    }

    public static boolean hasSelectableExternalDisplay(Context context) {
        if (context == null) {
            return false;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return false;
        }
        return findLargestSelectableDisplayId(displayManager) > Display.DEFAULT_DISPLAY;
    }

    public static boolean hasPhysicalExternalDisplay(Context context) {
        if (context == null) {
            return false;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return false;
        }
        return findLargestPhysicalExternalDisplayId(displayManager) > Display.DEFAULT_DISPLAY;
    }

    public static void logDisplays(Context context, String reason) {
        if (context == null) {
            State.log("[DisplaySelect] display snapshot skipped for " + reason + ": context is null");
            return;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            State.log("[DisplaySelect] display snapshot skipped for " + reason + ": DisplayManager is null");
            return;
        }
        logDisplays(displayManager, reason);
    }

    private static boolean selectLargestDisplay(Context context, boolean showError) {
        if (context == null) {
            if (showError) {
                State.showErrorStatus("Cannot select TNT display without an active context");
            }
            return false;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            if (showError) {
                State.showErrorStatus("Cannot access DisplayManager");
            }
            return false;
        }
        int largestDisplayId = findLargestDisplayId(displayManager);
        int displayId = findLargestSelectableDisplayId(displayManager);
        if (displayId <= Display.DEFAULT_DISPLAY) {
            // Suppress error toast when display 0 is the only option because base wrapper was filtered
            boolean isDisplay0OnlyFallback = displayId == Display.DEFAULT_DISPLAY && largestDisplayId > Display.DEFAULT_DISPLAY;
            if (showError && !isDisplay0OnlyFallback) {
                State.showErrorStatus("TNT mode did not find a selectable external display. Wait for TNT to start, then reconnect Moonlight.");
            }
            State.log("[DisplaySelect] TNT mode found no selectable external display, selected="
                    + displayId + " largest=" + largestDisplayId
                    + " basePresent=" + hasBaseDisplay(displayManager));
            logDisplays(displayManager, "TNT-no-selectable");
            return false;
        }
        if (largestDisplayId != displayId) {
            State.log("[DisplaySelect] skipped low-id base wrapper and selected displayId="
                    + displayId + " largest=" + largestDisplayId
                    + " tntPc=" + isTntPcDisplayId(displayId));
        }
        return selectDisplay(context, displayId, showError, "TNT");
    }

    private static boolean selectDisplay(Context context, int displayId, boolean showError, String mode) {
        if (context == null) {
            if (showError) {
                State.showErrorStatus("Cannot select display without an active context");
            }
            return false;
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            if (showError) {
                State.showErrorStatus("Cannot access DisplayManager");
            }
            return false;
        }
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            if (showError) {
                State.showErrorStatus(mode + " display " + displayId + " is not available");
            }
            return false;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        State.externalDisplayId = displayId;
        State.externalControlDisplayId = displayId;
        State.externalDisplayWidth = metrics.widthPixels;
        State.externalDisplayHeight = metrics.heightPixels;
        State.log("[DisplaySelect] " + mode + " mode selected displayId=" + displayId
                + " name=" + display.getName()
                + " size=" + metrics.widthPixels + "x" + metrics.heightPixels);
        logDisplays(displayManager, mode);
        return true;
    }

    private static int findLargestDisplayId(DisplayManager displayManager) {
        int largestDisplayId = -1;
        for (Display display : displayManager.getDisplays()) {
            if (display != null && display.getDisplayId() > largestDisplayId) {
                largestDisplayId = display.getDisplayId();
            }
        }
        return largestDisplayId;
    }

    private static int findLargestSelectableDisplayId(DisplayManager displayManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            int physicalDisplayId = findLargestPhysicalExternalDisplayId(displayManager);
            if (physicalDisplayId > Display.DEFAULT_DISPLAY) {
                return physicalDisplayId;
            }
            int baseDisplayId = findSmartisanBaseDisplayId(displayManager);
            if (baseDisplayId > Display.DEFAULT_DISPLAY) {
                return baseDisplayId;
            }
        }
        int largestTntPcDisplayId = -1;
        int largestDisplayId = -1;
        for (Display display : displayManager.getDisplays()) {
            if (display == null || isTntBaseWrapper(display)) {
                continue;
            }
            int displayId = display.getDisplayId();
            if (isTntPcDisplayId(displayId) && displayId > largestTntPcDisplayId) {
                largestTntPcDisplayId = displayId;
            }
            if (displayId > largestDisplayId) {
                largestDisplayId = displayId;
            }
        }
        return largestTntPcDisplayId > Display.DEFAULT_DISPLAY
                ? largestTntPcDisplayId
                : largestDisplayId;
    }

    private static int findSmartisanBaseDisplayId(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            if (display == null) {
                continue;
            }
            int displayId = display.getDisplayId();
            if (displayId > Display.DEFAULT_DISPLAY
                    && TntDebugVirtualDisplayHelper.DISPLAY_NAME.equals(display.getName())) {
                return displayId;
            }
        }
        return Display.DEFAULT_DISPLAY;
    }

    private static int findLargestPhysicalExternalDisplayId(DisplayManager displayManager) {
        int largestDisplayId = -1;
        for (Display display : displayManager.getDisplays()) {
            if (display == null || isTntBaseWrapper(display)) {
                continue;
            }
            int displayId = display.getDisplayId();
            if (displayId <= Display.DEFAULT_DISPLAY || isTntPcDisplayId(displayId)) {
                continue;
            }
            if (displayId > largestDisplayId) {
                largestDisplayId = displayId;
            }
        }
        return largestDisplayId;
    }

    private static boolean hasBaseDisplay(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            if (display != null && isTntBaseWrapper(display)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTntBaseWrapper(Display display) {
        return display != null
                && display.getDisplayId() < TNT_PC_DISPLAY_ID_MIN
                && TntDebugVirtualDisplayHelper.DISPLAY_NAME.equals(display.getName());
    }

    private static boolean isTntPcDisplayId(int displayId) {
        return displayId >= TNT_PC_DISPLAY_ID_MIN;
    }

    private static void logDisplays(DisplayManager displayManager, String reason) {
        try {
            Display[] displays = displayManager.getDisplays();
            StringBuilder builder = new StringBuilder("[DisplaySelect] displays ")
                    .append(reason)
                    .append(" count=")
                    .append(displays.length);
            for (Display display : displays) {
                if (display == null) {
                    continue;
                }
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                builder.append(" {id=").append(display.getDisplayId())
                        .append(", name=").append(display.getName())
                        .append(", size=").append(metrics.widthPixels).append("x").append(metrics.heightPixels)
                        .append(", flags=").append(display.getFlags())
                        .append(", baseWrapper=").append(isTntBaseWrapper(display))
                        .append(", tntPc=").append(isTntPcDisplayId(display.getDisplayId()))
                        .append("}");
            }
            State.log(builder.toString());
        } catch (Throwable e) {
            State.log("[DisplaySelect] display snapshot failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }
}
