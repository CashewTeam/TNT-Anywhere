package com.gitee.connect_screen;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
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
                        // 处理按下事件
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        // 处理移动事件
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        // 处理抬起事件
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
                // 功能1
            }
        });
        
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 功能2
            }
        });
        
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 功能3
            }
        });
    }

    // 新增显示光标方法
    private void showMouseCursor() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        
        params.x = 0;
        params.y = 0;
        
        cursorView = new ImageView(this);
        cursorView.setImageResource(R.drawable.mouse_cursor);
        
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display targetDisplay = displayManager.getDisplay(displayId);
        
        Context displayContext = createDisplayContext(targetDisplay);
        WindowManager windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        
        try {
            windowManager.addView(cursorView, params);
        } catch (Exception e) {
            Toast.makeText(this, "显示鼠标光标失败", Toast.LENGTH_SHORT).show();
            State.log("显示鼠标光标失败: " + e.getMessage());
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