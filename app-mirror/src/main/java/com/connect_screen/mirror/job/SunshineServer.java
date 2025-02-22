package com.connect_screen.mirror.job;

public class SunshineServer {
    static {
        System.loadLibrary("sunshine");
    }

    public native void start();
}
