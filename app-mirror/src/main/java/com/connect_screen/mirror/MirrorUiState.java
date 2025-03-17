package com.connect_screen.mirror;

public class MirrorUiState {
    private final String mirrorStatusText;
    private final boolean settingsBtnVisibility;
    private final boolean screenOffBtnVisibility;
    private final boolean touchScreenBtnVisibility;
    private final String touchScreenBtnText;

    public MirrorUiState(String mirrorStatusText, boolean settingsBtnVisibility, 
                         boolean screenOffBtnVisibility, boolean touchScreenBtnVisibility, 
                         String touchScreenBtnText) {
        this.mirrorStatusText = mirrorStatusText;
        this.settingsBtnVisibility = settingsBtnVisibility;
        this.screenOffBtnVisibility = screenOffBtnVisibility;
        this.touchScreenBtnVisibility = touchScreenBtnVisibility;
        this.touchScreenBtnText = touchScreenBtnText;
    }

    public String getMirrorStatusText() {
        return mirrorStatusText;
    }

    public boolean isSettingsBtnVisible() {
        return settingsBtnVisibility;
    }

    public boolean isScreenOffBtnVisible() {
        return screenOffBtnVisibility;
    }

    public boolean isTouchScreenBtnVisible() {
        return touchScreenBtnVisibility;
    }
    
    public String getTouchScreenBtnText() {
        return touchScreenBtnText;
    }
} 