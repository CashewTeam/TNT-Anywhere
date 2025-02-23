package com.connect_screen.mirror.job;

// 代码拷贝自 v2025.122.141614
public class SunshineServer {
    static {
        System.loadLibrary("sunshine");
    }

    public static native void start();
    
    public static native void setPkeyPath(String path);
    public static native void setCertPath(String path);
}
