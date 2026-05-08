package com.connect_screen.mirror.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.RemoteException;
import android.view.Surface;

import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public class ProjectViaMoonlight implements Job {
    private final int width;
    private final int height;
    private final int frameRate;
    private final int packetDuration;
    private final Surface surface;
    private final boolean shouldSendAudio;
    private final TntDisplaySelector tntDisplaySelector = new TntDisplaySelector();
    private boolean mediaProjectionRequested;
    private boolean userServiceRequested;

    public ProjectViaMoonlight(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldSendAudio) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.packetDuration = packetDuration;
        this.surface = surface;
        this.shouldSendAudio = shouldSendAudio;
    }

    @Override
    public void start() throws YieldException {
        State.log("[ProjectViaMoonlight] start, w=" + width + " h=" + height
                + " fps=" + frameRate + " audio=" + shouldSendAudio);

        Context context = State.getContext();
        if (context == null) {
            State.log("[ProjectViaMoonlight] context is null, abort");
            return;
        }

        if (!tntDisplaySelector.ensureSelected()) {
            return;
        }

        boolean mirrorExternal = Pref.getSkipExternalActivity() && State.externalDisplayId > 0;
        if (mirrorExternal) {
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
            if (!ensureMediaProjectionPermission()) {
                return;
            }
            State.log("[MirrorLocal] start default display mirror w=" + width + " h=" + height);
            mirrorLocalDisplay(width, height, surface);
            State.log("[MirrorLocal] local mirror ready");
        }

        if (shouldSendAudio) {
            if (SunshineAudio.sendAudio(context, packetDuration)) {
                return;
            }
        } else {
            State.log("Client did not request audio; keep playing through phone speaker");
        }
        if (!mirrorExternal && State.getMediaProjection() != null) {
            State.setMediaProjection(null);
        }
    }

    private boolean ensureMediaProjectionPermission() throws YieldException {
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection already exists, skip permission request");
            return true;
        }
        if (State.mirrorVirtualDisplay != null) {
            try {
                State.log("Release stale Moonlight mirror VirtualDisplay before requesting permission again");
                State.mirrorVirtualDisplay.release();
            } catch (Throwable e) {
                State.log("Release stale Moonlight mirror VirtualDisplay failed: " + e.getMessage());
            }
            State.mirrorVirtualDisplay = null;
        }
        if (mediaProjectionRequested) {
            State.showErrorStatus("Moonlight local mirror has not received screen capture permission");
            return false;
        }
        mediaProjectionRequested = true;
        MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
        if (mirrorMainActivity == null) {
            State.showErrorStatus("Open the main UI to grant Moonlight local mirror screen capture permission");
            return false;
        }
        mirrorMainActivity.startMediaProjectionService();
        throw new YieldException("Waiting for screen capture permission");
    }

    private void mirrorLocalDisplay(int width, int height, Surface surface) {
        MediaProjection mediaProjection = State.getMediaProjection();
        if (mediaProjection == null) {
            State.showErrorStatus("Local mirror needs screen capture permission first");
            return;
        }
        try {
            boolean autoRotate = Pref.getAutoRotate();
            boolean autoScale = Pref.getAutoScale();
            if (autoRotate || autoScale) {
                SunshineMouse.autoRotateAndScaleForMoonlight = new AutoRotateAndScaleForMoonlight(
                        new VirtualDisplayArgs("Moonlight", width, height, frameRate, 160, false));
                SunshineMouse.autoRotateAndScaleForMoonlight.start(surface);
                State.log("[MirrorLocal] AutoRotateAndScaleForMoonlight enabled, autoRotate="
                        + autoRotate + " autoScale=" + autoScale);
                SunshineServer.showMoonlightControlHint();
                return;
            }

            State.mirrorVirtualDisplay = mediaProjection.createVirtualDisplay(
                    "Moonlight",
                    width,
                    height,
                    160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null);
            if (State.mirrorVirtualDisplay == null) {
                State.showErrorStatus("Failed to create local mirror VirtualDisplay");
                return;
            }
            State.log("[MirrorLocal] MediaProjection VirtualDisplay id="
                    + State.mirrorVirtualDisplay.getDisplay().getDisplayId()
                    + " size=" + width + "x" + height);
            SunshineServer.showMoonlightControlHint();
        } catch (Throwable e) {
            State.log("[MirrorLocal] create VirtualDisplay failed: "
                    + e.getClass().getSimpleName() + " msg=" + e.getMessage());
            State.showErrorStatus("Local mirror failed: " + e.getMessage());
        }
    }

    private void mirrorExternalDisplay(int width, int height, Surface surface) throws YieldException {
        State.log("[MirrorExternalDisplay] enter, surface=" + surface
                + " w=" + width + " h=" + height
                + " externalDisplayId=" + State.externalDisplayId
                + " userService=" + State.userService);
        try {
            if (!State.isUserServiceAlive()) {
                waitForUserService("[MirrorExternalDisplay] userService unavailable, rebind before mirroring external display");
            }
            State.log("[MirrorExternalDisplay] call userService.createExternalMirror");
            int result = State.userService.createExternalMirror("Moonlight-mirror", width, height, State.externalDisplayId, surface);
            State.log("[MirrorExternalDisplay] createExternalMirror result=" + result);
            if (result < 0) {
                State.showErrorStatus("TNT mode failed to mirror external display. Restart Sunshine service and retry.");
                return;
            }
            State.mirrorExternalToken = null;
            State.lastSingleAppDisplay = State.externalControlDisplayId > 0
                    ? State.externalControlDisplayId
                    : State.externalDisplayId;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                State.log("[MirrorExternalDisplay] [API31+] success, vdId=" + result);
            } else {
                State.log("[MirrorExternalDisplay] [API30] SurfaceControl success");
            }
            SunshineServer.showMoonlightControlHint();
        } catch (YieldException e) {
            throw e;
        } catch (RemoteException e) {
            State.log("[MirrorExternalDisplay] RemoteException: "
                    + e.getClass().getSimpleName() + " msg=" + e.getMessage());
            State.userService = null;
            waitForUserService("[MirrorExternalDisplay] userService binder died, rebind and retry");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            State.log("[MirrorExternalDisplay] exception: "
                    + e.getClass().getSimpleName() + " msg=" + e.getMessage());
        }
    }

    private void waitForUserService(String reason) throws YieldException {
        State.log(reason);
        if (!ShizukuUtils.hasPermission()) {
            State.showErrorStatus("TNT mode needs Shizuku permission");
            throw new RuntimeException("Shizuku permission missing");
        }
        if (userServiceRequested) {
            State.showErrorStatus("TNT mode failed to wait for Shizuku user service. Confirm Shizuku is running, then retry.");
            throw new RuntimeException("Shizuku user service unavailable");
        }
        userServiceRequested = true;
        State.unbindUserService();
        State.bindUserService();
        State.resumeJobLater(3000);
        throw new YieldException("Waiting for Shizuku user service");
    }
}
