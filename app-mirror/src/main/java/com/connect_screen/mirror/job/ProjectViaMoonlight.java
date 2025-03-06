package com.connect_screen.mirror.job;

import static com.connect_screen.mirror.MirrorMainActivity.REQUEST_RECORD_AUDIO_PERMISSION;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.view.Surface;


import androidx.core.app.ActivityCompat;

import com.connect_screen.mirror.MediaProjectionService;
import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.MirrorSettingsFragment;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.TouchpadAccessibilityService;
import com.connect_screen.mirror.shizuku.ServiceUtils;
import com.connect_screen.mirror.shizuku.ShizukuUtils;


public class ProjectViaMoonlight implements Job {
    private final int width;
    private final int height;
    private final int frameRate;
    private final int packetDuration;
    private final Surface surface;
    private boolean mediaProjectionRequested;
    private boolean audioPermissionRequested;

    public ProjectViaMoonlight(int width, int height, int frameRate, int packetDuration, Surface surface) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.packetDuration = packetDuration;
        this.surface = surface;
    }

    @Override
    public void start() throws YieldException {
        if (!requestMediaProjectionPermission(State.currentActivity.get())) {
            return;
        }
        // 创建AudioRecord
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(State.currentActivity.get(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                // 配置音频捕获参数
                int sampleRate = 48000; // 与您的Opus配置匹配
                int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
                int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;

                // 计算每个数据包的帧数 (每个通道的样本数)
                // packetDuration 是毫秒，所以需要除以1000转换为秒
                int framesPerPacket = (int) (sampleRate * packetDuration / 1000.0f);
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(State.getMediaProjection())
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();
                AudioRecord audioRecord = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .build();
                audioRecord.startRecording();
                
                // 将 AudioRecord 传递给 SunshineServer 进行处理
                SunshineServer.startAudioRecording(audioRecord, framesPerPacket);

            } else {
                if (audioPermissionRequested) {
                    State.log("因为未授予录音权限，跳过任务");
                    return;
                }
                audioPermissionRequested = true;
                ActivityCompat.requestPermissions(State.currentActivity.get(),
                        new String[]{Manifest.permission.RECORD_AUDIO}, 
                        REQUEST_RECORD_AUDIO_PERMISSION);
                throw new YieldException("等待录音权限授权");
            }
        }
        SharedPreferences preferences = MediaProjectionService.instance.getSharedPreferences(MirrorSettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        boolean autoRotate = preferences.getBoolean(MirrorSettingsFragment.KEY_AUTO_ROTATE, true);
        boolean autoScale = preferences.getBoolean(MirrorSettingsFragment.KEY_AUTO_SCALE, true);
        boolean singleAppMode = preferences.getBoolean(MirrorSettingsFragment.KEY_SINGLE_APP_MODE, false);
        if (ShizukuUtils.hasPermission() && singleAppMode) {
            int singleAppDpi = preferences.getInt(MirrorSettingsFragment.KEY_SINGLE_APP_DPI, 160);
            State.mirrorVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(new VirtualDisplayArgs("Moonlight",
                    width, height, frameRate, singleAppDpi, autoRotate), surface);
            String selectedAppPackage = preferences.getString(MirrorSettingsFragment.KEY_SELECTED_APP_PACKAGE, "");
            ServiceUtils.launchPackage(MediaProjectionService.instance, selectedAppPackage, State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            InputRouting.bindAllExternalInputToDisplay(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
        } else if (autoRotate || autoScale) {
            SunshineServer.autoRotateAndScaleForMoonlight = new AutoRotateAndScaleForMoonlight(new VirtualDisplayArgs("Moonlight",
                    width, height, frameRate, 160, false));
            SunshineServer.autoRotateAndScaleForMoonlight.start(surface);
        } else {
            State.mirrorVirtualDisplay = State.getMediaProjection().createVirtualDisplay("Moonlight",
                    width,
                    height,
                    160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null);
            State.setMediaProjection(null);
        }
    }

    private boolean requestMediaProjectionPermission(Context context) throws YieldException {
        if (State.mirrorVirtualDisplay != null) {
            return true;
        }
        if (State.getMediaProjection() != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            if (MediaProjectionService.isStarting && MediaProjectionService.instance == null) {
                throw new YieldException("等待服务启动");
            }
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        mediaProjectionRequested = true;
        State.currentActivity.get().startMediaProjectionService();
        throw new YieldException("等待用户投屏授权");
    }
}
