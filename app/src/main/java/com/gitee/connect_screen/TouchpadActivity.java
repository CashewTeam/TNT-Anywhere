package com.gitee.connect_screen;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class TouchpadActivity extends AppCompatActivity {
    
    private View touchpadArea;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touchpad);
        
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
}