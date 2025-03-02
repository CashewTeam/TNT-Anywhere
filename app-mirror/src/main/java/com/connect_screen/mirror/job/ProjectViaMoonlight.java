package com.connect_screen.mirror.job;

import static com.connect_screen.mirror.MirrorMainActivity.REQUEST_RECORD_AUDIO_PERMISSION;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import com.connect_screen.mirror.State;


public class ProjectViaMoonlight implements Job {
    private final int width;
    private final int height;
    private final int frameRate;
    private final Surface surface;
    private boolean mediaProjectionRequested;
    private boolean audioPermissionRequested;

    public ProjectViaMoonlight(int width, int height, int frameRate, Surface surface) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
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
                new AudioRecordingThread(State.currentActivity.get(), State.getMediaProjection()).start();
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
        // SunshineServer.autoRotateAndScaleForMoonlight = new AutoRotateAndScaleForMoonlight(new VirtualDisplayArgs("ScreenCapture",
        //         width, height, frameRate, 160, false));
        // SunshineServer.autoRotateAndScaleForMoonlight.start(surface);
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
        MediaProjectionService.isStarting = true;
        mediaProjectionRequested = true;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            State.currentActivity.get().startActivityForResult(captureIntent, MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            throw new YieldException("等待用户投屏授权");
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }
}
