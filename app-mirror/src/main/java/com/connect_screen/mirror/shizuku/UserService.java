package com.connect_screen.mirror.shizuku;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.IDisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private Context context;
    private boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;

    public UserService() {
        Log.i("UserService", "constructor");
    }

    @Keep
    public UserService(Context context) {
        this.context = context;
        Log.i("UserService", "constructor with Context: context=" + context.toString());
    }

    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        stopListenVolumeKey();
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -f /sdcard/Download/安卓屏连.log");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "logcat -d failed", e);
            throw new RemoteException("Failed to execute logcat -d: " + e.getMessage());
        }
    }

    @Override
    public String executeCommand(String command) throws RemoteException {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys input");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        }
    }

    public boolean setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        try {
            IBinder displayToken = getDisplayToken();
            if (displayToken == null) {
                return false;
            }
            boolean result = SurfaceControl.setDisplayPowerMode(displayToken, powerMode);
            Ln.d("after setDisplayPowerMode: " + result);
        } catch(Throwable e) {
            Ln.e("setScreenPower failed", e);
        }
        return true;
    }

    private @Nullable IBinder getDisplayToken() {
        try {
            long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
            Ln.d("physicalDisplayIds count: " + physicalDisplayIds.length);
            if (physicalDisplayIds.length > 0) {
                return DisplayControl.getPhysicalDisplayToken(physicalDisplayIds[0]);
            }
            return SurfaceControl.getBuiltInDisplay();
        } catch (Throwable e) {
            Ln.e("failed to getDisplayToken", e);
            try {
                return SurfaceControl.getBuiltInDisplay();
            } catch (Throwable e2) {
                Ln.e("failed to getDisplayToken", e2);
            }
        }
        return null;
    }

    public void startListenVolumeKey() throws RemoteException {
        if (listenVolumeKey) {
            return;
        }
        listenVolumeKey = true;
        Thread thread = new Thread(() -> {
            while(listenVolumeKey) {
                try {
                    listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                    while (listenVolumeKey) {
                        String line = reader.readLine();
                        if (line == null || !listenVolumeKey) {
                            break;
                        }
                        if (!line.endsWith("0000 0000 00000000") &&
                                (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                            Log.i("UserService", "try to exit pure black activity");
                            setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                            if (context != null) {
                                Intent intent = new Intent("com.connect_screen.mirror.EXIT_PURE_BLACK");
                                intent.setPackage("com.connect_screen.mirror");
                                context.sendBroadcast(intent);
                            } else {
                                Log.i("UserService", "context is null, can not send EXIT_PURE_BLACK");
                            }
                        }
                    }
                    reader.close();
                    listenVolumeKeyProcess.waitFor();
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        listenVolumeKeyProcess.destroyForcibly();
                    } else {
                        listenVolumeKeyProcess.destroy();
                    }
                } catch (Exception e) {
                    Log.e("UserService", "Listen volume key failed", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        volumeKeyThread = thread;
        thread.start();
    }

    public void stopListenVolumeKey() {
        listenVolumeKey = false;
        if (listenVolumeKeyProcess != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                listenVolumeKeyProcess.destroyForcibly();
            } else {
                listenVolumeKeyProcess.destroy();
            }
            listenVolumeKeyProcess = null;
        }
        if (volumeKeyThread != null) {
            volumeKeyThread.interrupt();
            volumeKeyThread = null;
        }
    }
}
