package com.gitee.connect_screen.job;

public class MirrorArgs {
    public final int monitorWidth;
    public final int monitorHeight;
    public final int virtualDisplayWidth;
    public final int refreshRate;

    public MirrorArgs() {
        this(1920, 1080, 1920, 60);
    }
    public MirrorArgs(int monitorWidth, int monitorHeight, int virtualDisplayWidth, int refreshRate) {
        if(monitorWidth == 0) {
            monitorWidth = 1920;
        }
        if (virtualDisplayWidth == 0) {
            virtualDisplayWidth = 1920;
        }
        if (monitorHeight == 0) {
            monitorHeight = 1080;
        }
        if (refreshRate == 0) {
            refreshRate = 60;
        }
        this.monitorWidth = monitorWidth;
        this.monitorHeight = monitorHeight;
        this.virtualDisplayWidth = virtualDisplayWidth;
        this.refreshRate = refreshRate;
    }
}