package com.gitee.connect_screen;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TouchpadActivity extends AppCompatActivity {
    
    private View touchpadArea;
    private ImageView cursorView;
    private int displayId;
    private float lastX, lastY;
    private static final String TAG = "TouchpadActivity";
    private float cursorX = 0;
    private float cursorY = 0;
    private WindowManager.LayoutParams cursorParams;
    private static final long CLICK_TIME_THRESHOLD = 200; // 毫秒
    private long touchDownTime;
    private boolean isMoved = false;
    private float halfWidth;
    private float halfHeight;
    private TouchpadAccessibilityService accessibilityService;
    private ImageView darkOverlayImage;
    private boolean isDarkMode = false;
    
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
        
        // 设置触控板的触摸事件监听
        touchpadArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchDownTime = System.currentTimeMillis();
                        isMoved = false;
                        lastX = event.getX();
                        lastY = event.getY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float currentX = event.getX();
                        float currentY = event.getY();
                        float deltaX = currentX - lastX;
                        float deltaY = currentY - lastY;
                        
                        // 如果移动距离超过阈值，标记为已移动
                        if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {
                            isMoved = true;
                            updateCursorPosition(deltaX, deltaY);
                            lastX = currentX;
                            lastY = currentY;
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        long touchUpTime = System.currentTimeMillis();
                        // 检测快速点击（短时间内按下抬起且没有明显移动）
                        if (!isMoved && (touchUpTime - touchDownTime) < CLICK_TIME_THRESHOLD) {
                            performClick();
                        }
                        return true;
                        
                    case MotionEvent.ACTION_POINTER_DOWN:
                        Log.d(TAG, "触控板: 多指触摸，手指数: " + event.getPointerCount());
                        return true;
                        
                    default:
                        return false;
                }
            }
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
    }

    // 新增显示光标方法
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
}