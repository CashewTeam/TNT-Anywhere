package com.connect_screen.mirror.shizuku;

import android.view.Surface;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String fetchLogs() = 2;

    String executeCommand(String command) = 3;

    boolean setScreenPower(int powerMode) = 4;

    void startListenVolumeKey() = 5;

    void stopListenVolumeKey() = 6;

    int createVirtualDisplay(in Surface surface) = 7;

    boolean isRooted() = 8;

    int readAudio(out float[] buffer) = 9;

    boolean startRecordingAudio() = 10;

    boolean stopRecordingAudio() = 11;

    IBinder createDisplay(String name, boolean secure) = 12;

    int createExternalMirror(String name, int width, int height, int displayIdToMirror, in Surface surface) = 13;
    void destroyExternalMirror() = 14;
    String executeShellCommand(String command) = 15;
   int startDisplayScreenshotMirror(int width, int height, int displayIdToMirror, in Surface surface, int fps) = 16;
   void stopDisplayScreenshotMirror() = 17;
   int redirectDisplayToSurface(int displayId, in Surface surface) = 18;
}
