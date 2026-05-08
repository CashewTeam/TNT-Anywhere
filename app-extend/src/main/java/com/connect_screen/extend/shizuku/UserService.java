package com.connect_screen.extend.shizuku;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.display.IDisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Keep;

import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private Context context;
    private boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;
    private VirtualDisplay mirrorVirtualDisplay;
    private IBinder mirrorExternalToken;

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

    public void setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        IDisplayManager displayManager = IDisplayManager.Stub.asInterface(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        if (Build.VERSION.SDK_INT >= 35) {
            if (powerMode == SurfaceControl.POWER_MODE_OFF) {
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
                    Log.i("UserService", "requestDisplayPower by bool");
                } catch(Throwable e) {
                    Log.e("UserService", "failed to power off screen", e);
                    try {
                        displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
                        Log.i("UserService", "requestDisplayPower by int");
                    } catch(Throwable e2) {
                        Log.e("UserService", "failed to power off screen", e2);
                    }
                }
            } else {
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
                    Log.i("UserService", "requestDisplayPower by bool");
                } catch (Throwable e) {
                    Log.e("UserService", "failed to power up screen", e);
                    try {
                        displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
                        Log.i("UserService", "requestDisplayPower by int");
                    } catch(Throwable e2) {
                        Log.e("UserService", "failed to power up screen", e2);
                    }
                }
            }
        } else {
            IBinder d = SurfaceControl.getBuiltInDisplay();
            if (d == null) {
                Log.i("UserService", "Could not get built-in display");
            } else {
                SurfaceControl.setDisplayPowerMode(d, powerMode);
                Log.i("UserService", "setDisplayPowerMode success");
            }
        }
    }

    public void startListenVolumeKey() throws RemoteException {
        if (listenVolumeKey) {
            return;
        }
        listenVolumeKey = true;
        Thread thread = new Thread(() -> {
            try {
                listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                while (true) {
                    String line = reader.readLine();
                    if (line == null || !listenVolumeKey) {
                        break;
                    }
                    if (!line.endsWith("0000 0000 00000000") &&
                        (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                        Log.i("UserService", "try to exit pure black activity");
                        setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                        if (context != null) {
                            Intent intent = new Intent("com.connect_screen.extend.EXIT_PURE_BLACK");
                            intent.setPackage("com.connect_screen.extend");
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

    @Override
    public IBinder createDisplay(String name, boolean secure) throws RemoteException {
        try {
            return SurfaceControl.createDisplay(name, secure);
        } catch (Exception e) {
            Log.e("UserService", "createDisplay failed", e);
            return null;
        }
    }

    @Override
    public int createExternalMirror(String name, int width, int height, int displayIdToMirror, Surface surface) throws RemoteException {
        Log.i("UserService", "createExternalMirror: name=" + name + " mirroring displayId=" + displayIdToMirror + " sdk=" + Build.VERSION.SDK_INT);
        try {
            if (context == null) {
                Log.e("UserService", "createExternalMirror: context is null");
                return -1;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return createExternalMirrorApi31(name, width, height, displayIdToMirror, surface);
            } else {
                return createExternalMirrorApi30(name, width, height, displayIdToMirror, surface);
            }
        } catch (Exception e) {
            Log.e("UserService", "createExternalMirror failed", e);
            return -1;
        }
    }

    private int createExternalMirrorApi31(String name, int width, int height, int displayIdToMirror, Surface surface) throws Exception {
        android.hardware.display.DisplayManager dm =
                (android.hardware.display.DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Method method = android.hardware.display.DisplayManager.class
                .getMethod("createVirtualDisplay", String.class, int.class, int.class, int.class, Surface.class);
        if (mirrorVirtualDisplay != null) {
            mirrorVirtualDisplay.release();
            mirrorVirtualDisplay = null;
        }
        mirrorVirtualDisplay = (VirtualDisplay) method.invoke(dm, name, width, height, displayIdToMirror, surface);
        int vdId = mirrorVirtualDisplay.getDisplay().getDisplayId();
        Log.i("UserService", "createExternalMirror [API31+] success, virtualDisplayId=" + vdId);
        return vdId;
    }

    private int createExternalMirrorApi30(String name, int width, int height, int displayIdToMirror, Surface surface) throws Exception {
        Log.i("UserService", "createExternalMirror [API30] 开始, name=" + name + " w=" + width + " h=" + height + " displayId=" + displayIdToMirror);
        IDisplayManager dm = IDisplayManager.Stub.asInterface(
                SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        android.view.DisplayInfo extInfo = dm.getDisplayInfo(displayIdToMirror);
        if (extInfo == null) {
            Log.e("UserService", "createExternalMirror [API30]: getDisplayInfo(" + displayIdToMirror + ") 返回 null");
            return -1;
        }
        Log.d("UserService", "createExternalMirror [API30] getDisplayInfo 成功");
        int layerStack;
        try {
            layerStack = extInfo.layerStack;
            Log.d("UserService", "createExternalMirror [API30] layerStack=" + layerStack);
        } catch (NoSuchFieldError e) {
            Log.w("UserService", "createExternalMirror [API30] layerStack字段不存在, 尝试 dumpsys");
            layerStack = getLayerStackFromDumpsys(displayIdToMirror);
            Log.d("UserService", "createExternalMirror [API30] dumpsys layerStack=" + layerStack);
        }
        if (layerStack <= 0) {
            Log.e("UserService", "createExternalMirror [API30]: layerStack=" + layerStack);
            return -1;
        }
        Log.d("UserService", "createExternalMirror [API30] 准备 SurfaceControl.createDisplay...");
        IBinder token = SurfaceControl.createDisplay(name, false);
        Log.d("UserService", "createExternalMirror [API30] token=" + token);
        if (token == null) {
            Log.e("UserService", "createExternalMirror [API30]: SurfaceControl.createDisplay 返回 null");
            return -1;
        }
        Rect r = new Rect(0, 0, width, height);
        Log.d("UserService", "createExternalMirror [API30] 事务: surface→projection→layerStack, displayRect=" + r);
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(token, surface);
            Log.d("UserService", "createExternalMirror [API30] setDisplaySurface 完成");
            SurfaceControl.setDisplayProjection(token, 0, r, r);
            Log.d("UserService", "createExternalMirror [API30] setDisplayProjection 完成");
            SurfaceControl.setDisplayLayerStack(token, layerStack);
            Log.d("UserService", "createExternalMirror [API30] setDisplayLayerStack 完成");
        } finally {
            SurfaceControl.closeTransaction();
        }
        Log.d("UserService", "createExternalMirror [API30] 事务已提交");
        mirrorExternalToken = token;
        SurfaceControl.setDisplayPowerMode(token, SurfaceControl.POWER_MODE_NORMAL);
        Log.d("UserService", "createExternalMirror [API30] setDisplayPowerMode NORMAL 完成");
        Log.i("UserService", "createExternalMirror [API30] 全部完成, layerStack=" + layerStack);
        return 0;
    }

    private int getLayerStackFromDumpsys(int displayId) {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys display");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            Pattern regex = Pattern.compile(
                    "^    mOverrideDisplayInfo=DisplayInfo\\{\".*?, displayId " + displayId + ".*?(, FLAG_.*)?, real ([0-9]+) x ([0-9]+).*?, "
                            + "rotation ([0-9]+).*?, density ([0-9]+).*?, layerStack ([0-9]+)",
                    Pattern.MULTILINE);
            Matcher m = regex.matcher(output.toString());
            if (m.find()) {
                return Integer.parseInt(m.group(6));
            }
        } catch (Exception e) {
            Log.e("UserService", "dumpsys display parse layerStack failed", e);
        }
        return -1;
    }

    @Override
    public void destroyExternalMirror() throws RemoteException {
        Log.i("UserService", "destroyExternalMirror");
        if (mirrorVirtualDisplay != null) {
            mirrorVirtualDisplay.release();
            mirrorVirtualDisplay = null;
        }
        if (mirrorExternalToken != null) {
            SurfaceControl.destroyDisplay(mirrorExternalToken);
            mirrorExternalToken = null;
        }
    }
}
