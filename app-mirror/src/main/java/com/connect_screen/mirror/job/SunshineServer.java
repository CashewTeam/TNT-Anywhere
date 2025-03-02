package com.connect_screen.mirror.job;

import android.app.Activity;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.Touch;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.widget.EditText;
import android.widget.Toast;
import android.media.AudioRecord;

import com.connect_screen.mirror.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 代码拷贝自 v2025.122.141614
public class SunshineServer {
    public static AutoRotateAndScaleForMoonlight autoRotateAndScaleForMoonlight;

    static {
        System.loadLibrary("sunshine");
    }

    public static native void start();

    public static native void setSunshineName(String sunshineName);
    public static native void setPkeyPath(String path);
    public static native void setCertPath(String path);
    public static native void setFileStatePath(String path);
    
    // 添加新的回调方法，当需要 PIN 码时被 C++ 代码调用
    public static void onPinRequested() {
        // 使用 Handler 将回调切换到主线程
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.currentActivity.get();
            if (context == null) {
                return;
            }
            
            // 创建一个输入框
            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            // 限制输入长度为4位
            InputFilter[] filters = new InputFilter[1];
            filters[0] = new InputFilter.LengthFilter(4);
            input.setFilters(filters);
            
            // 创建对话框
             AlertDialog.Builder builder = new AlertDialog.Builder(context);
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
//            submitPin("1234");
        });
    }
    
    // 添加提交PIN码的native方法
    public static native void submitPin(String pin);
    
    // 添加发送音频样本的方法
    public static native void postAudioSample(float[] audioData, int sampleCount);

    
    // surface created by MediaCodec
    public static void createVirtualDisplay(int width, int height, int frameRate, int packetDuration, Surface surface) {
        new Handler(Looper.getMainLooper()).post(() -> {
            State.startNewJob(new ProjectViaMoonlight(width, height, frameRate, packetDuration, surface));
        });
    }

    public static void stopVirtualDisplay() {
        new Handler(Looper.getMainLooper()).post(() -> {
            State.log("停止 Moonlight 投屏");
            if (autoRotateAndScaleForMoonlight != null) {
                autoRotateAndScaleForMoonlight.stop();
                autoRotateAndScaleForMoonlight = null;
            }
            if (State.mirrorVirtualDisplay != null) {
                State.mirrorVirtualDisplay.release();
                State.mirrorVirtualDisplay = null;
                ExitAll.execute(State.currentActivity.get(), true);
            }
        });
    }

    // 添加新方法用于启动音频录制
    public static native void startAudioRecording(AudioRecord audioRecord, int framesPerPacket);

    private static class TouchPacket {
        public int action;
        public int pointerId;
        public float x;
        public float y;
    }

    private static List<TouchPacket> bufferedPackets = new ArrayList<>();

    // 添加处理触摸事件的静态方法
    public static void handleTouchPacket(int eventType, int rotation, int pointerId, 
                                        float x, float y, float pressureOrDistance,
                                        float contactAreaMajor, float contactAreaMinor) {
        int motionEventAction;
        // 将 Sunshine/Moonlight 的触摸事件类型转换为 Android 的 MotionEvent 类型
        switch (eventType) {
            case 0x00: // LI_TOUCH_EVENT_HOVER
                motionEventAction = MotionEvent.ACTION_HOVER_MOVE;
                break;
            case 0x01: // LI_TOUCH_EVENT_DOWN
                motionEventAction = MotionEvent.ACTION_DOWN;
                break;
            case 0x02: // LI_TOUCH_EVENT_UP
                motionEventAction = MotionEvent.ACTION_UP;
                break;
            case 0x03: // LI_TOUCH_EVENT_MOVE
                motionEventAction = MotionEvent.ACTION_MOVE;
                break;
            case 0x04: // LI_TOUCH_EVENT_CANCEL
                motionEventAction = MotionEvent.ACTION_CANCEL;
                break;
            case 0x05: // LI_TOUCH_EVENT_BUTTON_ONLY
                motionEventAction = MotionEvent.ACTION_BUTTON_PRESS; // 或 ACTION_BUTTON_RELEASE，可能需要额外信息区分
                break;
            case 0x06: // LI_TOUCH_EVENT_HOVER_LEAVE
                motionEventAction = MotionEvent.ACTION_HOVER_EXIT;
                break;
            case 0x07: // LI_TOUCH_EVENT_CANCEL_ALL
                motionEventAction = MotionEvent.ACTION_CANCEL;
                break;
            default:
                android.util.Log.e("SunshineServer", "未知的触摸事件类型: " + eventType);
                return;
        }
        TouchPacket packet = new TouchPacket();
        packet.action = motionEventAction;
        packet.pointerId = pointerId;
        packet.x = x;
        packet.y = y;
        if (motionEventAction != MotionEvent.ACTION_MOVE) {
            if (!bufferedPackets.isEmpty()) {
                triggerTouch(bufferedPackets);
                bufferedPackets.clear();
            }
            triggerTouch(Arrays.asList(packet));
            return;
        }
        if (bufferedPackets.isEmpty()) {
            bufferedPackets.add(packet);
            return;
        }
        if (bufferedPackets.get(0).action != motionEventAction) {
            triggerTouch(bufferedPackets);
            bufferedPackets.clear();
            bufferedPackets.add(packet);
            return;
        }
        boolean found = false;
        for(TouchPacket bufferedPacket : bufferedPackets) {
            if (bufferedPacket.pointerId == pointerId) {
                found = true;
                break;
            }
        }
        if (found) {
            triggerTouch(bufferedPackets);
            bufferedPackets.clear();
            bufferedPackets.add(packet);
            return;
        }
        bufferedPackets.add(packet);

        // 这里可以添加实际处理触摸事件的代码
        // 例如：创建 MotionEvent 并分发到当前活动窗口
    }

    private static void triggerTouch(List<TouchPacket> bufferedPackets) {
        android.util.Log.d("SunshineServer", "触摸事件: 类型=" + bufferedPackets.get(0).action + ", count=" + bufferedPackets.size());
    }

}
