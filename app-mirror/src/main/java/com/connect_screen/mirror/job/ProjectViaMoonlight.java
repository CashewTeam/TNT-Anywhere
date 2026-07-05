package com.connect_screen.mirror.job;

import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.view.Display;
import android.view.Surface;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public class ProjectViaMoonlight implements Job {
    public interface StartupCallback {
        void onStartupComplete(boolean success);
    }

    private final int width;
    private final int height;
    private final int frameRate;
    private final int packetDuration;
    private final Surface surface;
    private final boolean shouldMutePhone;
    private final long sessionId;
    private final StartupCallback startupCallback;
    private final TntDisplaySelector tntDisplaySelector = new TntDisplaySelector();
    private boolean userServiceRequested;
    private int autoTntStartAttempts;
    private boolean startupReported;

    public ProjectViaMoonlight(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldMutePhone, long sessionId) {
        this(width, height, frameRate, packetDuration, surface, shouldMutePhone, sessionId, null);
    }

    public ProjectViaMoonlight(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldMutePhone, long sessionId, StartupCallback startupCallback) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.packetDuration = packetDuration;
        this.surface = surface;
        this.shouldMutePhone = shouldMutePhone;
        this.sessionId = sessionId;
        this.startupCallback = startupCallback;
    }

    @Override
    public void start() throws YieldException {
        State.log("[ProjectViaMoonlight] start, w=" + width + " h=" + height
                + " fps=" + frameRate + " mutePhone=" + shouldMutePhone);

        Context context = State.getContext();
        if (context == null) {
            State.log("[ProjectViaMoonlight] context is null, abort");
            reportStartup(false);
            return;
        }

        if (!isAndroid10TntFixEnabled()) {
            startDefaultProjection(context);
            return;
        }

        startAndroid10Projection(context);
    }

    private void startDefaultProjection(Context context) throws YieldException {
        boolean tntMode = Pref.getSkipExternalActivity();
        if (tntMode) {
            if (!ensureTntDisplayStartedForClient(context)) {
                return;
            }
            if (!tntDisplaySelector.ensureSelected()) {
                return;
            }
            if (State.externalDisplayId <= 0) {
                State.showErrorStatus("TNT mode did not find an external display");
                return;
            }
            if (!ShizukuUtils.hasPermission()) {
                State.showErrorStatus("TNT mode needs Shizuku permission to mirror the external display");
                return;
            }
            SunshineMouse.initialize(width, height);
            SunshineKeyboard.initialize();
            State.log("[MirrorExternal] start external mirror displayId="
                    + State.externalDisplayId + " controlDisplayId=" + State.externalControlDisplayId
                    + " w=" + width + " h=" + height);
            mirrorExternalDisplay(width, height, surface);
        } else {
            if (!ShizukuUtils.hasPermission()) {
                State.showErrorStatus("Mirror mode needs Shizuku permission to capture display 0");
                return;
            }
            releaseStaleAppMirrorState();
            SunshineMouse.initialize(width, height);
            SunshineKeyboard.initialize();
            State.log("[MirrorPrimary] start display 0 mirror w=" + width + " h=" + height);
            mirrorPrimaryDisplay(width, height, surface);
        }

        State.log(shouldMutePhone
                ? "Moonlight audio route requests phone speaker mute; audio capture is driven by native audio thread"
                : "Moonlight audio route keeps phone speaker enabled; audio capture is driven by native audio thread");
    }

    private void startAndroid10Projection(Context context) throws YieldException {
        boolean tntMode = Pref.getSkipExternalActivity();
        boolean mirrorStarted;
        if (tntMode) {
            if (!ShizukuUtils.hasPermission()) {
                State.showErrorStatus("TNT mode needs Shizuku permission to create and mirror the real TNT desktop display");
                reportStartup(false);
                return;
            }
            if (!State.isUserServiceAlive()) {
                waitForUserService("[ProjectViaMoonlight] userService unavailable before TNT display creation");
            }
            if (!ensureTntDisplayStartedForClient(context)) {
                reportStartup(false);
                return;
            }
            if (!tntDisplaySelector.ensureSelected()) {
                reportStartup(false);
                return;
            }
            if (State.externalDisplayId <= 0) {
                State.showErrorStatus("TNT mode did not find an external display");
                reportStartup(false);
                return;
            }
            SunshineMouse.initialize(width, height);
            SunshineKeyboard.initialize();
            State.log("[MirrorExternal] start external mirror displayId="
                    + State.externalDisplayId + " controlDisplayId=" + State.externalControlDisplayId
                    + " w=" + width + " h=" + height);
            mirrorStarted = mirrorExternalDisplay(width, height, surface);
        } else {
            if (!ShizukuUtils.hasPermission()) {
                State.showErrorStatus("Mirror mode needs Shizuku permission to capture display 0");
                reportStartup(false);
                return;
            }
            releaseStaleAppMirrorState();
            SunshineMouse.initialize(width, height);
            SunshineKeyboard.initialize();
            State.log("[MirrorPrimary] start display 0 mirror w=" + width + " h=" + height);
            mirrorStarted = mirrorPrimaryDisplay(width, height, surface);
        }

        if (!mirrorStarted) {
            reportStartup(false);
            return;
        }

        State.log(shouldMutePhone
                ? "Moonlight audio route requests phone speaker mute; audio capture is driven by native audio thread"
                : "Moonlight audio route keeps phone speaker enabled; audio capture is driven by native audio thread");
        reportStartup(true);
    }

    private boolean ensureTntDisplayStartedForClient(Context context) throws YieldException {
        if (!Pref.getSkipExternalActivity()) {
            return true;
        }
        int targetWidth = Pref.getAdaptTntResolutionToClient() ? width : Pref.getTntOverlayWidth();
        int targetHeight = Pref.getAdaptTntResolutionToClient() ? height : Pref.getTntOverlayHeight();
        int targetDpi = Pref.getTntOverlayDpi();
        boolean useOverlayBackend = TntDisplayStarter.isOverlayBackend();
        boolean externalDisplayPresent = TntDisplaySelector.hasExternalDisplay(context);
        boolean selectableExternalPresent = TntDisplaySelector.hasSelectableExternalDisplay(context);
        boolean physicalExternalPresent = TntDisplaySelector.hasPhysicalExternalDisplay(context);
        boolean baseDisplayPresent = TntDebugVirtualDisplayHelper.isBaseDisplayPresent(context);
        boolean helperActive = TntDebugVirtualDisplayHelper.isActive();
        boolean overlayConfigured = TntOverlayHelper.hasOverlayDisplayConfig();
        boolean overlayMatchesTarget = TntOverlayHelper.isOverlayActiveWithConfig(
                targetWidth,
                targetHeight,
                targetDpi);
        boolean overlayActive = TntOverlayHelper.isOverlayOwnedByApp();
        boolean helperMatchesTarget = TntDebugVirtualDisplayHelper.isActiveWithConfig(
                targetWidth,
                targetHeight,
                targetDpi);

        if (!useOverlayBackend && physicalExternalPresent) {
            State.log("[ProjectViaMoonlight] physical external display already present; skip native base display creation");
            return true;
        }
        if (TntDisplayStarter.isReadyForClient(context, targetWidth, targetHeight, targetDpi)) {
            return true;
        }

        if (autoTntStartAttempts >= 4) {
            State.log("[ProjectViaMoonlight] TNT auto start timed out, externalPresent="
                    + externalDisplayPresent
                    + " selectableExternalPresent=" + selectableExternalPresent
                    + " physicalExternalPresent=" + physicalExternalPresent
                    + " basePresent=" + baseDisplayPresent
                    + " overlayBackend=" + useOverlayBackend);
            TntDisplaySelector.logDisplays(context, "auto-start-timeout");
            if (!useOverlayBackend) {
                if (isAndroid10TntFixEnabled() && baseDisplayPresent && !selectableExternalPresent) {
                    State.showErrorStatus("The system did not create a real TNT desktop display (smt.tnt.virtual.display / displayId 100000+). Phone mirror fallback is disabled in TNT mode.");
                    return false;
                }
                State.showErrorStatus(isAndroid10TntFixEnabled()
                        ? "TNT auto start timed out before a real TNT desktop display appeared. Wait for TNT to finish starting and reconnect Moonlight."
                        : "TNT auto start timed out. Wait for TNT to finish starting and reconnect Moonlight.");
                return false;
            }
            return true;
        }

        if (autoTntStartAttempts == 0) {
            State.log("[ProjectViaMoonlight] ensuring TNT display before Moonlight mirror, externalPresent="
                    + externalDisplayPresent
                    + " selectableExternalPresent=" + selectableExternalPresent
                    + " physicalExternalPresent=" + physicalExternalPresent
                    + " basePresent=" + baseDisplayPresent
                    + " helperActive=" + helperActive
                    + " overlayBackend=" + useOverlayBackend
                    + " overlayActive=" + overlayActive
                    + " overlayConfigured=" + overlayConfigured
                    + " overlayMatchesTarget=" + overlayMatchesTarget
                    + " helperMatchesTarget=" + helperMatchesTarget
                    + " target="
                    + targetWidth + "x" + targetHeight + "/" + targetDpi
                    + (Pref.getAdaptTntResolutionToClient() ? " from Moonlight client request" : " from TNT settings"));
            boolean started = TntDisplayStarter.ensureForClient(targetWidth, targetHeight, targetDpi);
            if (!started) {
                State.showErrorStatus("TNT auto start failed. Start TNT manually and reconnect Moonlight.");
                return false;
            }
        } else {
            State.log("[ProjectViaMoonlight] waiting for TNT display, attempt=" + (autoTntStartAttempts + 1)
                    + " selectableExternalPresent=" + selectableExternalPresent
                    + " physicalExternalPresent=" + physicalExternalPresent
                    + " basePresent=" + baseDisplayPresent);
        }
        autoTntStartAttempts++;
        State.resumeJobLater(1500);
        throw new YieldException("Waiting for TNT display auto start");
    }

    private boolean mirrorPrimaryDisplay(int width, int height, Surface surface) throws YieldException {
        if (Pref.getAutoRotate()) {
            if (isAndroid10TntFixEnabled() && !State.isUserServiceAlive()) {
                waitForUserService("[MirrorPrimaryDisplay] userService unavailable, rebind before auto-rotate mirror");
            }
            State.log("[MirrorPrimaryDisplay] auto-rotate enabled, use GL mirror pipeline");
            AutoRotateAndScaleForMoonlight autoRotatePipeline =
                    new AutoRotateAndScaleForMoonlight(
                            new VirtualDisplayArgs("Moonlight-main-mirror", width, height, frameRate, 160, false));
            SunshineMouse.autoRotateAndScaleForMoonlight = autoRotatePipeline;
            autoRotatePipeline.start(
                    surface,
                    Display.DEFAULT_DISPLAY,
                    "Moonlight-main-mirror",
                    "Mirror mode failed to mirror display 0. Confirm Shizuku is running, then retry.");
            return true;
        }
        return mirrorDisplay(
                "[MirrorPrimaryDisplay]",
                "Moonlight-main-mirror",
                Display.DEFAULT_DISPLAY,
                Display.DEFAULT_DISPLAY,
                width,
                height,
                surface,
                "Mirror mode failed to mirror display 0. Confirm Shizuku is running, then retry.");
    }

    private void releaseStaleAppMirrorState() {
        if (State.mirrorVirtualDisplay != null) {
            try {
                State.log("[MirrorPrimary] release stale app VirtualDisplay before Shizuku mirror");
                State.mirrorVirtualDisplay.release();
            } catch (Exception e) {
                State.log("[MirrorPrimary] release stale app VirtualDisplay failed: "
                        + e.getClass().getSimpleName() + " " + e.getMessage());
            }
            State.mirrorVirtualDisplay = null;
        }
        State.log("[MirrorPrimary] keep MediaProjection for Android native audio capture");
    }

    private boolean mirrorExternalDisplay(int width, int height, Surface surface) throws YieldException {
        return mirrorDisplay(
                "[MirrorExternalDisplay]",
                "Moonlight-mirror",
                State.externalDisplayId,
                State.externalControlDisplayId > 0 ? State.externalControlDisplayId : State.externalDisplayId,
                width,
                height,
                surface,
                "TNT mode failed to mirror external display. Restart Sunshine service and retry.");
    }

    private boolean mirrorDisplay(
            String logPrefix,
            String mirrorName,
            int displayIdToMirror,
            int controlDisplayId,
            int width,
            int height,
            Surface surface,
            String failureMessage) throws YieldException {
        State.log(logPrefix + " enter, surface=" + surface
                + " w=" + width + " h=" + height
                + " displayId=" + displayIdToMirror
                + " userService=" + State.userService);
        try {
            if (!State.isUserServiceAlive()) {
                waitForUserService(logPrefix + " userService unavailable, rebind before mirroring display");
            }
            Surface mirrorSurface = surface;
            ExternalDisplayFramePacer framePacer = startFramePacerIfNeeded(
                    logPrefix,
                    width,
                    height,
                    frameRate,
                    surface);
            SunshineMouse.setExternalDisplayFramePacer(framePacer, sessionId);
            if (framePacer != null) {
                mirrorSurface = framePacer.getInputSurface();
            }
            State.log(logPrefix + " call userService.createExternalMirror");
            int result = State.userService.createExternalMirror(mirrorName, width, height, displayIdToMirror, mirrorSurface);
            State.log(logPrefix + " createExternalMirror result=" + result);
            if (result < 0) {
                SunshineMouse.stopExternalDisplayFramePacer(sessionId, false);
                State.showErrorStatus(failureMessage);
                return false;
            }
            State.mirrorExternalToken = null;
            State.lastSingleAppDisplay = controlDisplayId;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                State.log(logPrefix + " [API31+] success, vdId=" + result);
            } else {
                State.log(logPrefix + " [API30] SurfaceControl success");
            }
            SunshineServer.showMoonlightControlHint();
            return true;
        } catch (YieldException e) {
            throw e;
        } catch (RemoteException e) {
            State.log(logPrefix + " RemoteException: "
                    + e.getClass().getSimpleName() + " msg=" + e.getMessage());
            State.userService = null;
            waitForUserService(logPrefix + " userService binder died, rebind and retry");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            State.log(logPrefix + " exception: "
                    + e.getClass().getSimpleName() + " msg=" + e.getMessage());
        }
        return false;
    }

    private ExternalDisplayFramePacer startFramePacerIfNeeded(
            String logPrefix,
            int width,
            int height,
            int frameRate,
            Surface outputSurface) {
        if (Pref.getEncoderDynamicFrameRate()) {
            State.log(logPrefix + " dynamic frame rate enabled, use direct encoder surface");
            return null;
        }
        ExternalDisplayFramePacer framePacer = null;
        try {
            framePacer = new ExternalDisplayFramePacer(width, height, frameRate, outputSurface);
            framePacer.start();
            State.log(logPrefix + " fixed frame pacer enabled");
            return framePacer;
        } catch (RuntimeException e) {
            State.log(logPrefix + " fixed frame pacer unavailable, fallback to direct surface: "
                    + e.getMessage());
            if (framePacer != null) {
                framePacer.stop();
            }
            return null;
        }
    }

    private void waitForUserService(String reason) throws YieldException {
        State.log(reason);
        if (!ShizukuUtils.hasPermission()) {
            State.showErrorStatus("Moonlight mirror needs Shizuku permission");
            throw new RuntimeException("Shizuku permission missing");
        }
        if (userServiceRequested) {
            State.showErrorStatus("Moonlight mirror failed to wait for Shizuku user service. Confirm Shizuku is running, then retry.");
            throw new RuntimeException("Shizuku user service unavailable");
        }
        userServiceRequested = true;
        State.unbindUserService();
        State.bindUserService();
        State.resumeJobLater(3000);
        throw new YieldException("Waiting for Shizuku user service");
    }

    private void reportStartup(boolean success) {
        if (startupReported) {
            return;
        }
        startupReported = true;
        if (startupCallback != null) {
            startupCallback.onStartupComplete(success);
        }
    }

    private static boolean isAndroid10TntFixEnabled() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
    }
}
