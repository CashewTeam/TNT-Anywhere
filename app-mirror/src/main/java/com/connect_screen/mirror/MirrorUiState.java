package com.connect_screen.mirror;

public class MirrorUiState {
    private final boolean screenMirroring;
    private final boolean singleAppMode;
    private final boolean canUseTouchscreen;
    private final boolean permissionDenied;

    public MirrorUiState(boolean screenMirroring, boolean singleAppMode, boolean canUseTouchscreen, boolean permissionDenied) {
        this.screenMirroring = screenMirroring;
        this.singleAppMode = singleAppMode;
        this.canUseTouchscreen = canUseTouchscreen;
        this.permissionDenied = permissionDenied;
    }

    public boolean isScreenMirroring() {
        return screenMirroring;
    }

    public boolean isSingleAppMode() {
        return singleAppMode;
    }

    public boolean canUseTouchscreen() {
        return canUseTouchscreen;
    }
    
    public boolean isPermissionDenied() {
        return permissionDenied;
    }
} 