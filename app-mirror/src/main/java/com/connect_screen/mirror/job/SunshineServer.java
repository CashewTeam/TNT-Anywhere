package com.connect_screen.mirror.job;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.content.Context;
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
    private static volatile long activeMoonlightSessionId;
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
    
    
    // surface created by MediaCodec
    // width always > height, as it is a landscape mode
    public static void createVirtualDisplay(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldMutePhone, long sessionId) {
        suppressPin = null;
        activeMoonlightSessionId = sessionId;
        SmartisanPerformanceHelper.updateStreamingBoost(true, "Moonlight session starting");
        Context context = State.getContext();
        if (context == null) {
            return;
        }

        SunshineMouse.initialize(width, height);
        SunshineKeyboard.initialize();
        
        new Handler(Looper.getMainLooper()).post(() -> {
            State.startNewJob(new ProjectViaMoonlight(width, height, frameRate, packetDuration, surface, shouldMutePhone, sessionId));
        });
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

    private static void cleanupMoonlightProjection(long sessionId) {
        boolean force = sessionId == 0;
        long activeSessionId = activeMoonlightSessionId;
        if (!force && activeSessionId != 0 && activeSessionId != sessionId) {
            State.log("跳过过期 Moonlight 投屏清理，session=" + sessionId
                    + " active=" + activeSessionId);
            return;
        }
        State.log("停止 Moonlight 投屏");
        activeMoonlightSessionId = 0;
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
        if (Pref.getAutoCloseTntOnClientDisconnect()) {
            if (Pref.getUseTntOverlayBackend()) {
                TntOverlayHelper.clearOverlayDisplay();
            } else {
                TntDebugVirtualDisplayHelper.clearVirtualDisplay();
            }
        }
    }

    // 添加新方法用于启动音频录制
    public static native void startAudioRecording(Object audioRecord, int framesPerPacket);

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
