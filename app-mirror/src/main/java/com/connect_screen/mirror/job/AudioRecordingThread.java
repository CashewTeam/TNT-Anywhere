package com.connect_screen.mirror.job;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;

import androidx.core.app.ActivityCompat;

import com.connect_screen.mirror.State;

public class AudioRecordingThread extends Thread {
    private float[] buffer;
    private AudioRecord audioRecord;
    private boolean isRecording;

    public AudioRecordingThread(Context context, MediaProjection mediaProjection) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            com.connect_screen.mirror.State.log("缺少录音权限");
            return;
        }
        // 配置音频捕获参数
        int sampleRate = 48000; // 与您的Opus配置匹配
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;
        buffer = new float[bufferSize / 4];  // float 是 4 字节，所以除以 4
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        audioRecord = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .build();
    }

    @Override
    public void run() {
        if (audioRecord == null) {
            return;
        }
        audioRecord.startRecording();
        isRecording = true;

        while (isRecording) {
            android.util.Log.d("AudioRecordingThread", "开始录制音频");
            int readSize = audioRecord.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            if (readSize > 0) {
                // 检查录制到的音频帧是否有实际内容
                boolean hasContent = false;
                float maxAmplitude = 0;
                for (int i = 0; i < readSize; i++) {
                    float abs = Math.abs(buffer[i]);
                    if (abs > 0.01f) {  // 设置一个阈值来判断是否有有效音频
                        hasContent = true;
                    }
                    maxAmplitude = Math.max(maxAmplitude, abs);
                }
                android.util.Log.d("AudioRecordingThread", "录制到了音频: " + readSize + 
                                  ", 有内容: " + hasContent + ", 最大振幅: " + maxAmplitude);
                // 在这里处理音频数据
            } else {
                android.util.Log.e("AudioRecordingThread", "录制音频错误: " + readSize);
            }
        }
    }
    
    public void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }
}
