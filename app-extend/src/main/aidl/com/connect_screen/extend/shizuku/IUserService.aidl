package com.connect_screen.extend.shizuku;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String fetchLogs() = 2;

    String executeCommand(String command) = 3;

    void setScreenPower(int powerMode) = 4;

    void startListenVolumeKey() = 5;

    void stopListenVolumeKey() = 6;

    IBinder createDisplay(String name, boolean secure) = 7;

    int createExternalMirror(String name, int width, int height, int displayIdToMirror, in Surface surface) = 8;
    void destroyExternalMirror() = 9;
}