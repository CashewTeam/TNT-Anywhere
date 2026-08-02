package com.connect_screen.mirror.shizuku;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.display.IDisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;
import android.view.Surface;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.connect_screen.mirror.job.CreateVirtualDisplay;
import com.connect_screen.mirror.BuildConfig;

import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private static final int SMARTISAN_TNT_DISPLAY_ID_MIN = 100000;

    private Context context;
    private boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;
    private volatile Process audioLoopbackProcess;
    private volatile DataInputStream audioLoopbackStream;
    private volatile File audioLoopbackHelperFile;
    private byte[] audioPcmBuffer;
    private VirtualDisplay mirrorVirtualDisplay;
    private IBinder mirrorExternalToken;
    private volatile boolean screenshotMirrorRunning;
    private Thread screenshotMirrorThread;
    private Surface screenshotMirrorSurface;

    public UserService() {
        Ln.i("Start UserService without context: " + android.os.Process.myUid());
    }

    @Keep
    public UserService(Context context) {
        this.context = context;
        Ln.i("Start UserService with context: " + android.os.Process.myUid());
    }

    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        stopListenVolumeKey();
        try {
            stopDisplayScreenshotMirror();
        } catch (RemoteException e) {
            // local binder call; ignore
        }
        setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
        stopAudioLoopbackProcess();
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -f /sdcard/Download/瀹夊崜灞忚繛.log");
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
            return readProcessOutput(Runtime.getRuntime().exec(command), false);
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        }
    }

    @Override
    public String executeShellCommand(String command) throws RemoteException {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            return readProcessOutput(process, true);
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        }
    }

    private String readProcessOutput(Process process, boolean includeExitCode) throws Exception {
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));

        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        reader.close();
        int exitCode = process.waitFor();
        if (includeExitCode) {
            output.append("__EXIT_CODE=").append(exitCode).append("\n");
        }

        return output.toString();
    }

    public boolean setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        if (Build.VERSION.SDK_INT >= 35 && powerMode == SurfaceControl.POWER_MODE_NORMAL) {
            setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_OFF);
            setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_NORMAL);
        }
        try {
            IBinder displayToken = getDisplayToken();
            if (displayToken == null) {
                return false;
            }
            Ln.d("setDisplayPowerMode: " + displayToken + " " + powerMode);
            boolean result = SurfaceControl.setDisplayPowerMode(displayToken, powerMode);
            Ln.d("after setDisplayPowerMode: " + result);
        } catch(Throwable e) {
            Ln.e("setScreenPower failed", e);
        }
        return true;
    }

    private boolean setScreenPowerViaNewApi(int powerMode) {
        IDisplayManager displayManager = IDisplayManager.Stub.asInterface(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        if (powerMode == SurfaceControl.POWER_MODE_OFF) {
            try {
                displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
                Log.i("UserService", "requestDisplayPower by bool false");
            } catch(Throwable e) {
                Log.e("UserService", "failed to power off screen", e);
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
                    Log.i("UserService", "requestDisplayPower by int: " + powerMode);
                } catch(Throwable e2) {
                    Log.e("UserService", "failed to power off screen", e2);
                    return false;
                }
            }
        } else {
            try {
                displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
                Log.i("UserService", "requestDisplayPower by bool true");
            } catch (Throwable e) {
                Log.e("UserService", "failed to power up screen", e);
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
                    Log.i("UserService", "requestDisplayPower by int: " + powerMode);
                } catch(Throwable e2) {
                    Log.e("UserService", "failed to power up screen", e2);
                    return false;
                }
            }
        }
        return true;
    }

    private @Nullable IBinder getDisplayToken() {
        try {
            long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
            Ln.d("getDisplayToken: physicalDisplayIds count=" + physicalDisplayIds.length + " ids=" + java.util.Arrays.toString(physicalDisplayIds));
            if (physicalDisplayIds.length > 0) {
                Ln.d("getDisplayToken: 浣跨敤 physicalDisplayIds[0]=" + physicalDisplayIds[0]);
                return DisplayControl.getPhysicalDisplayToken(physicalDisplayIds[0]);
            }
            Ln.d("getDisplayToken: physicalDisplayIds 涓虹┖, 浣跨敤 getBuiltInDisplay");
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
                    Ln.i("Run getevent to detect volume key pressed");
                    listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                    while (listenVolumeKey) {
                        String line = reader.readLine();
                        if (line == null || !listenVolumeKey) {
                            Ln.i("break out getevent");
                            break;
                        }
                        if (!line.endsWith("0000 0000 00000000") &&
                                (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                            Ln.i("detected volume key, try to power on screen");
                            setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                            if (context != null) {
                                Intent intent = new Intent("com.connect_screen.mirror.EXIT_PURE_BLACK");
                                intent.setPackage(BuildConfig.APPLICATION_ID);
                                context.sendBroadcast(intent);
                            } else {
                                Ln.i("context is null, can not send EXIT_PURE_BLACK");
                            }
                        }
                    }
                    reader.close();
                    if (listenVolumeKeyProcess != null) {
                        listenVolumeKeyProcess.waitFor();
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            listenVolumeKeyProcess.destroyForcibly();
                        } else {
                            listenVolumeKeyProcess.destroy();
                        }
                    }
                } catch (Exception e) {
                    Ln.e("Listen volume key failed", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Ln.i("getevent thread end");
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
    public int createVirtualDisplay(Surface surface) throws RemoteException {
        Ln.i("try to createVirtualDisplay");
        try {
            return DisplayManager.create().createNewVirtualDisplay("test", 1920, 1080, 160, surface, CreateVirtualDisplay.getFlags(true, true)).getDisplay().getDisplayId();
        } catch (Throwable e) {
            Ln.e("failed to create virtual display", e);
        }
        return 0;
    }


    @Override
    public boolean isRooted() throws RemoteException {
        return android.os.Process.myUid() == 0;
    }

    @Override
    public int readAudio(float[] result) throws RemoteException {
        try {
            DataInputStream stream = audioLoopbackStream;
            if (stream == null) {
                return 0;
            }
            int byteCount = result.length * 2;
            if (audioPcmBuffer == null || audioPcmBuffer.length < byteCount) {
                audioPcmBuffer = new byte[byteCount];
            }
            stream.readFully(audioPcmBuffer, 0, byteCount);
            for (int i = 0; i < result.length; i++) {
                int low = audioPcmBuffer[i * 2] & 0xff;
                int high = audioPcmBuffer[i * 2 + 1];
                result[i] = (short) (low | (high << 8)) / 32768.0f;
            }
            return result.length;
        } catch (EOFException e) {
            Ln.e("audio_loopback helper ended", e);
            stopAudioLoopbackProcess();
            return -1;
        } catch(Throwable e) {
            Ln.e("failed to read audio", e);
            stopAudioLoopbackProcess();
            return -1;
        }
    }

    @Override
    public boolean startRecordingAudio() throws RemoteException {
        try {
            if (audioLoopbackProcess != null && audioLoopbackProcess.isAlive()
                    && audioLoopbackStream != null) {
                return true;
            }
            if (context == null || !android.os.Process.is64Bit()) {
                Ln.e("Smartisan audio_loopback requires a 64-bit UserService context");
                return false;
            }
            File helper = extractAudioLoopbackHelper();
            Process process = new ProcessBuilder(helper.getAbsolutePath()).start();
            DataInputStream stream = new DataInputStream(
                    new BufferedInputStream(process.getInputStream()));
            byte[] ready = new byte[4];
            try {
                stream.readFully(ready);
            } catch (EOFException e) {
                int exitCode = process.waitFor();
                Ln.e("audio_loopback helper failed exit=" + exitCode
                        + " error=" + readProcessError(process));
                stream.close();
                if (!helper.delete()) {
                    Ln.e("failed to delete audio_loopback helper " + helper);
                }
                return false;
            }
            if (!Arrays.equals(ready, new byte[]{'T', 'N', 'T', '1'})) {
                process.destroy();
                stream.close();
                if (!helper.delete()) {
                    Ln.e("failed to delete audio_loopback helper " + helper);
                }
                Ln.e("audio_loopback helper returned an invalid handshake");
                return false;
            }
            audioLoopbackProcess = process;
            audioLoopbackStream = stream;
            audioLoopbackHelperFile = helper;
            Ln.i("Smartisan audio_loopback helper started");
            return true;
        } catch(Throwable e) {
            Ln.e("failed to start recording audio", e);
            stopAudioLoopbackProcess();
            return false;
        }
    }

    @Override
    public boolean stopRecordingAudio() throws RemoteException {
        try {
            stopAudioLoopbackProcess();
            return true;
        } catch(Throwable e) {
            Ln.e("failed to stop recording audio", e);
            return false;
        }
    }

    private File extractAudioLoopbackHelper() throws Exception {
        File helper = new File("/data/local/tmp/tntanywhere-audio-loopback-"
                + BuildConfig.VERSION_CODE + "-" + android.os.Process.myPid());
        try (InputStream input = context.getAssets().open("audio_loopback_helper_arm64");
             FileOutputStream output = new FileOutputStream(helper, false)) {
            byte[] copyBuffer = new byte[8192];
            int count;
            while ((count = input.read(copyBuffer)) != -1) {
                output.write(copyBuffer, 0, count);
            }
        }
        if (!helper.setExecutable(true, true)) {
            throw new IllegalStateException("Cannot make audio_loopback helper executable");
        }
        return helper;
    }

    private void stopAudioLoopbackProcess() {
        DataInputStream stream = audioLoopbackStream;
        Process process = audioLoopbackProcess;
        File helper = audioLoopbackHelperFile;
        audioLoopbackStream = null;
        audioLoopbackProcess = null;
        audioLoopbackHelperFile = null;
        if (process != null) {
            process.destroy();
        }
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Ln.e("failed to close audio_loopback stream", e);
            }
        }
        if (helper != null && helper.exists() && !helper.delete()) {
            Ln.e("failed to delete audio_loopback helper " + helper);
        }
    }

    private String readProcessError(Process process) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder error = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (error.length() > 0) error.append(' ');
            error.append(line);
        }
        return error.toString();
    }

    @Override
    public IBinder createDisplay(String name, boolean secure) throws RemoteException {
        try {
            return SurfaceControl.createDisplay(name, secure);
        } catch (Exception e) {
            Ln.e("createDisplay failed", e);
            return null;
        }
    }

    @Override
    public int createExternalMirror(String name, int width, int height, int displayIdToMirror, Surface surface) throws RemoteException {
        Ln.i("createExternalMirror: name=" + name + " mirroring displayId=" + displayIdToMirror + " sdk=" + Build.VERSION.SDK_INT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return createExternalMirrorApi31(name, width, height, displayIdToMirror, surface);
            } else {
                return createExternalMirrorApi30(name, width, height, displayIdToMirror, surface);
            }
        } catch (Exception e) {
            Ln.e("createExternalMirror failed", e);
            return -1;
        }
    }

    private int createExternalMirrorApi31(String name, int width, int height, int displayIdToMirror, Surface surface) throws Exception {
        android.hardware.display.DisplayManager dm;
        if (context != null) {
            dm = (android.hardware.display.DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        } else {
            java.lang.reflect.Constructor<android.hardware.display.DisplayManager> ctor =
                    android.hardware.display.DisplayManager.class.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            dm = ctor.newInstance(FakeContext.get());
        }
        Method method = android.hardware.display.DisplayManager.class
                .getMethod("createVirtualDisplay", String.class, int.class, int.class, int.class, Surface.class);
        if (mirrorVirtualDisplay != null) {
            mirrorVirtualDisplay.release();
            mirrorVirtualDisplay = null;
        }
        mirrorVirtualDisplay = (VirtualDisplay) method.invoke(dm, name, width, height, displayIdToMirror, surface);
        int vdId = mirrorVirtualDisplay.getDisplay().getDisplayId();
        Ln.i("createExternalMirror [API31+] success, virtualDisplayId=" + vdId);
        return vdId;
    }

    private int createExternalMirrorApi30(String name, int width, int height, int displayIdToMirror, Surface surface) throws Exception {
        Ln.i("createExternalMirror [API30] begin name=" + name + " w=" + width + " h=" + height + " displayId=" + displayIdToMirror + " surface=" + surface);
        IDisplayManager dm = IDisplayManager.Stub.asInterface(
                SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        Ln.d("createExternalMirror [API30] IDisplayManager=" + dm);
        android.view.DisplayInfo extInfo = dm.getDisplayInfo(displayIdToMirror);
        if (extInfo == null) {
            Ln.e("createExternalMirror [API30]: getDisplayInfo(" + displayIdToMirror + ") 杩斿洖 null");
            return -1;
        }
        Ln.d("createExternalMirror [API30] getDisplayInfo success, reading layerStack");
        int layerStack;
        try {
            layerStack = extInfo.layerStack;
            Ln.d("createExternalMirror [API30] layerStack from DisplayInfo=" + layerStack);
        } catch (NoSuchFieldError e) {
            Ln.w("createExternalMirror [API30] layerStack field missing, trying dumpsys");
            layerStack = getLayerStackFromDumpsys(displayIdToMirror);
            Ln.d("createExternalMirror [API30] layerStack from dumpsys=" + layerStack);
        }
        layerStack = resolveExternalMirrorLayerStack(displayIdToMirror, layerStack);
        if (layerStack < 0) {
            Ln.e("createExternalMirror [API30]: layerStack=" + layerStack);
            return -1;
        }
        Ln.d("createExternalMirror [API30] 鍑嗗璋冪敤 SurfaceControl.createDisplay...");
        IBinder token = SurfaceControl.createDisplay(name, true);
        Ln.d("createExternalMirror [API30] createDisplay token=" + token);
        if (token == null) {
            Ln.e("createExternalMirror [API30]: SurfaceControl.createDisplay 杩斿洖 null");
            return -1;
        }
        Rect sourceRect = getSourceDisplayRect(extInfo, width, height);
        Rect displayRect = getAspectFitRect(sourceRect, width, height);
        Ln.d("createExternalMirror [API30] projection sourceRect=" + sourceRect
                + " displayRect=" + displayRect + " encoder=" + width + "x" + height);
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(token, surface);
            Ln.d("createExternalMirror [API30] setDisplaySurface 瀹屾垚");
            SurfaceControl.setDisplayProjection(token, 0, sourceRect, displayRect);
            Ln.d("createExternalMirror [API30] setDisplayProjection 瀹屾垚");
            SurfaceControl.setDisplayLayerStack(token, layerStack);
            Ln.d("createExternalMirror [API30] setDisplayLayerStack 瀹屾垚");
        } finally {
            SurfaceControl.closeTransaction();
        }
        Ln.d("createExternalMirror [API30] transaction committed");
        mirrorExternalToken = token;
        SurfaceControl.setDisplayPowerMode(token, SurfaceControl.POWER_MODE_NORMAL);
        Ln.d("createExternalMirror [API30] setDisplayPowerMode NORMAL 瀹屾垚");
        Ln.i("createExternalMirror [API30] 鍏ㄩ儴瀹屾垚, layerStack=" + layerStack);
        return 0;
    }

    private int resolveExternalMirrorLayerStack(int displayIdToMirror, int displayInfoLayerStack) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                && displayIdToMirror >= SMARTISAN_TNT_DISPLAY_ID_MIN
                && displayInfoLayerStack != displayIdToMirror) {
            Ln.i("createExternalMirror [API30] Smartisan TNT displayId="
                    + displayIdToMirror
                    + " uses layerStack=" + displayIdToMirror
                    + " instead of DisplayInfo override layerStack=" + displayInfoLayerStack);
            return displayIdToMirror;
        }
        return displayInfoLayerStack;
    }

    private Rect getSourceDisplayRect(android.view.DisplayInfo displayInfo, int fallbackWidth, int fallbackHeight) {
        int[] logicalSize = getDisplayInfoSizeFields(displayInfo, "logicalWidth", "logicalHeight");
        if (isValidSize(logicalSize)) {
            Ln.d("createExternalMirror [API30] source size from logical fields="
                    + logicalSize[0] + "x" + logicalSize[1]);
            return new Rect(0, 0, logicalSize[0], logicalSize[1]);
        }

        int[] realSize = parseRealSize(displayInfo.toString());
        if (isValidSize(realSize)) {
            Ln.d("createExternalMirror [API30] source size from DisplayInfo.toString="
                    + realSize[0] + "x" + realSize[1]);
            return new Rect(0, 0, realSize[0], realSize[1]);
        }

        int[] modeSize = getDefaultModeSize(displayInfo);
        if (isValidSize(modeSize)) {
            Ln.d("createExternalMirror [API30] source size from default mode="
                    + modeSize[0] + "x" + modeSize[1]);
            return new Rect(0, 0, modeSize[0], modeSize[1]);
        }

        Ln.w("createExternalMirror [API30] cannot read source display size, fallback to encoder size="
                + fallbackWidth + "x" + fallbackHeight);
        return new Rect(0, 0, fallbackWidth, fallbackHeight);
    }

    private int[] getDisplayInfoSizeFields(android.view.DisplayInfo displayInfo, String widthField, String heightField) {
        try {
            Field w = displayInfo.getClass().getDeclaredField(widthField);
            Field h = displayInfo.getClass().getDeclaredField(heightField);
            w.setAccessible(true);
            h.setAccessible(true);
            return new int[] {w.getInt(displayInfo), h.getInt(displayInfo)};
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private int[] parseRealSize(String displayInfoText) {
        Matcher matcher = Pattern.compile("real ([0-9]+) x ([0-9]+)").matcher(displayInfoText);
        if (!matcher.find()) {
            return null;
        }
        return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    }

    private int[] getDefaultModeSize(android.view.DisplayInfo displayInfo) {
        Display.Mode[] modes = displayInfo.supportedModes;
        if (modes == null || modes.length == 0) {
            return null;
        }
        for (Display.Mode mode : modes) {
            if (mode != null && mode.getModeId() == displayInfo.defaultModeId) {
                return new int[] {mode.getPhysicalWidth(), mode.getPhysicalHeight()};
            }
        }
        Display.Mode first = modes[0];
        return first == null ? null : new int[] {first.getPhysicalWidth(), first.getPhysicalHeight()};
    }

    private boolean isValidSize(int[] size) {
        return size != null && size.length >= 2 && size[0] > 0 && size[1] > 0;
    }

    private Rect getAspectFitRect(Rect sourceRect, int width, int height) {
        if (sourceRect.width() <= 0 || sourceRect.height() <= 0 || width <= 0 || height <= 0) {
            return new Rect(0, 0, width, height);
        }
        long scaledWidthByHeight = (long) height * sourceRect.width();
        long maxScaledWidth = (long) width * sourceRect.height();
        if (scaledWidthByHeight <= maxScaledWidth) {
            int displayWidth = (int) Math.max(1, scaledWidthByHeight / sourceRect.height());
            int left = (width - displayWidth) / 2;
            return new Rect(left, 0, left + displayWidth, height);
        }
        long scaledHeightByWidth = (long) width * sourceRect.height();
        int displayHeight = (int) Math.max(1, scaledHeightByWidth / sourceRect.width());
        int top = (height - displayHeight) / 2;
        return new Rect(0, top, width, top + displayHeight);
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
            DisplayInfo parsed = DisplayManager.parseDisplayInfo(output.toString(), displayId);
            if (parsed != null && parsed.getLayerStack() > 0) {
                return parsed.getLayerStack();
            }
        } catch (Exception e) {
            Ln.e("dumpsys display parse layerStack failed", e);
        }
        return -1;
    }

    @Override
    public void destroyExternalMirror() throws RemoteException {
        Ln.i("destroyExternalMirror");
        stopDisplayScreenshotMirror();
        if (mirrorVirtualDisplay != null) {
            mirrorVirtualDisplay.release();
            mirrorVirtualDisplay = null;
        }
        if (mirrorExternalToken != null) {
            SurfaceControl.destroyDisplay(mirrorExternalToken);
            mirrorExternalToken = null;
        }
    }

    @Override
    public int startDisplayScreenshotMirror(int width, int height, int displayIdToMirror, Surface surface, int fps) throws RemoteException {
        Ln.i("startDisplayScreenshotMirror: displayId=" + displayIdToMirror
                + " size=" + width + "x" + height + " fps=" + fps + " surface=" + surface);
        if (surface == null || !surface.isValid()) {
            Ln.e("startDisplayScreenshotMirror: invalid surface");
            return -1;
        }
        stopDisplayScreenshotMirror();
        IDisplayManager dm = IDisplayManager.Stub.asInterface(
                SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        android.view.DisplayInfo displayInfo;
        try {
            displayInfo = dm.getDisplayInfo(displayIdToMirror);
        } catch (Throwable e) {
            Ln.e("startDisplayScreenshotMirror: getDisplayInfo failed", e);
            return -1;
        }
        if (displayInfo == null) {
            Ln.e("startDisplayScreenshotMirror: display info is null for " + displayIdToMirror);
            return -1;
        }
        Rect sourceRect = getSourceDisplayRect(displayInfo, width, height);
        Rect displayRect = getAspectFitRect(sourceRect, width, height);
        IBinder displayToken = SurfaceControl.getDisplayToken(displayIdToMirror);
        if (displayToken == null) {
            Ln.e("startDisplayScreenshotMirror: display token is null");
            return -1;
        }

        int safeFps = Math.max(1, Math.min(60, fps));
        long frameIntervalMs = Math.max(1, 1000L / safeFps);
        screenshotMirrorSurface = surface;
        screenshotMirrorRunning = true;
        screenshotMirrorThread = new Thread(() -> runScreenshotMirrorLoop(
                displayToken,
                sourceRect,
                displayRect,
                width,
                height,
                frameIntervalMs), "TNTAnywhere-ScreenshotMirror");
        screenshotMirrorThread.start();
        return 0;
    }

    @Override
    public void stopDisplayScreenshotMirror() throws RemoteException {
        screenshotMirrorRunning = false;
        Thread thread = screenshotMirrorThread;
        screenshotMirrorThread = null;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
       screenshotMirrorSurface = null;
   }

   @Override
   public int redirectDisplayToSurface(int displayId, Surface surface) throws RemoteException {
       Ln.i("redirectDisplayToSurface: displayId=" + displayId + " surface=" + surface);
       try {
           IBinder token = SurfaceControl.getDisplayToken(displayId);
           if (token == null) {
               Ln.e("redirectDisplayToSurface: getDisplayToken(" + displayId + ") returned null");
               return -1;
           }
           SurfaceControl.openTransaction();
           SurfaceControl.setDisplaySurface(token, surface);
           SurfaceControl.closeTransaction();
           Ln.i("redirectDisplayToSurface: success, displayId=" + displayId);
           return 0;
       } catch (Exception e) {
           Ln.e("redirectDisplayToSurface failed", e);
           return -1;
       }
   }

   private void runScreenshotMirrorLoop(
            IBinder displayToken,
            Rect sourceRect,
            Rect displayRect,
            int width,
            int height,
            long frameIntervalMs) {
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        Rect fullRect = new Rect(0, 0, width, height);
        long nextFrameAt = android.os.SystemClock.uptimeMillis();
        while (screenshotMirrorRunning) {
            Bitmap bitmap = null;
            Canvas canvas = null;
            try {
                bitmap = captureDisplayBitmap(displayToken, sourceRect, width, height);
                Surface surface = screenshotMirrorSurface;
                if (bitmap != null && surface != null && surface.isValid()) {
                    canvas = surface.lockCanvas(null);
                    canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(bitmap, null, displayRect == null ? fullRect : displayRect, paint);
                }
            } catch (Throwable e) {
                Ln.e("screenshot mirror frame failed", e);
                sleepQuietly(200);
            } finally {
                Surface surface = screenshotMirrorSurface;
                if (canvas != null && surface != null && surface.isValid()) {
                    try {
                        surface.unlockCanvasAndPost(canvas);
                    } catch (Throwable e) {
                        Ln.e("screenshot mirror unlock failed", e);
                    }
                }
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            nextFrameAt += frameIntervalMs;
            long delay = nextFrameAt - android.os.SystemClock.uptimeMillis();
            if (delay > 0) {
                sleepQuietly(delay);
            } else {
                nextFrameAt = android.os.SystemClock.uptimeMillis();
            }
        }
    }

    private Bitmap captureDisplayBitmap(IBinder displayToken, Rect sourceRect, int width, int height) throws Exception {
        Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
        Throwable lastError = null;
        try {
            Method method = surfaceControlClass.getMethod("screenshot", Rect.class, int.class, int.class, int.class);
            Object result = method.invoke(null, sourceRect, width, height, 0);
            Bitmap bitmap = bitmapFromScreenshotResult(result);
            if (bitmap != null) {
                return bitmap;
            }
        } catch (Throwable e) {
            lastError = e;
        }
        try {
            Method method = surfaceControlClass.getMethod("screenshot", IBinder.class, Rect.class, int.class, int.class);
            Object result = method.invoke(null, displayToken, sourceRect, width, height);
            Bitmap bitmap = bitmapFromScreenshotResult(result);
            if (bitmap != null) {
                return bitmap;
            }
        } catch (Throwable e) {
            lastError = e;
        }
        try {
            Object args = buildDisplayCaptureArgs(displayToken, sourceRect, width, height);
            Method method = surfaceControlClass.getMethod("captureDisplay", args.getClass());
            Object result = method.invoke(null, args);
            Bitmap bitmap = bitmapFromScreenshotResult(result);
            if (bitmap != null) {
                return bitmap;
            }
        } catch (Throwable e) {
            lastError = e;
        }
        throw new IllegalStateException("No supported SurfaceControl screenshot API", lastError);
    }

    private Object buildDisplayCaptureArgs(IBinder displayToken, Rect sourceRect, int width, int height) throws Exception {
        Class<?> builderClass = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs$Builder");
        Object builder = builderClass.getConstructor(IBinder.class).newInstance(displayToken);
        tryInvoke(builder, "setSourceCrop", new Class[]{Rect.class}, new Object[]{sourceRect});
        tryInvoke(builder, "setSize", new Class[]{int.class, int.class}, new Object[]{width, height});
        tryInvoke(builder, "setUseIdentityTransform", new Class[]{boolean.class}, new Object[]{true});
        return builderClass.getMethod("build").invoke(builder);
    }

    private void tryInvoke(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            target.getClass().getMethod(name, types).invoke(target, args);
        } catch (Throwable e) {
            // API-level dependent option; ignore if unavailable.
        }
    }

    private Bitmap bitmapFromScreenshotResult(Object result) throws Exception {
        if (result == null) {
            return null;
        }
        if (result instanceof Bitmap) {
            return (Bitmap) result;
        }
        try {
            Object bitmap = result.getClass().getMethod("asBitmap").invoke(result);
            if (bitmap instanceof Bitmap) {
                return (Bitmap) bitmap;
            }
        } catch (NoSuchMethodException e) {
            // Try unwrap below.
        }
        try {
            Object buffer = result.getClass().getMethod("getGraphicBuffer").invoke(result);
            if (buffer != null) {
                Object bitmap = result.getClass().getMethod("asBitmap").invoke(result);
                if (bitmap instanceof Bitmap) {
                    return (Bitmap) bitmap;
                }
            }
        } catch (NoSuchMethodException e) {
            // Not a screenshot buffer wrapper.
        }
        return null;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
