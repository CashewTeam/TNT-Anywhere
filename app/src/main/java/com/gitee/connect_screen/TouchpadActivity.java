package com.gitee.connect_screen;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;

import android.app.ActivityOptions;
import android.app.IActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.KeyEventHidden;
import android.view.MotionEvent;
import android.view.MotionEventHidden;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;

import dev.rikka.tools.refine.Refine;

public class TouchpadActivity extends AppCompatActivity {
    
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2;
    private View touchpadArea;
    private ImageView cursorView;
    private int displayId;
    private static final String TAG = "TouchpadActivity";
    private float cursorX = 0;
    private float cursorY = 0;
    private WindowManager.LayoutParams cursorParams;
    private static final long CLICK_TIME_THRESHOLD = 200; // 毫秒
    private float halfWidth;
    private float halfHeight;
    private TouchpadAccessibilityService accessibilityService;
    private ImageView darkOverlayImage;
    private boolean isDarkMode = false;
    private GestureDetector gestureDetector;
    private IInputManager inputManager;
    private GestureState gestureState = new GestureState();

    private static class GestureState {
        boolean isGestureInProgress = false;
        boolean isMoveGesture = false;
        List<MotionEvent> allMotionEvents = new ArrayList<>();
        int lastReplayed = 0;
        float initialTouchX = 0;
        float initialTouchY = 0;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        Window window = getWindow();
        window.setDecorFitsSystemWindows(false);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }
        
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        
        // 4. 最后设置内容视图
        setContentView(R.layout.activity_touchpad);
        
        // 获取目标显示器ID
        displayId = getIntent().getIntExtra("display_id", Display.DEFAULT_DISPLAY);
        
        if (ShizukuUtils.hasPermission()) {
            inputManager = ServiceUtils.getInputManager();
        }
        // 计算屏幕尺寸
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display targetDisplay = displayManager.getDisplay(displayId);
        android.graphics.Point size = new android.graphics.Point();
        targetDisplay.getSize(size);

        // 计算屏幕边界（以屏幕中心为原点）
        halfWidth = size.x / 2.0f;
        halfHeight = size.y / 2.0f;

        // 显示光标
        showMouseCursor();

        touchpadArea = findViewById(R.id.touchpad_area);
        
        // 初始化手势检测器
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (inputManager == null) {
                    Log.d(TAG, "检测到单击手势");
                    performClick();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e2.getPointerCount() == 1) {
                    // 单指移动光标
                    gestureState.isMoveGesture = true;
                    Log.d(TAG, "移动光标 - 偏移量: (" + (-distanceX) + ", " + (-distanceY) + ")");
                    updateCursorPosition(-distanceX, -distanceY);
                    return true;
                }
                if (inputManager != null) {
                    return false;
                }
                if (e2.getPointerCount() == 2) {
                    // 双指滚动，直接传递增量值
                    if (Math.abs(distanceY) > 5) {
                        accessibilityService.performScroll(
                            displayId,
                            cursorX + halfWidth,  // 当前光标X坐标
                            -distanceY * 2        // Y方向的增量
                        );
                        return true;
                    } else {
                        return false;
                    }
                }
                return false;
            }
        });

        // 替换触控板的触摸事件监听
        touchpadArea.setOnTouchListener((v, event) -> {
            gestureState.isGestureInProgress = true;
            gestureState.allMotionEvents.add(event);
            boolean handled = gestureDetector.onTouchEvent(event);
            if (!gestureState.isMoveGesture && gestureState.allMotionEvents.size() > 2) {
                replayBufferedEvents();
            }
            
            // 处理手势结束
            if (event.getAction() == MotionEvent.ACTION_UP || 
                event.getAction() == MotionEvent.ACTION_CANCEL) {
                Log.d(TAG, "触摸事件结束");
                if (accessibilityService != null) {
                    accessibilityService.cancelScroll();
                }
                if (!gestureState.isMoveGesture) {
                    replayBufferedEvents();
                }
                gestureState.lastReplayed = 0;
                gestureState.isGestureInProgress = false;
                gestureState.isMoveGesture = false;
                gestureState.allMotionEvents.clear();
            }
            return handled;
        });
        
        accessibilityService = TouchpadAccessibilityService.getInstance();
        
        darkOverlayImage = findViewById(R.id.darkOverlayImage);
        darkOverlayImage.setOnClickListener(v -> toggleDarkMode());
        
        // 修改暗色模式按钮点击事件
        ImageButton goDarkButton = findViewById(R.id.goDarkButton);
        goDarkButton.setOnClickListener(v -> toggleDarkMode());
        
        // 添加返回按钮的点击监听器
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (inputManager != null) {
                injectKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0, INJECT_INPUT_EVENT_MODE_ASYNC);
                injectKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, INJECT_INPUT_EVENT_MODE_ASYNC);
                return;
            }
            performBackGesture();
        });

        // 添加Home按钮的点击监听器
        ImageButton homeButton = findViewById(R.id.homeButton);
        homeButton.setOnClickListener(v -> {
            if (State.lastPackageName == null) {
                return;
            }
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(State.lastPackageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(displayId);
                v.getContext().startActivity(launchIntent, options.toBundle());
            }
        });

        // 添加退出按钮的点击监听器
        findViewById(R.id.exitButton).setOnClickListener(v -> {
            finish(); // 结束当前Activity，返回上一级
        });

        // 添加帮助按钮的点击监听器
        findViewById(R.id.helpButton).setOnClickListener(v -> {
            showHelpDialog();
        });
    }

    private void replayBufferedEvents() {
        if (inputManager == null) {
            return;
        }
        if (gestureState.allMotionEvents.isEmpty()) {
            Log.d(TAG, "没有需要重放的事件");
            return;
        }

        // 获取初始触摸事件的坐标
        MotionEvent firstEvent = gestureState.allMotionEvents.get(0);
        if (gestureState.lastReplayed == 0) {
            gestureState.initialTouchX = firstEvent.getX();
            gestureState.initialTouchY = firstEvent.getY();
            Log.d(TAG, "初始触摸点坐标: (" + gestureState.initialTouchX + ", " + gestureState.initialTouchY + ")");
        }

        // 从上次重放的位置开始，重放所有未处理的事件
        for (int i = gestureState.lastReplayed; i < gestureState.allMotionEvents.size(); i++) {
            MotionEvent event = gestureState.allMotionEvents.get(i);
            
            // 计算相对于初始触摸点的偏移
            float relativeX = event.getX() - gestureState.initialTouchX;
            float relativeY = event.getY() - gestureState.initialTouchY;

            float absoluteX = cursorX + halfWidth + relativeX;
            float absoluteY = cursorY + halfHeight + relativeY;

            float offsetX = absoluteX - event.getX();
            float offsetY = absoluteY - event.getY();
            
            // 将相对偏移应用到光标位置
            MotionEvent copiedEventWithOffset = obtainMotionEventWithOffset(event,
                offsetX,
                offsetY
            );
            
            Log.d(TAG, String.format(
                "重放事件 #%d [显示ID=%d]: " +
                "初始触摸点=(%.2f, %.2f), 当前事件点=(%.2f, %.2f), " +
                "相对偏移=(%.2f, %.2f), 绝对位置=(%.2f, %.2f), 最终偏移=(%.2f, %.2f), " +
                "复制后坐标=(%.2f, %.2f)", 
                i, displayId,
                gestureState.initialTouchX, gestureState.initialTouchY,
                event.getX(), event.getY(),
                relativeX, relativeY, 
                absoluteX, absoluteY, 
                offsetX, offsetY,
                copiedEventWithOffset.getX(), copiedEventWithOffset.getY()));
            
            
            MotionEventHidden eventHidden = Refine.unsafeCast(copiedEventWithOffset);
            eventHidden.setDisplayId(displayId);
            IActivityTaskManager activityTaskManager = ServiceUtils.getActivityTaskManager();
            activityTaskManager.focusTopTask(displayId);
            boolean success = inputManager.injectInputEvent(copiedEventWithOffset, INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT);
            if (!success) {
                Log.e(TAG, "重放事件失败");
            }
        }
        
        gestureState.lastReplayed = gestureState.allMotionEvents.size();
    }

    private void injectKeyEvent(int action, int keyCode, int repeat, int metaState, int injectMode) {
        IActivityTaskManager activityTaskManager = ServiceUtils.getActivityTaskManager();
        activityTaskManager.focusTopTask(displayId);
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        KeyEventHidden eventHidden = Refine.unsafeCast(event);
        eventHidden.setDisplayId(displayId);
        inputManager.injectInputEvent(event, injectMode);
    }

    private void showMouseCursor() {
        cursorParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        
        cursorParams.x = 0;
        cursorParams.y = 0;
        
        cursorView = new ImageView(this);
        cursorView.setImageResource(R.drawable.mouse_cursor);
        
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display targetDisplay = displayManager.getDisplay(displayId);
        
        Context displayContext = createDisplayContext(targetDisplay);
        WindowManager windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        
        try {
            windowManager.addView(cursorView, cursorParams);
        } catch (Exception e) {
            Toast.makeText(this, "显示鼠标光标失败", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "显示鼠标光标失败: " + e.getMessage());
        }
    }

    // 添加更新光标位置的方法
    private void updateCursorPosition(float deltaX, float deltaY) {
        cursorX += deltaX * 1.5f;
        cursorY += deltaY * 1.5f;
        
        // 检查并记录是否超出边界
        if (cursorX < -halfWidth || cursorX > halfWidth || 
            cursorY < -halfHeight || cursorY > halfHeight) {
            Log.w(TAG, "光标位置超出边界 - 原始位置: (" + cursorX + ", " + cursorY + ")");
        }
        
        // 确保光标不会超出屏幕边界
        cursorX = Math.max(-halfWidth, Math.min(cursorX, halfWidth));
        cursorY = Math.max(-halfHeight, Math.min(cursorY, halfHeight));
        
        if (cursorView != null && cursorParams != null) {
            cursorParams.x = (int) cursorX;
            cursorParams.y = (int) cursorY;
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            try {
                windowManager.updateViewLayout(cursorView, cursorParams);
            } catch (Exception e) {
                Log.e(TAG, "更新光标位置失败: " + e.getMessage());
            }
        }
    }

    // 添加执行点击的方法
    private void performClick() {
        accessibilityService = TouchpadAccessibilityService.getInstance();
        if (accessibilityService != null) {
            float x = cursorX + halfWidth;
            float y = cursorY + halfHeight; 
            Log.d(TAG, "执行点击操作 - 光标位置: (" + x + ", " + y + ")");
            accessibilityService.performClick(displayId, x, y);
        } else {
            Log.e(TAG, "无法执行点击操作 - AccessibilityService 未连接");
        }
    }

    // 添加执行返回手势的方法
    private void performBackGesture() {
        accessibilityService.performBackGesture(displayId);
    }

    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        darkOverlayImage.setVisibility(isDarkMode ? View.VISIBLE : View.GONE);
        if (cursorView != null) {
            cursorView.setVisibility(isDarkMode ? View.GONE : View.VISIBLE);
        }
        accessibilityService.setFocus(displayId);
    }

    // 添加显示帮助对话框的方法
    private void showHelpDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("触控板使用帮助")
            .setMessage(
                "• 单指轻触 - 点击\n" +
                "• 单指滑动 - 移动光标\n" +
                "• 双指滑动 - 滚动页面\n" +
                "• 返回按钮 - 在目标屏幕上按返回\n" +
                "• 主页按钮 - 再次打开列表中选择的应用\n" +
                "• 暗色按钮 - 纯黑色屏幕省电\n" +
                "• 退出按钮 - 退出触控板"
            )
            .setPositiveButton("知道了", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除光标视图
        if (cursorView != null && cursorView.getWindowToken() != null) {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.removeView(cursorView);
        }
    }

    // 添加新的辅助方法
    private MotionEvent obtainMotionEventWithOffset(MotionEvent source, float offsetX, float offsetY) {
        int pointerCount = source.getPointerCount();
        
        // 准备所有触点的属性和坐标数组
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        
        // 复制每个触点的信息
        for (int i = 0; i < pointerCount; i++) {
            // 复制触点属性
            properties[i] = new MotionEvent.PointerProperties();
            source.getPointerProperties(i, properties[i]);
            
            // 复制并修改触点坐标
            coords[i] = new MotionEvent.PointerCoords();
            source.getPointerCoords(i, coords[i]);
            coords[i].x += offsetX;
            coords[i].y += offsetY;
        }
        
        // 创建新的MotionEvent
        int DEFAULT_DEVICE_ID = 0;
        return MotionEvent.obtain(
            source.getDownTime(),
            source.getEventTime(),
            source.getAction(),
            pointerCount,
            properties,
            coords,
            source.getMetaState(),
            source.getButtonState(),
            source.getXPrecision(),
            source.getYPrecision(),
            DEFAULT_DEVICE_ID,
            source.getEdgeFlags(),
            source.getSource(),
            source.getFlags()
        );
    }
}