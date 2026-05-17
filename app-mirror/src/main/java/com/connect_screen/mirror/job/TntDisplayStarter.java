package com.connect_screen.mirror.job;

import android.content.Context;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;

public final class TntDisplayStarter {
    private TntDisplayStarter() {
    }

    public static boolean isOverlayBackend() {
        return Pref.getUseTntOverlayBackend();
    }

    public static boolean isActiveForCurrentBackend() {
        return isOverlayBackend()
                ? TntOverlayHelper.isOverlayOwnedByApp()
                : TntDebugVirtualDisplayHelper.isActive();
    }

    public static boolean toggleFromPreferences() {
        if (isOverlayBackend()) {
            if (TntOverlayHelper.isOverlayOwnedByApp()) {
                boolean cleared = TntOverlayHelper.clearOverlayDisplay();
                State.log("[TNTStart] manual toggle backend=overlay action=stop result=" + cleared);
                State.refreshMainActivity();
                return cleared;
            }
            boolean started = ensureFromPreferences();
            State.log("[TNTStart] manual toggle backend=overlay action=start result=" + started);
            return started;
        }

        if (TntDebugVirtualDisplayHelper.isActive()) {
            boolean cleared = TntDebugVirtualDisplayHelper.clearVirtualDisplay();
            State.log("[TNTStart] manual toggle backend=native action=stop result=" + cleared);
            State.refreshMainActivity();
            return cleared;
        }
        boolean started = ensureFromPreferences();
        State.log("[TNTStart] manual toggle backend=native action=start result=" + started);
        return started;
    }

    public static boolean ensureFromPreferences() {
        return ensureForClient(
                Pref.getTntOverlayWidth(),
                Pref.getTntOverlayHeight(),
                Pref.getTntOverlayDpi());
    }

    public static boolean ensureForClient(int width, int height, int density) {
        if (isOverlayBackend()) {
            clearNativeResidual();
            State.log("[TNTStart] ensure backend=overlay target=" + width + "x" + height + "/" + density
                    + " overlayConfig=" + TntOverlayHelper.hasOverlayDisplayConfig()
                    + " overlayMatches=" + TntOverlayHelper.isOverlayActiveWithConfig(width, height, density));
            return TntOverlayHelper.ensureHeadlessOverlayDisplay(width, height, density);
        }

        clearOverlayResidualIfOwned();
        State.log("[TNTStart] ensure backend=native target=" + width + "x" + height + "/" + density
                + " nativeActive=" + TntDebugVirtualDisplayHelper.isActive()
                + " nativeMatches=" + TntDebugVirtualDisplayHelper.isActiveWithConfig(width, height, density));
        return TntDebugVirtualDisplayHelper.ensureVirtualDisplay(width, height, density);
    }

    public static boolean clearCurrentBackendAndResidual() {
        boolean success;
        if (isOverlayBackend()) {
            success = TntOverlayHelper.clearOverlayDisplay();
            clearNativeResidual();
        } else {
            success = TntDebugVirtualDisplayHelper.clearVirtualDisplay();
            clearOverlayResidualIfOwned();
        }
        State.refreshMainActivity();
        return success;
    }

    public static void clearAllOwnedDisplays() {
        TntDebugVirtualDisplayHelper.clearVirtualDisplay();
        TntOverlayHelper.clearOverlayDisplayIfOwned();
        State.refreshMainActivity();
    }

    public static boolean isReadyForClient(Context context, int width, int height, int density) {
        boolean externalDisplayPresent = TntDisplaySelector.hasExternalDisplay(context);
        if (!externalDisplayPresent) {
            return false;
        }

        if (isOverlayBackend()) {
            return TntOverlayHelper.isOverlayActiveWithConfig(width, height, density);
        }

        if (TntDisplaySelector.hasPhysicalExternalDisplay(context)) {
            return true;
        }
        if (!TntDebugVirtualDisplayHelper.isActive()
                && !TntDebugVirtualDisplayHelper.isBaseDisplayPresent(context)) {
            return false;
        }
        return TntDisplaySelector.hasSelectableExternalDisplay(context);
    }

    private static void clearNativeResidual() {
        if (TntDebugVirtualDisplayHelper.isActive()) {
            State.log("[TNTStart] clear residual native virtual display before overlay backend");
            TntDebugVirtualDisplayHelper.clearVirtualDisplay();
        }
    }

    private static void clearOverlayResidualIfOwned() {
        if (TntOverlayHelper.hasOverlayDisplayConfig()) {
            State.log("[TNTStart] clear residual overlay display before native backend");
            TntOverlayHelper.clearOverlayDisplay();
            return;
        }
        TntOverlayHelper.clearOverlayDisplayIfOwned();
    }
}
