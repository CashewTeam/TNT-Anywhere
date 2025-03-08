package com.connect_screen.mirror.job;

import android.hardware.input.IInputManager;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.widget.EditText;
import android.widget.Toast;
import android.media.AudioRecord;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ServiceUtils;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.rikka.tools.refine.Refine;

// 代码拷贝自 v2025.122.141614
public class SunshineServer {
    public static AutoRotateAndScaleForMoonlight autoRotateAndScaleForMoonlight;
    private static IInputManager inputManager;
    private static int screenWidth;
    private static int screenHeight;

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
        if (ShizukuUtils.hasPermission()) {
            inputManager = ServiceUtils.getInputManager();
            screenWidth = width;
            screenHeight = height;
        }
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

    public static native void enableH265();

    private static class PointerStatus {
        public float x = 0;
        public float y = 0;
    }

    private static Map<Integer, PointerStatus> pointers = new HashMap<>();

    // 添加处理触摸事件的静态方法
    public static void handleTouchPacket(int eventType, int rotation, int pointerId, 
                                        float x, float y, float pressureOrDistance,
                                        float contactAreaMajor, float contactAreaMinor) {
        if (inputManager == null) {
            return;
        }
        int displayRotation = State.mirrorVirtualDisplay.getDisplay().getRotation();
        // 根据屏幕旋转调整坐标
        float adjustedX = x * screenWidth;
        float adjustedY = y * screenHeight;
        switch (displayRotation) {
            case Surface.ROTATION_90:
                adjustedX = y * screenHeight;
                adjustedY = (1 - x) * screenWidth;
                break;
            case Surface.ROTATION_180:
                adjustedX = (1 - x) * screenWidth;
                adjustedY = (1 - y) * screenHeight;
                break;
            case Surface.ROTATION_270:
                adjustedX = (1 - y) * screenHeight;
                adjustedY = x * screenWidth;
                break;
        }
        pointerId = pointerId % 10;
        switch (eventType) {
            case 0x01: // LI_TOUCH_EVENT_DOWN
                handleTouchEventDown(pointerId, adjustedX, adjustedY);
                break;
            case 0x02: // LI_TOUCH_EVENT_UP
                handleTouchEventUp(pointerId, adjustedX, adjustedY, false);
                break;
            case 0x03: // LI_TOUCH_EVENT_MOVE
                handleTouchEventMove(pointerId, adjustedX, adjustedY);
                break;
            case 0x04: // LI_TOUCH_EVENT_CANCEL
                handleTouchEventUp(pointerId, adjustedX, adjustedY, true);
                break;
            case 0x07: // LI_TOUCH_EVENT_CANCEL_ALL
                handleTouchEventCancelAll();
                break;
            default:
                android.util.Log.e("SunshineServer", "未知的触摸事件类型: " + eventType);
        }
    }

    private static void handleTouchEventDown(int pointerId, float x, float y) {
        if (!bufferedMove.isEmpty()) {
            bufferedMove.clear();
            triggerTouchEventMove();
        }
        if (!pointers.containsKey(pointerId)) {
            PointerStatus pointerStatus = new PointerStatus();
            pointerStatus.x = x;
            pointerStatus.y = y;
            pointers.put(pointerId, pointerStatus);
        }

        int action = pointers.size() == 1 ? android.view.MotionEvent.ACTION_DOWN :
                                    android.view.MotionEvent.ACTION_POINTER_DOWN | (pointerId << 8);
        // 构造 MotionEvent
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];
        
        int index = 0;
        for (Integer k : pointers.keySet()) {
            PointerStatus status = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;  // 保持id为原始的pointerId
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = status.x;
            coords[index].y = status.y;
            coords[index].pressure = 1.0f;
            index++;
        }
        
        // 构造 MotionEvent
        MotionEvent event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointers.size(), // 使用实际的触摸点数量
            properties,
            coords,
            0, // metaState
            0, // buttonState
            1.0f, // xPrecision
            1.0f, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_TOUCHSCREEN,
            0 // flags
        );
        if (State.mirrorVirtualDisplay != null) {
            MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
            motionEventHidden.setDisplayId(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            inputManager.injectInputEvent(event, 0);
            android.util.Log.d("SunshineServer", "inject down: " + event);
        }
    }

    private static void handleTouchEventUp(int pointerId, float x, float y, boolean cancelled) {
        PointerStatus status = pointers.get(pointerId);
        if(status == null) {
            return;
        }
        if (!bufferedMove.isEmpty()) {
            bufferedMove.clear();
            triggerTouchEventMove();
        }
        status.x = x;
        status.y = y;

        // 确定动作类型
        int action = pointers.size() == 1 ? android.view.MotionEvent.ACTION_UP :
                android.view.MotionEvent.ACTION_POINTER_UP | (pointerId << 8);

        // 构造 MotionEvent
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        // 创建包含所有活跃触摸点的属性数组
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];

        int index = 0;
        for (Integer k : pointers.keySet()) {
            PointerStatus ps = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;  // 保持id为原始的pointerId
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = ps.x;
            coords[index].y = ps.y;
            coords[index].pressure = k == pointerId ? 0.0f : 1.0f;
            index++;
        }

        // 构造并注入 MotionEvent
        MotionEvent event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointers.size(), // 使用实际的触摸点数量
            properties,
            coords,
            0, // metaState
            0, // buttonState
            1.0f, // xPrecision
            1.0f, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_TOUCHSCREEN,
            cancelled ? MotionEvent.FLAG_CANCELED : 0 // flags
        );

        pointers.remove(pointerId);

        if (State.mirrorVirtualDisplay != null) {
            MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
            motionEventHidden.setDisplayId(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            inputManager.injectInputEvent(event, 0);
            android.util.Log.d("SunshineServer", "inject up: " + event);
        }
    }

    private static Set<Integer> bufferedMove = new HashSet<>();

    private static void handleTouchEventMove(int pointerId, float x, float y) {
        PointerStatus status = pointers.get(pointerId);
        if (status == null) {
            return;
        }

        if (bufferedMove.contains(pointerId)) {
            bufferedMove.clear();
            triggerTouchEventMove();
        } else {
            bufferedMove.add(pointerId);
        }
        
        // 更新指针位置
        status.x = x;
        status.y = y;
    }

    private static void handleTouchEventCancelAll() {
        // 取消所有触摸事件
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];
        
        int index = 0;
        for (Integer k : pointers.keySet()) {
            PointerStatus status = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = status.x;
            coords[index].y = status.y;
            coords[index].pressure = 1.0f;
            index++;
        }
        
        MotionEvent event = MotionEvent.obtain(
            downTime,
            eventTime,
            android.view.MotionEvent.ACTION_CANCEL,
            pointers.size(),
            properties,
            coords,
            0, 0, 1.0f, 1.0f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        );
        pointers.clear();
        
        if (State.mirrorVirtualDisplay != null) {
            MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
            motionEventHidden.setDisplayId(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            inputManager.injectInputEvent(event, 0);
            android.util.Log.d("SunshineServer", "inject cancel: " + event);
        }
    }

    private static void triggerTouchEventMove() {
        if (!bufferedMove.isEmpty()) {
            bufferedMove.clear();
            triggerTouchEventMove();
        }
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.size()];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers.size()];
        
        int index = 0;
        for (Integer k : pointers.keySet()) {
            PointerStatus status = pointers.get(k);
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = k;
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;

            coords[index] = new MotionEvent.PointerCoords();
            coords[index].x = status.x;
            coords[index].y = status.y;
            coords[index].pressure = 1.0f;
            index++;
        }
        
        MotionEvent event = MotionEvent.obtain(
            downTime,
            eventTime,
            android.view.MotionEvent.ACTION_MOVE,
            pointers.size(),
            properties,
            coords,
            0, 0, 1.0f, 1.0f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        );
        
        if (State.mirrorVirtualDisplay != null) {
            MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
            motionEventHidden.setDisplayId(State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            inputManager.injectInputEvent(event, 0);
            android.util.Log.d("SunshineServer", "inject move: " + event);
        }

    }

    // 添加显示编码器错误的方法
    public static void showEncoderError(String errorMessage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.currentActivity.get();
            if (context == null) {
                return;
            }
            
            new AlertDialog.Builder(context)
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

}
