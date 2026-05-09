package com.connect_screen.mirror.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;

public final class TntDisplaySelector {
    public boolean ensureSelected() {
        return selectForCurrentMode(State.getContext(), true);
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
        int displayId = findLargestDisplayId(displayManager);
        if (displayId <= Display.DEFAULT_DISPLAY) {
            if (showError) {
                State.showErrorStatus("TNT mode did not find an external display. Start TNT first, then connect Moonlight again.");
            }
            State.log("[DisplaySelect] TNT mode found no external display, selected=" + displayId);
            return false;
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
        logDisplays(displayManager);
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

    private static void logDisplays(DisplayManager displayManager) {
        try {
            Display[] displays = displayManager.getDisplays();
            StringBuilder builder = new StringBuilder("[DisplaySelect] displays count=")
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
                        .append("}");
            }
            State.log(builder.toString());
        } catch (Throwable e) {
            State.log("[DisplaySelect] display snapshot failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }
}
