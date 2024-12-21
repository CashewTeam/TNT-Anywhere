package com.gitee.connect_screen;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.input.IInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.job.StartTouchPad;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.rikka.tools.refine.Refine;

public class TouchpadActivity extends AppCompatActivity {
    
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2;
    private TextView touchpadArea;
    private ImageView cursorView;
    private int displayId;
    private static final String TAG = "TouchpadActivity";
    private float cursorX = 0;
    private float cursorY = 0;
    private WindowManager.LayoutParams cursorParams;
    private static final long CLICK_TIME_THRESHOLD = 200; // 毫秒
    private float halfWidth;
    private float halfHeight;
    private ImageView darkOverlayImage;
    private boolean isDarkMode = false;
    private GestureDetector gestureDetector;
    private IInputManager inputManager;
    private GestureState gestureState = new GestureState();
    private boolean isCursorLocked = false;
    private Button toggleCursorButton;

    private static class GestureState {
        List<MotionEvent> allMotionEvents = new ArrayList<>();
        int lastReplayed = 0;
        boolean isSingleFinger;
        float initialTouchX = 0;
        float initialTouchY = 0;
    }

    public static boolean startTouchpad(Context context,int displayId, boolean dryRun) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q && !ShizukuUtils.hasPermission()) {
            return false;
        }
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(context)) {
            if (!dryRun) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName())
                );
                context.startActivity(intent);
            }
            return false;
        }
        
        if (ShizukuUtils.hasShizukuStarted()) {
            if (!dryRun) {
                State.startNewJob(new StartTouchPad(displayId));
            }
            return true;
        }
        
        // 检查无障碍服务权限并尝试启动服务
        if (!isAccessibilityServiceEnabled(context)) {
            if (!dryRun) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                context.startActivity(intent);
            }
            return false;
        }
        
        if (!dryRun) {
            // 启动无障碍服务
            Intent serviceIntent = new Intent(context, TouchpadAccessibilityService.class);
            context.startService(serviceIntent);

            // 延迟1秒后启动触控板
            new Handler().postDelayed(() -> {
                if (TouchpadAccessibilityService.getInstance() == null) {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    context.startActivity(intent);
                } else {
                    // 权限都具备且服务启动后启动触控板
                    Intent touchpadIntent = new Intent(context, TouchpadActivity.class);
                    touchpadIntent.putExtra("display_id", displayId);
                    context.startActivity(touchpadIntent);
                }
            }, 1000);
            
        }
        return true;
    }

    

    // 检查无障碍服务是否启用
    private static boolean isAccessibilityServiceEnabled(Context context) {
        String serviceName = context.getPackageName() + "/" + TouchpadAccessibilityService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        
        if (enabledServices != null) {
            return enabledServices.contains(serviceName);
        }
        return false;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        
        // 4. 最后设置内容视图
        setContentView(R.layout.activity_touchpad);
        
        // 设置触控板区域的帮助文案
        touchpadArea = findViewById(R.id.touchpad_area);
        updateHelp();

        // 获取目标显示器ID
        displayId = getIntent().getIntExtra("display_id", Display.DEFAULT_DISPLAY);
        
        if (ShizukuUtils.hasPermission()) {
            inputManager = ServiceUtils.getInputManager();
        }
        // 计算屏幕尺寸
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display targetDisplay = displayManager.getDisplay(displayId);

        // 计算屏幕边界（以屏幕中心为原点）
        halfWidth = targetDisplay.getWidth() / 2.0f;
        halfHeight = targetDisplay.getHeight() / 2.0f;

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
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // 只在光标锁定时响应快速滑动手势
                if (isCursorLocked && inputManager == null) {
                    if (Math.abs(velocityY) > Math.abs(velocityX)) {  // 垂直方向的快速滑动
                        float x = cursorX + halfWidth;
                        TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
                        if (accessibilityService != null) {
                            if (velocityY < 0) {  // 向上滑动
                                Log.d(TAG, "onFling up");
                                accessibilityService.performFling(displayId, x, true);
                            } else {  // 向下滑动
                                Log.d(TAG, "onFling up down");
                                accessibilityService.performFling(displayId, x, false);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e2.getPointerCount() == 1) {
                    gestureState.isSingleFinger = true;
                    // 只在光标未锁定时更新位置
                    if (!isCursorLocked) {
                        updateCursorPosition(-distanceX, -distanceY);
                    }
                }
                if (inputManager != null) {
                    return false;
                }
                if (e2.getPointerCount() == 2) {
                    if (Math.abs(distanceY) > 5) {
                        TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
                        if (accessibilityService != null) {
                            accessibilityService.performScroll(
                                    displayId,
                                    cursorX + halfWidth,
                                    -distanceY * 2
                            );
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        // 替换触控板的触摸事件监听
        touchpadArea.setOnTouchListener((v, event) -> {
            // 计算偏移量并存储修改后的事件
            if (gestureState.allMotionEvents.isEmpty()) {
                gestureState.initialTouchX = event.getX();
                gestureState.initialTouchY = event.getY();
            }
            
            float relativeX = event.getX() - gestureState.initialTouchX;
            float relativeY = event.getY() - gestureState.initialTouchY;
            float absoluteX = cursorX + halfWidth + relativeX * 2;
            float absoluteY = cursorY + halfHeight + relativeY * 2;
            float offsetX = absoluteX - event.getX();
            float offsetY = absoluteY - event.getY();
            
            MotionEvent copiedEventWithOffset = obtainMotionEventWithOffset(event, offsetX, offsetY);
            gestureState.allMotionEvents.add(copiedEventWithOffset);
            
            gestureDetector.onTouchEvent(event);
            boolean shouldReplay = isCursorLocked;
            if (!gestureState.isSingleFinger && gestureState.allMotionEvents.size() > 4) {
                shouldReplay = true;
            }
            if (shouldReplay) {
                replayBufferedEvents();
            }
            
            // 处理手势结束
            if (event.getAction() == MotionEvent.ACTION_UP || 
                event.getAction() == MotionEvent.ACTION_CANCEL) {
                Log.d(TAG, "触摸事件结束");
                TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
                if (accessibilityService != null) {
                    accessibilityService.cancelScroll();
                }
                if (!gestureState.isSingleFinger) {
                    replayBufferedEvents();
                }
                gestureState.lastReplayed = 0;
                gestureState.isSingleFinger = false;
                gestureState.allMotionEvents.clear();
            }
            return true;
        });
        
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
            SharedPreferences appPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE);
            String lastPackageName = appPreferences.getString("LAST_PACKAGE_NAME", null);
            if (lastPackageName == null) {
                return;
            }
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(lastPackageName);
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

        toggleCursorButton = findViewById(R.id.helpButton);
        toggleCursorButton.setOnClickListener(v -> toggleCursorLock());

        if (ShizukuUtils.hasPermission()) {
            setFocus();
        }
    }

    private void updateHelp() {
        String singleFingerAction = "移动光标";
        if (isCursorLocked) {
            singleFingerAction = "刷抖音的上滑或者下滑";
        }
        touchpadArea.setText(
            "请在此区域触控\n" +
            "• 单指滑动 - " + singleFingerAction +"\n" +
            "• 单指轻触 - 点击\n" +
            "• 双指滑动 - 滚动页面\n" +
            "• 返回按钮 - 在目标屏幕上按返回\n" +
            "• 主页按钮 - 再次打开列表中选择的应用\n" +
            "• 月亮按钮 - 纯黑色屏幕省电\n" +
            "• 如果蓝牙手柄等控制不了显示器，请尝试用月亮按钮让主屏熄屏，从而把输入焦点转移到显示器"
        );
    }

    private void replayBufferedEvents() {
        if (inputManager == null || gestureState.allMotionEvents.isEmpty()) {
            return;
        }

        setFocus();

        // 直重放已经计算好偏移量的事件
        for (int i = gestureState.lastReplayed; i < gestureState.allMotionEvents.size(); i++) {
            MotionEvent event = gestureState.allMotionEvents.get(i);
            
            Log.d(TAG, String.format(
                "重放事件 #%d [显示ID=%d]: 坐标=(%.2f, %.2f), 动作=%d", 
                i, displayId, event.getX(), event.getY(), event.getAction()));
            
            MotionEventHidden eventHidden = Refine.unsafeCast(event);
            eventHidden.setDisplayId(displayId);
            inputManager.injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC);
        }
        
        gestureState.lastReplayed = gestureState.allMotionEvents.size();
    }

    private void injectKeyEvent(int action, int keyCode, int repeat, int metaState, int injectMode) {
        setFocus();
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

    // 添加更新光标位的方法
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
        TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
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
        TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
        if (accessibilityService != null) {
            accessibilityService.performBackGesture(displayId);
        }
    }

    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        darkOverlayImage.setVisibility(isDarkMode ? View.VISIBLE : View.GONE);
        if (cursorView != null) {
            cursorView.setVisibility(isDarkMode ? View.GONE : View.VISIBLE);
        }
        setFocus();
    }

    private void setFocus() {
        try {
            if (inputManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceUtils.getActivityTaskManager().focusTopTask(displayId);
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    List<ActivityTaskManager.RootTaskInfo> taskInfos = ServiceUtils.getActivityTaskManager().getAllRootTaskInfosOnDisplay(displayId);
                    for (ActivityTaskManager.RootTaskInfo taskInfo : taskInfos) {
                        ServiceUtils.getActivityTaskManager().setFocusedRootTask(taskInfo.taskId);
                        break;
                    }
                } else {
                    List<Object> stackInfos = ServiceUtils.getActivityTaskManager().getAllStackInfosOnDisplay(displayId);
                    if (!stackInfos.isEmpty()) {
                        Object stackInfo = stackInfos.get(0);
                        Field stackIdField = stackInfo.getClass().getDeclaredField("stackId");
                        stackIdField.setAccessible(true);
                        int stackId = stackIdField.getInt(stackInfo);
                        ServiceUtils.getActivityTaskManager().setFocusedStack(stackId);
                    }
                }
            } else {
                TouchpadAccessibilityService accessibilityService = TouchpadAccessibilityService.getInstance();
                if (accessibilityService != null) {
                    accessibilityService.setFocus(displayId);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "设置焦点失败", e);
        }
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

    private void toggleCursorLock() {
        isCursorLocked = !isCursorLocked;
        toggleCursorButton.setText(isCursorLocked ? "解锁光标" : "锁定光标");
        updateHelp();
    }
}