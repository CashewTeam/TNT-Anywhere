package com.connect_screen.mirror.job;

import static com.connect_screen.mirror.MirrorMainActivity.REQUEST_RECORD_AUDIO_PERMISSION;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.os.RemoteException;
import android.view.Surface;


import androidx.core.app.ActivityCompat;

import com.connect_screen.mirror.FloatingButtonService;
import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ServiceUtils;
import com.connect_screen.mirror.shizuku.ShizukuUtils;


public class ProjectViaMoonlight implements Job {
    private final int width;
    private final int height;
    private final int frameRate;
    private final int packetDuration;
    private final Surface surface;
    private final boolean shouldSendAudio;
    private boolean mediaProjectionRequested;
    private boolean audioPermissionRequested;

    public ProjectViaMoonlight(int width, int height, int frameRate, int packetDuration, Surface surface, boolean shouldSendAudio) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.packetDuration = packetDuration;
        this.surface = surface;
        this.shouldSendAudio = shouldSendAudio;
    }

    @Override
    public void start() throws YieldException {
        if (!requestMediaProjectionPermission()) {
            return;
        }
        Context context = State.getContext();
        if (context == null) {
            return;
        }
        // 创建AudioRecord
        if (shouldSendAudio) {
            if (State.userService != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                int framesPerPacket = (int) (48000 * packetDuration / 1000.0f);
                AudioRecordProxy audioRecordProxy = new AudioRecordProxy();
                if (!startRecording()) {
                    State.log("启动录音失败");
                    return;
                }
                SunshineServer.startAudioRecording(audioRecordProxy, framesPerPacket);
            } else if (sendAudioUseNormalPermission(context)) {
                return;
            }
        }
        boolean autoRotate = Pref.getAutoRotate();
        boolean autoScale = Pref.getAutoScale();
        boolean singleAppMode = Pref.getSingleAppMode();
        if (singleAppMode) {
            if (ShizukuUtils.hasPermission()) {
                int singleAppDpi = Pref.getSingleAppDpi();
                State.mirrorVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(new VirtualDisplayArgs("Moonlight",
                        width, height, frameRate, singleAppDpi, autoRotate), surface);
                String selectedAppPackage = Pref.getSelectedAppPackage();
                ServiceUtils.launchPackage(context, selectedAppPackage, State.mirrorVirtualDisplay.getDisplay().getDisplayId());
                InputRouting.bindAllExternalInputToDisplay(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
                InputRouting.moveImeToExternal(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            } else {
                State.showErrorStatus("moonlight 单应用投屏需要 shizuku 权限");
            }
        } else if (autoRotate || autoScale) {
            SunshineMouse.autoRotateAndScaleForMoonlight = new AutoRotateAndScaleForMoonlight(new VirtualDisplayArgs("Moonlight",
                    width, height, frameRate, 160, false));
            SunshineMouse.autoRotateAndScaleForMoonlight.start(surface);
            CreateVirtualDisplay.powerOffScreen();
        } else {
            State.mirrorVirtualDisplay = State.getMediaProjection().createVirtualDisplay("Moonlight",
                    width,
                    height,
                    160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null);
            State.setMediaProjection(null);
            FloatingButtonService.startForMirror();
            CreateVirtualDisplay.powerOffScreen();
        }
    }

    private boolean startRecording() {
        try {
            return State.userService.startRecordingAudio();
        } catch (RemoteException e) {
            return false;
        }
    }

    private boolean sendAudioUseNormalPermission(Context context) throws YieldException {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            State.log("安卓版本太低，无法录音");
            return false;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(State.getMediaProjection())
                    .excludeUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            AudioRecord audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            audioRecord.startRecording();

            // 将 AudioRecord 传递给 SunshineServer 进行处理
            SunshineServer.startAudioRecording(audioRecord, framesPerPacket);

        } else {
            if (audioPermissionRequested) {
                State.log("因为未授予录音权限，跳过任务");
                return true;
            }
            audioPermissionRequested = true;
            MirrorMainActivity activity = State.currentActivity.get();
            if (activity == null) {
                return true;
            }
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            throw new YieldException("等待录音权限授权");
        }
        return false;
    }

    private boolean requestMediaProjectionPermission() throws YieldException {
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        mediaProjectionRequested = true;
        MirrorMainActivity mirrorMainActivity = State.currentActivity.get();
        if (mirrorMainActivity == null) {
            return false;
        }
        mirrorMainActivity.startMediaProjectionService();
        throw new YieldException("等待用户投屏授权");
    }
}
