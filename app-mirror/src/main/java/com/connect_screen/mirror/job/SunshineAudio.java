package com.connect_screen.mirror.job;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

import com.connect_screen.mirror.State;

public class SunshineAudio {
    private static boolean isMuted = false;
    private static AudioManager.OnAudioFocusChangeListener volumeChangeListener;
    private static final java.util.concurrent.atomic.AtomicBoolean oemLoopbackActive =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean oemLoopbackRecordingStarted =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile Thread oemLoopbackThread;
    private static volatile boolean oemLoopbackStopRequested;
    private static final java.util.concurrent.atomic.AtomicBoolean oemScreenrecordRouteEnabled =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static volatile boolean android81Rooted;

    public static void setAndroid81Rooted(boolean rooted) {
        android81Rooted = rooted;
    }

    private static boolean isAndroid81OemLoopback() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1;
    }

    public static void startClientAudioCapture(Context context, int packetDuration, boolean shouldMutePhone) {
        boolean started;
        if (isAndroid81OemLoopback()) {
            started = android81Rooted
                    ? startRemoteSubmixAudioCapture(packetDuration)
                    : startOemLoopbackAudioCapture(context, packetDuration);
        } else {
            started = startAudioUseNormalPermission(context, packetDuration);
        }

        if (!started) {
            State.log("Moonlight 音频捕获未启动，继续视频串流");
            return;
        }
        if (isAndroid81OemLoopback()) {
            if (android81Rooted) {
                if (shouldMutePhone) {
                    mutePhoneSpeaker(context);
                } else {
                    State.log("8.1 REMOTE_SUBMIX keeps phone speaker enabled at client request");
                }
            } else {
                State.log("8.1 audio_loopback capture started; phone speaker remains enabled");
            }
        } else if (shouldMutePhone) {
            mutePhoneSpeaker(context);
        } else {
            State.log("客户端请求保留手机端播放，不静音手机扬声器");
        }
    }

    private static void mutePhoneSpeaker(Context context) {
        if (context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            State.log("没有音频控制权限，无法静音");
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            isMuted = true;
            State.log("应客户端的请求对手机静音");
            // 注册音量变化监听器
            registerVolumeChangeListener(context, audioManager);
        } else {
            State.log("静音设置未成功");
        }
    }

    // 添加注册音量变化监听器的方法
    private static void registerVolumeChangeListener(Context context, AudioManager audioManager) {

        // 创建音频焦点变化监听器
        volumeChangeListener = focusChange -> {
            // 如果还在投屏且应该保持静音状态，检查并重新设置静音
            if (isMuted) {
                checkAndRestoreMute();
            }
        };

        // 请求音频焦点以便接收音频变化事件
        audioManager.requestAudioFocus(volumeChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        // 创建内容观察者监听音量变化
        context.getContentResolver().registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        // 如果还在投屏且应该保持静音状态，检查并重新设置静音
                        if (isMuted) {
                            checkAndRestoreMute();
                        }
                    }
                }
        );
    }

    // 检查并恢复静音状态
    private static void checkAndRestoreMute() {
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (!audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
            State.log("检测到音量变化，重新设置静音");
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        }
    }

    private static boolean startAudioUseNormalPermission(Context context, int packetDuration) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            State.log("安卓版本太低，无法录音");
            return false;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // 配置音频捕获参数
            int sampleRate = 48000; // 与您的Opus配置匹配
            int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            int audioEncoding = AudioFormat.ENCODING_PCM_FLOAT;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding) * 2;

            // 计算每个数据包的帧数 (每个通道的样本数)
            // packetDuration 是毫秒，所以需要除以1000转换为秒
            int framesPerPacket = (int) (sampleRate * packetDuration / 1000.0f);
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(audioEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build();
            MediaProjection mediaProjection = State.getMediaProjection();
            if (mediaProjection == null) {
                State.log("没有可用 MediaProjection，跳过系统音频捕获");
                return false;
            }
            AudioRecord audioRecord;
            try {
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .excludeUsage(AudioAttributes.USAGE_ALARM)
                        .build();
                audioRecord = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize)
                        .build();
                audioRecord.startRecording();
            } catch (Throwable e) {
                State.log("系统音频捕获启动失败，继续视频投屏: " + e.getMessage());
                return false;
            }

            // 将 AudioRecord 传递给 SunshineServer 进行处理
            SunshineServer.startAudioRecording(audioRecord, framesPerPacket);
            State.log("Android 原生系统音频捕获已启动");
            return true;

        } else {
            State.log("未授予录音权限，跳过音频捕获并继续视频串流");
            return false;
        }
    }

    private static boolean startOemLoopbackAudioCapture(Context context, int packetDuration) {
        if (!isAndroid81OemLoopback()) {
            return false;
        }
        if (!State.isUserServiceAlive()) {
            State.log("8.1 系统音频捕获需要 Shizuku UserService");
            return false;
        }
        if (!setSmartisanScreenrecordRoute(context, true)) {
            State.log("8.1 Smartisan 音频路由启动失败");
            return false;
        }
        oemScreenrecordRouteEnabled.set(true);
        try {
            if (!State.userService.startRecordingAudio()) {
                State.log("8.1 Smartisan audio_loopback 启动失败");
                stopOemLoopbackAudio();
                return false;
            }
            oemLoopbackRecordingStarted.set(true);
        } catch (Throwable e) {
            State.log("8.1 系统音频启动异常: " + e.getClass().getSimpleName() + " " + e.getMessage());
            stopOemLoopbackAudio();
            return false;
        }

        oemLoopbackStopRequested = false;
        if (!oemLoopbackActive.compareAndSet(false, true)) {
            return true;
        }
        oemLoopbackRecordingStarted.set(true);
        int framesPerPacket = Math.max(1, (int) (48000 * packetDuration / 1000.0f));
        float[] buffer = new float[framesPerPacket * 2];
        Thread thread = new Thread(() -> {
            long lastLevelLogTime = 0;
            while (oemLoopbackActive.get() && !oemLoopbackStopRequested) {
                try {
                    int n = State.userService.readAudio(buffer);
                    if (n > 0) {
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastLevelLogTime >= 2000) {
                            float peak = 0;
                            for (int i = 0; i < n; i++) {
                                peak = Math.max(peak, Math.abs(buffer[i]));
                            }
                            Log.i("ConnectScreen", "8.1 audio_loopback PCM peak=" + peak);
                            lastLevelLogTime = now;
                        }
                        SunshineServer.pushAudioSamples(buffer, n);
                    } else if (n < 0) {
                        State.log("8.1 audio_loopback readAudio 返回 " + n);
                        break;
                    }
                } catch (Throwable e) {
                    State.log("8.1 audio_loopback readAudio 异常: " + e.getClass().getSimpleName() + " " + e.getMessage());
                    break;
                }
            }
            oemLoopbackActive.set(false);
            State.log("8.1 audio_loopback reader 线程结束");
        }, "moonlight-audio-loopback");
        thread.setDaemon(true);
        oemLoopbackThread = thread;
        thread.start();
        State.log("8.1 Smartisan audio_loopback 音频捕获已启动");
        return true;
    }

    private static boolean startRemoteSubmixAudioCapture(int packetDuration) {
        if (!State.isUserServiceAlive()) {
            State.log("8.1 REMOTE_SUBMIX needs a root UserService");
            return false;
        }
        try {
            if (!State.userService.forceTntAudioRoute(true)) {
                State.log("8.1 REMOTE_SUBMIX route setup failed");
                return false;
            }
            if (!State.userService.startRecordingAudio()) {
                State.log("8.1 REMOTE_SUBMIX AudioRecord failed to start");
                State.userService.forceTntAudioRoute(false);
                return false;
            }
        } catch (Throwable e) {
            State.log("8.1 REMOTE_SUBMIX start failed: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }

        oemLoopbackStopRequested = false;
        if (!oemLoopbackActive.compareAndSet(false, true)) {
            return true;
        }
        int framesPerPacket = Math.max(1, (int) (48000 * packetDuration / 1000.0f));
        float[] buffer = new float[framesPerPacket * 2];
        Thread thread = new Thread(() -> {
            while (oemLoopbackActive.get() && !oemLoopbackStopRequested) {
                try {
                    int n = State.userService.readAudio(buffer);
                    if (n > 0) {
                        SunshineServer.pushAudioSamples(buffer, n);
                    } else if (n < 0) {
                        State.log("8.1 REMOTE_SUBMIX readAudio returned " + n);
                        break;
                    }
                } catch (Throwable e) {
                    State.log("8.1 REMOTE_SUBMIX read failed: "
                            + e.getClass().getSimpleName() + " " + e.getMessage());
                    break;
                }
            }
            oemLoopbackActive.set(false);
            State.log("8.1 REMOTE_SUBMIX reader stopped");
        }, "moonlight-audio-submix");
        thread.setDaemon(true);
        oemLoopbackThread = thread;
        thread.start();
        State.log("8.1 REMOTE_SUBMIX capture started");
        return true;
    }

    public static void stopOemLoopbackAudio() {
        if (!isAndroid81OemLoopback()) {
            return;
        }
        boolean recordingStarted = oemLoopbackRecordingStarted.getAndSet(false);
        boolean routeEnabled = oemScreenrecordRouteEnabled.get();
        boolean wasActive = oemLoopbackActive.get() || oemLoopbackThread != null;
        if (!recordingStarted && !routeEnabled && !wasActive) {
            return;
        }
        oemLoopbackStopRequested = true;
        try {
            if (State.userService != null) {
                if (recordingStarted) {
                    State.userService.stopRecordingAudio();
                }
                if (android81Rooted) {
                    State.userService.forceTntAudioRoute(false);
                }
            }
        } catch (Throwable e) {
            State.log("8.1 audio_loopback 停止异常: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        if (oemScreenrecordRouteEnabled.getAndSet(false)) {
            Context context = State.getContext();
            if (context != null && !setSmartisanScreenrecordRoute(context, false)) {
                State.log("8.1 Smartisan 音频路由停止失败");
            }
        }
        Thread thread = oemLoopbackThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        oemLoopbackThread = null;
        oemLoopbackActive.set(false);
        if (wasActive) {
            State.log("8.1 audio_loopback 音频已停止");
        }
    }

    private static boolean setSmartisanScreenrecordRoute(Context context, boolean enabled) {
        if (context == null
                || context.checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            State.log("8.1 Smartisan 音频路由缺少 MODIFY_AUDIO_SETTINGS 权限");
            return false;
        }
        try {
            Class<?> audioSystem = Class.forName("android.media.AudioSystem");
            Method setParameters = audioSystem.getMethod("setParameters", String.class);
            int result = (Integer) setParameters.invoke(null, "screenrecord=" + enabled);
            Log.i("ConnectScreen", "8.1 Smartisan screenrecord=" + enabled + " result=" + result);
            return result == 0;
        } catch (Throwable e) {
            State.log("8.1 Smartisan 音频路由异常: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }
    }

    public static void restoreVolume(Context context) {
        stopOemLoopbackAudio();
        if (isMuted && context != null) {
            State.log("恢复音量");
            isMuted = false;
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);

            // 取消注册音量变化监听器
            if (volumeChangeListener != null) {
                audioManager.abandonAudioFocus(volumeChangeListener);
                volumeChangeListener = null;
            }

        }
    }
}
