package com.connect_screen.mirror.job;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.widget.EditText;
import android.widget.Toast;
import android.media.AudioRecord;

import android.media.AudioManager;

import androidx.annotation.NonNull;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.R;
import com.connect_screen.mirror.SmartisanPerformanceHelper;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.connect_screen.mirror.TouchpadActivity;
import com.connect_screen.mirror.shizuku.ServiceUtils;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.connect_screen.mirror.shizuku.SurfaceControl;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.rikka.tools.refine.Refine;

// 代码拷贝自 v2025.122.141614
public class SunshineServer {
    public static String suppressPin;
    public static String pinCandidate;
    private static final AtomicBoolean stoppingVirtualDisplay = new AtomicBoolean(false);
    private static final long AUTO_SCREEN_OFF_DELAY_MS = 30_000L;
    private static final long MOONLIGHT_PROJECTION_START_TIMEOUT_MS = 15_000L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile long activeMoonlightSessionId;
    private static volatile Thread videoSourceThread;
    private static volatile Surface activeEncoderSurface;
    private static volatile boolean videoSourceCancelled;
    private static Runnable autoScreenOffRunnable;
    private static final String MOONLIGHT_CONTROL_HINT =
            "按 Ctrl+Alt+Shift+C 打开光标\n如果不可操控，请在 Moonlight 切换一下控制模式";

    static {
        System.loadLibrary("sunshine");
    }

    public static native void start();

    public static native void setSunshineName(String sunshineName);
    public static native void setPkeyPath(String path);
    public static native void setCertPath(String path);
    public static native void setFileStatePath(String path);
    public static native void setVideoCodec(int codec);
    public static native void setEncoderSettings(
            int bitratePercent,
            int bitrateMode,
            int complexity,
            int iFrameInterval,
            int maxFps,
            boolean lowLatency,
            boolean disableBFrames,
            boolean realtimePriority,
            int fecPercent);
    public static native void setEncoderAvcSettings(int profile, int level);

    public static void setEncoderSettingsFromPreferences() {
        setEncoderSettings(
                Pref.getEncoderBitratePercent(),
                Pref.getEncoderBitrateMode(),
                Pref.getEncoderComplexity(),
                Pref.getEncoderIFrameInterval(),
                Pref.getEncoderMaxFps(),
                Pref.getEncoderLowLatency(),
                Pref.getEncoderDisableBFrames(),
                Pref.getEncoderRealtimePriority(),
                Pref.getStreamFecPercent());
        setEncoderAvcSettings(Pref.getEncoderAvcProfile(), Pref.getEncoderAvcLevel());
    }
    
    // 添加新的回调方法，当需要 PIN 码时被 C++ 代码调用
    public static void onPinRequested() {
        // 使用 Handler 将回调切换到主线程
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.getContext();
            if (context == null) {
                return;
            }
            
            // 创建一个输入框
            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText(pinCandidate);
            // 限制输入长度为4位
            InputFilter[] filters = new InputFilter[1];
            filters[0] = new InputFilter.LengthFilter(4);
            input.setFilters(filters);
            
            // 创建对话框
            if (suppressPin != null) {
                submitPin(suppressPin);
            } else {
                MaterialAlertDialogBuilder builder =
                        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog);
                builder.setTitle("请输入PIN码")
                        .setMessage("请输入4位数字PIN码")
                        .setView(input)
                        .setPositiveButton("确定", (dialog, which) -> {
                            String pin = input.getText().toString();
                            if (pin.length() == 4) {
                                // 这里添加处理PIN码的逻辑
                                // 例如：调用native方法将PIN码传递给C++代码
                                submitPin(pin);
                            } else {
                                Toast.makeText(context, "请输入4位数字PIN码", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", (dialog, which) -> dialog.cancel())
                        .show();
            }
        });
    }
    
    // 添加提交PIN码的native方法
    public static native void submitPin(String pin);

    public static native void notifyVideoSourceFailure();
    
    
    // surface created by MediaCodec
    // width always > height, as it is a landscape mode
    public static boolean createVirtualDisplay(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldMutePhone, long sessionId) {
        suppressPin = null;
        activeMoonlightSessionId = sessionId;
        State.refreshMainActivity();
        scheduleAutoScreenOffForSession(sessionId);
        SmartisanPerformanceHelper.updateStreamingBoost(true, "Moonlight session starting");
        Context context = State.getContext();
        if (context == null) {
            State.log("[ProjectViaMoonlight] context is null before scheduling projection startup");
            return !isAndroid10TntFixEnabled();
        }

        try {
            SunshineMouse.initialize(width, height);
        } catch(Throwable e) {
            State.log("[SunshineServer] SunshineMouse.initialize failed (non-fatal): " + e.getMessage());
        }
        try {
            SunshineKeyboard.initialize();
        } catch(Throwable e) {
            State.log("[SunshineServer] SunshineKeyboard.initialize failed (non-fatal): " + e.getMessage());
        }

       if (!isAndroid10TntFixEnabled()) {
           // API<28 no longer creates the encoder VD from this entry; native
           // calls onVideoInputSurface instead. Keep a ProjectViaMoonlight
           // fallback for any direct callers.
           State.log("[SunshineVD] createVirtualDisplay on API<28: fallback to ProjectViaMoonlight");
           new Handler(Looper.getMainLooper()).post(() -> {
               State.startNewJob(new ProjectViaMoonlight(width, height, frameRate, packetDuration, surface, shouldMutePhone, sessionId));
           });
           return true;
       }

        CountDownLatch startupLatch = new CountDownLatch(1);
        AtomicBoolean startupSucceeded = new AtomicBoolean(false);
        ProjectViaMoonlight.StartupCallback startupCallback = success -> {
            startupSucceeded.set(success);
            startupLatch.countDown();
        };

        MAIN_HANDLER.post(() -> {
            if (State.isJobRunning()) {
                State.log("[ProjectViaMoonlight] cannot start Moonlight projection because another job is running");
                State.showErrorStatus("Moonlight projection cannot start because another task is still running. Retry after it finishes.");
                startupCallback.onStartupComplete(false);
                return;
            }

            State.startNewJob(new ProjectViaMoonlight(
                    width,
                    height,
                    frameRate,
                    packetDuration,
                    surface,
                    shouldMutePhone,
                    sessionId,
                    startupCallback));
            if (!State.isJobRunning() && startupLatch.getCount() > 0) {
                State.log("[ProjectViaMoonlight] projection job finished without reporting startup result");
                startupCallback.onStartupComplete(false);
            }
        });

        try {
            if (!startupLatch.await(MOONLIGHT_PROJECTION_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                State.log("[ProjectViaMoonlight] projection startup wait timed out after "
                        + MOONLIGHT_PROJECTION_START_TIMEOUT_MS + "ms");
                MAIN_HANDLER.post(() -> {
                    State.cancelCurrentJob("Moonlight projection setup timed out");
                    State.showErrorStatus("Moonlight projection setup timed out before video source was ready. Reconnect after TNT or mirror mode is ready.");
                });
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            State.log("[ProjectViaMoonlight] projection startup wait interrupted");
            return false;
        }
        State.log("[ProjectViaMoonlight] projection startup result=" + startupSucceeded.get());
        return startupSucceeded.get();
    }

    public static void onVideoInputSurface(Surface surface, int width, int height, int frameRate, long sessionId) {
        if (surface == null) {
            State.log("[SunshineVD] onVideoInputSurface called with null surface");
            return;
        }
        suppressPin = null;
        activeMoonlightSessionId = sessionId;
        State.refreshMainActivity();
        scheduleAutoScreenOffForSession(sessionId);
        SmartisanPerformanceHelper.updateStreamingBoost(true, "Moonlight session starting");
        try {
            SunshineMouse.initialize(width, height);
        } catch (Throwable e) {
            State.log("[SunshineServer] SunshineMouse.initialize failed (non-fatal): " + e.getMessage());
        }
        try {
            SunshineKeyboard.initialize();
        } catch (Throwable e) {
            State.log("[SunshineServer] SunshineKeyboard.initialize failed (non-fatal): " + e.getMessage());
        }

        activeEncoderSurface = surface;
        videoSourceCancelled = false;
        Thread previous = videoSourceThread;
        if (previous != null && previous.isAlive()) {
            State.log("[SunshineVD] replacing previous video source worker");
        }
        Thread worker = new Thread(() -> startMoonlightVideoSource(surface, width, height, frameRate), "moonlight-video-source");
        worker.setDaemon(true);
        videoSourceThread = worker;
        worker.start();
    }

    private static void startMoonlightVideoSource(Surface encoderSurface, int width, int height, int frameRate) {
        Context context = State.getContext();
        if (context == null) {
            State.log("[SunshineVD] context is null before starting video source");
            failMoonlightVideoSource("TNT 视频源启动失败: context is null");
            return;
        }
        try {
            if (!Pref.getSkipExternalActivity()) {
                State.log("[SunshineVD] mirror mode: route to ProjectViaMoonlight");
                MAIN_HANDLER.post(() -> {
                    if (!videoSourceCancelled) {
                        State.startNewJob(new ProjectViaMoonlight(width, height, frameRate, 0, encoderSurface, true, activeMoonlightSessionId));
                    }
                });
                return;
            }

            State.log("[SunshineVD] TNT mode: ARctrl-style video source " + width + "x" + height + "@" + frameRate);
            int dpi = Pref.getTntOverlayDpi();
            if (!State.isUserServiceAlive()) {
                State.ensureUserServiceBound();
                long deadline = System.currentTimeMillis() + 5000;
                while (!State.isUserServiceAlive() && System.currentTimeMillis() < deadline && !videoSourceCancelled) {
                    Thread.sleep(100);
                }
            }
            if (videoSourceCancelled) {
                return;
            }
            if (!State.isUserServiceAlive()) {
                failMoonlightVideoSource("TNT 视频源启动失败: UserService 不可用");
                return;
            }

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
                    && TntDisplaySelector.hasPhysicalExternalDisplay(context)) {
                State.log("[SunshineVD] 8.1 检测到已启动的真实 TNT 外接屏，直接截图镜像到编码器");
                MAIN_HANDLER.post(() -> {
                    if (!videoSourceCancelled) {
                        State.startNewJob(new ProjectViaMoonlight(
                                width,
                                height,
                                frameRate,
                                0,
                                encoderSurface,
                                true,
                                activeMoonlightSessionId));
                    }
                });
                return;
            }

            // API<28: create the TNT virtual display directly on the encoder
            // surface. Rebinding an ImageReader base later races with
            // TntManagerService and tears down PC mode before frames start.
            if (!TntDebugVirtualDisplayHelper.ensureVirtualDisplay(width, height, dpi, encoderSurface)) {
                if (TntDebugVirtualDisplayHelper.isWhitelistRebootRequired()) {
                    failMoonlightVideoSource("TNT 显示白名单已切换，需要重启手机后生效");
                } else {
                    failMoonlightVideoSource("TNT 视频源启动失败: encoder Surface 绑定虚拟屏失败");
                }
                return;
            }
            if (videoSourceCancelled) {
                return;
            }

            TntDisplaySelector selector = new TntDisplaySelector();
            boolean selected = TntDisplaySelector.hasSelectableExternalDisplay(context);
            long deadline = System.currentTimeMillis() + 5000;
            while (!selected && System.currentTimeMillis() < deadline && !videoSourceCancelled) {
                Thread.sleep(100);
                selected = TntDisplaySelector.hasSelectableExternalDisplay(context);
            }
            if (videoSourceCancelled) {
                return;
            }
            if (!selected) {
                selected = selector.ensureSelected();
            }
            if (!selected) {
                failMoonlightVideoSource("TNT 视频源启动失败: TNT 显示未出现");
                return;
            }
            selector.ensureSelected();
            try {
                SunshineMouse.initialize(width, height);
                SunshineKeyboard.initialize();
            } catch (Throwable t) {
                State.log("[SunshineVD] input re-init failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
            }
            State.log("[SunshineVD] TNT encoder-surface virtual display active");
            showMoonlightControlHint();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            State.log("[SunshineVD] video source worker interrupted");
        } catch (Throwable t) {
            State.log("[SunshineVD] video source failed: " + t.getClass().getSimpleName() + " " + t.getMessage());
            failMoonlightVideoSource("TNT 视频源启动失败: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
        }
    }

    private static void failMoonlightVideoSource(String message) {
        State.log("[SunshineVD] " + message);
        showEncoderError(message);
        notifyVideoSourceFailure();
        stopVirtualDisplay();
    }

    private static boolean isAndroid10TntFixEnabled() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
    }

    public static boolean isMoonlightSessionActive() {
        return activeMoonlightSessionId != 0;
    }

    public static void updateStreamingDebugInfo(String info) {
        String nativeDebugInfo = info;
        new Handler(Looper.getMainLooper()).post(() -> {
            String debugInfo = nativeDebugInfo;
            String smartisanBoostInfo = SmartisanPerformanceHelper.getDebugStatusLine();
            if (!smartisanBoostInfo.isEmpty()) {
                debugInfo = debugInfo + "\n" + smartisanBoostInfo;
            }
            String framePacerInfo = SunshineMouse.collectFramePacerDebugLine();
            if (!framePacerInfo.isEmpty()) {
                debugInfo = debugInfo + "\n" + framePacerInfo;
            }
            State.streamingDebugInfo.setValue(debugInfo);
        });
    }

    public static void updateLastMoonlightHandshakeInfo(String info) {
        State.lastMoonlightHandshakeInfo = info;
    }

    public static void updateLastMoonlightControlInputInfo(String info) {
        String touchInputInfo = SunshineMouse.collectTouchInputDebugLine();
        if (!touchInputInfo.isEmpty()) {
            State.lastMoonlightControlInputInfo = info + "\n" + touchInputInfo;
            return;
        }
        State.lastMoonlightControlInputInfo = info;
    }

    public static void showMoonlightControlHint() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.getContext();
            if (context == null) {
                return;
            }
            Toast.makeText(context, MOONLIGHT_CONTROL_HINT, Toast.LENGTH_LONG).show();
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    Toast.makeText(context, MOONLIGHT_CONTROL_HINT, Toast.LENGTH_LONG).show(), 3500);
        });
    }

    public static void stopVirtualDisplay() {
        stopVirtualDisplay(0);
    }

    public static void stopVirtualDisplay(long sessionId) {
        if (!stoppingVirtualDisplay.compareAndSet(false, true)) {
            State.log("Moonlight 投屏正在停止，跳过重复停止请求");
            return;
        }
        Runnable cleanup = () -> {
            try {
                cleanupMoonlightProjection(sessionId);
            } finally {
                stoppingVirtualDisplay.set(false);
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run();
            return;
        }

        CountDownLatch done = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                cleanup.run();
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(5, TimeUnit.SECONDS)) {
                State.log("Moonlight 投屏停止等待超时，继续释放编码器");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            State.log("Moonlight 投屏停止等待被中断");
        }
    }

    private static void scheduleAutoScreenOffForSession(long sessionId) {
        cancelAutoScreenOffTimer();
        if (!Pref.getAutoScreenOff()) {
            State.log("[AutoScreenOff] disabled; skip scheduling");
            return;
        }
        autoScreenOffRunnable = () -> {
            if (activeMoonlightSessionId != sessionId) {
                State.log("[AutoScreenOff] skip expired session=" + sessionId
                        + " active=" + activeMoonlightSessionId);
                return;
            }
            if (!Pref.getAutoScreenOff()) {
                State.log("[AutoScreenOff] disabled before timer fired");
                return;
            }
            Context context = State.getContext();
            if (context == null) {
                State.log("[AutoScreenOff] skip because context is null");
                return;
            }
            State.log("[AutoScreenOff] trigger after 30s for session=" + sessionId);
            CreateVirtualDisplay.doPowerOffScreen(context);
        };
        MAIN_HANDLER.postDelayed(autoScreenOffRunnable, AUTO_SCREEN_OFF_DELAY_MS);
        State.log("[AutoScreenOff] scheduled after 30s for session=" + sessionId);
    }

    private static void cancelAutoScreenOffTimer() {
        if (autoScreenOffRunnable == null) {
            return;
        }
        MAIN_HANDLER.removeCallbacks(autoScreenOffRunnable);
        autoScreenOffRunnable = null;
        State.log("[AutoScreenOff] timer cancelled");
    }

    private static void cleanupMoonlightProjection(long sessionId) {
        boolean force = sessionId == 0;
        long activeSessionId = activeMoonlightSessionId;
        if (!force && activeSessionId != 0 && activeSessionId != sessionId) {
            State.log("跳过过期 Moonlight 投屏清理，session=" + sessionId
                    + " active=" + activeSessionId);
            return;
        }
        State.log("停止 Moonlight 投屏");
        cancelAutoScreenOffTimer();
        activeMoonlightSessionId = 0;
        State.refreshMainActivity();
        videoSourceCancelled = true;
        Thread videoThread = videoSourceThread;
        if (videoThread != null && videoThread != Thread.currentThread()) {
            videoThread.interrupt();
            try {
                videoThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        videoSourceThread = null;
        SmartisanPerformanceHelper.updateStreamingBoost(false, "Moonlight session stopped");
        State.streamingDebugInfo.setValue("串流未启动");
        SunshineAudio.restoreVolume(State.getContext());
        SunshineMouse.resetInjectedInputState();
        SunshineMouse.cleanupCursorOverlay();
        CreateVirtualDisplay.powerOnScreen();
        CreateVirtualDisplay.restoreAspectRatio();
        SunshineMouse.stopExternalDisplayFramePacer(sessionId, force);
        if (SunshineMouse.autoRotateAndScaleForMoonlight != null) {
            SunshineMouse.autoRotateAndScaleForMoonlight.stop();
            SunshineMouse.autoRotateAndScaleForMoonlight = null;
        }
        if (State.mirrorVirtualDisplay != null) {
            State.mirrorVirtualDisplay.release();
            State.mirrorVirtualDisplay = null;
        }
        if (State.isUserServiceAlive()) {
            try {
                State.userService.destroyExternalMirror();
            } catch (RemoteException e) {
                State.log("destroyExternalMirror failed: " + e.getMessage());
                State.userService = null;
            }
        } else if (State.userService != null) {
            State.userService = null;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            TntDebugVirtualDisplayHelper.clearVirtualDisplay();
        }
        Surface encoderSurface = activeEncoderSurface;
        activeEncoderSurface = null;
        if (encoderSurface != null) {
            try {
                encoderSurface.release();
            } catch (Throwable e) {
                State.log("release encoder surface failed: " + e.getMessage());
            }
        }
        if (Pref.getAutoCloseTntOnClientDisconnect()) {
            TntDisplayStarter.clearCurrentBackendAndResidual();
        }
    }

    // 添加新方法用于启动音频录制
    public static native void startAudioRecording(Object audioRecord, int framesPerPacket);
    public static native void pushAudioSamples(float[] data, int count);

    public static native void enableH265();

    public static void startMoonlightAudioCapture(int packetDuration, boolean shouldMutePhone) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.getContext();
            if (context == null) {
                State.log("Moonlight audio capture skipped: context is null");
                return;
            }
            SunshineAudio.startClientAudioCapture(context, packetDuration, shouldMutePhone);
        });
    }

    // 添加显示编码器错误的方法
    public static void showEncoderError(String errorMessage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.getContext();
            if (context == null) {
                return;
            }
            
            new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setTitle("无法配置编码器")
                .setMessage(errorMessage)
                .setPositiveButton("确定", (dialog, which) -> {
                    // 关闭对话框后停止虚拟显示
                    stopVirtualDisplay();
                })
                .setCancelable(false)
                .show();
        });
    }

    public static void onConnectScreenClientDiscovered(String connectScreenClient) {
        if (State.discoveredConnectScreenClients.contains(connectScreenClient)) {
            return;
        }
        State.discoveredConnectScreenClients.add(connectScreenClient);
    }

    public static void setConnectScreenServerUuid(String uuid) {
        State.serverUuid = uuid;
        if (!Pref.doNotAutoStartMoonlight) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (Pref.getAutoConnectClient() && !Pref.getSelectedClient().isEmpty()) {
                    ConnectToClient.connect((int)(Math.random() * 9000) + 1000);
                }
            }, 1000);
        }
    }

    public static native boolean exitServer();

}
