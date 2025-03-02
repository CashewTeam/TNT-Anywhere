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
import com.connect_screen.mirror.job.SunshineServer;

public class AudioRecordingThread extends Thread {
    private float[] buffer;
    private AudioRecord audioRecord;
    private boolean isRecording;
    private int sampleRate;
    private int framesPerPacket;

    public AudioRecordingThread(Context context, MediaProjection mediaProjection, int packetDuration) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            com.connect_screen.mirror.State.log("缺少录音权限");
            return;
        }
        // 配置音频捕获参数
        sampleRate = 48000; // 与您的Opus配置匹配
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;
        
        // 计算每个数据包的帧数 (每个通道的样本数)
        // packetDuration 是毫秒，所以需要除以1000转换为秒
        framesPerPacket = (int)(sampleRate * packetDuration / 1000.0f);
        // 每帧有2个通道(立体声)，每个通道一个float值
        buffer = new float[framesPerPacket * 2];
        
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
            int readSize = audioRecord.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            if (readSize > 0) {
                // 检查录制到的音频帧是否有实际内容
                boolean hasContent = false;
                float maxAmplitude = 0;
                for (int i = 0; i < readSize; i++) {
                    float abs = Math.abs(buffer[i]);  // 浮点值已经在[-1,1]范围内，不需要归一化
                    if (abs > 0.01f) {  // 设置一个阈值来判断是否有有效音频
                        hasContent = true;
                    }
                    maxAmplitude = Math.max(maxAmplitude, abs);
                }
                // 将音频数据发送到 SunshineServer
                if (hasContent) {
                    android.util.Log.d("AudioRecordingThread", "录制到了音频: " + readSize +
                            ", 有内容: " + hasContent + ", 最大振幅: " + maxAmplitude);
                    SunshineServer.postAudioSample(buffer, readSize);
                }
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
