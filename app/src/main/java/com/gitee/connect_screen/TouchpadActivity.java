package com.gitee.connect_screen;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touchpad);
        
        // 获取目标显示器ID
        displayId = getIntent().getIntExtra("display_id", Display.DEFAULT_DISPLAY);
        
        // 显示光标
        showMouseCursor();
        
        touchpadArea = findViewById(R.id.touchpad_area);
        
        // 设置触控板的触摸事件监听
        touchpadArea.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        Log.d(TAG, "触控板: 手指按下，坐标 (" + lastX + ", " + lastY + ")");
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float currentX = event.getX();
                        float currentY = event.getY();
                        float deltaX = currentX - lastX;
                        float deltaY = currentY - lastY;
                        
                        // 计算移动距离并更新光标位置
                        if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {
                            updateCursorPosition(deltaX, deltaY);
                            Log.d(TAG, "触控板: 移动，距离 (x:" + deltaX + ", y:" + deltaY + ")");
                            lastX = currentX;
                            lastY = currentY;
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "触控板: 手指抬起");
                        return true;
                        
                    case MotionEvent.ACTION_POINTER_DOWN:
                        Log.d(TAG, "触控板: 多指触摸，手指数: " + event.getPointerCount());
                        return true;
                        
                    default:
                        return false;
                }
            }
        });
        
        // 设置底部按钮点击事件
        ImageButton button1 = findViewById(R.id.button1);
        ImageButton button2 = findViewById(R.id.button2);
        ImageButton button3 = findViewById(R.id.button3);
        
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "触控板: 点击左键");
            }
        });
        
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "触控板: 点击中键");
            }
        });
        
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "触控板: 点击右键");
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
        // 更新光标坐标
        cursorX += deltaX * 1.5f;
        cursorY += deltaY * 1.5f;
        
        // 获取目标显示器的尺寸
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display targetDisplay = displayManager.getDisplay(displayId);
        android.graphics.Point size = new android.graphics.Point();
        targetDisplay.getSize(size);
        
        // 计算屏幕边界（以屏幕中心为原点）
        float halfWidth = size.x / 2.0f;
        float halfHeight = size.y / 2.0f;
        
        // 检查并记录是否超出边界
        if (cursorX < -halfWidth || cursorX > halfWidth || 
            cursorY < -halfHeight || cursorY > halfHeight) {
            Log.w(TAG, "光标位置超出边界 - 原始位置: (" + cursorX + ", " + cursorY + ")");
        }
        
        // 确保光标不会超出屏幕边界
        cursorX = Math.max(-halfWidth, Math.min(cursorX, halfWidth));
        cursorY = Math.max(-halfHeight, Math.min(cursorY, halfHeight));
        
        // 更新光标视图位置（转换回屏幕坐标系）
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