package com.gitee.connect_screen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.hardware.display.DisplayManager;

public class FloatingBackService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private int displayId;
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private static final String PREFS_NAME = "FloatingButtonPrefs";
    private static final String KEY_X = "button_x";
    private static final String KEY_Y = "button_y";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            displayId = intent.getIntExtra("display_id", -1);
            if (displayId != -1 && floatingView == null) {
                createFloatingButton();
            }
        }
        return START_STICKY;
    }

    private void createFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_back_button, null);
        ImageView backButton = floatingView.findViewById(R.id.floating_back_image);
        backButton.setImageResource(R.drawable.ic_back);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        
        // 读取上次保存的位置
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        params.x = prefs.getInt(KEY_X, 0);
        params.y = prefs.getInt(KEY_Y, 100);

        // 获取目标显示器的 WindowManager
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            stopSelf();
            return;
        }
        
        // 使用正确的显示器上下文创建 WindowManager
        Context displayContext = createDisplayContext(display);
        windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            State.log("添加悬浮窗失败: " + e.getMessage());
            stopSelf();
            return;
        }

        // 添加触摸事件处理
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                        params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 
                                && Math.abs(event.getRawY() - initialTouchY) < 10) {
                            // 模拟返回键点击
                            State.currentActivity.get().onBackPressed();
                        } else {
                            // 保存新的位置
                            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                            editor.putInt(KEY_X, params.x);
                            editor.putInt(KEY_Y, params.y);
                            editor.apply();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
    }
} 