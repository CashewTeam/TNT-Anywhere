package com.connect_screen.mirror;

import static com.connect_screen.mirror.MirrorSettingsFragment.KEY_FLOATING_BACK_BUTTON;
import static com.connect_screen.mirror.MirrorSettingsFragment.PREF_NAME;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
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

import com.connect_screen.mirror.shizuku.ServiceUtils;

public class FloatingButtonService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private int displayId;
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;
    private static final String PREFS_NAME = "FloatingButtonPrefs";
    private static final String KEY_X = "button_x";
    private static final String KEY_Y = "button_y";
    private static final int FADE_DELAY = 5000; // 5秒
    private Runnable fadeOutRunnable;
    private android.os.Handler handler;
    private boolean isReady = true;
    private static final long LONG_PRESS_TIMEOUT = 500; // 长按判定时间：500毫秒
    private static final float MOVE_THRESHOLD = 20; // 移动阈值：20像素
    private Runnable longPressRunnable;
    private boolean isLongPress = false;
    private boolean autoHide;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        State.floatingButtonService = this;
        if (intent != null) {
            displayId = intent.getIntExtra("display_id", -1);
            if (displayId != -1) {
                if (floatingView != null) {
                    this.onDestroy();
                }
                SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                autoHide = preferences.getBoolean(KEY_FLOATING_BACK_BUTTON, false);
                createFloatingButton();
            }
        }
        return START_STICKY;
    }

    public void onDisplayRemoved(int displayId) {
        if (this.displayId == displayId) {
            stopSelf();
        }
    }

    public void onSingleAppLaunched() {
        resetButtonVisibility();
    }

    private void createFloatingButton() {
        handler = new android.os.Handler();
        fadeOutRunnable = new Runnable() {
            @Override
            public void run() {
                fadeOutButton();
            }
        };

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
        
        // 读取强制横屏设置
        SharedPreferences appPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        boolean forceLandscape = appPreferences.getBoolean("FLOATING_BUTTON_FORCE_LANDSCAPE", false);
        if (forceLandscape) {
            params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        // 读取上次保存的位置
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        params.x = prefs.getInt(KEY_X, 0);
        params.y = prefs.getInt(KEY_Y, 100);
        if (params.x < 0) {
            params.x = 0;
        }
        if (params.y < 0) {
            params.y = 0;
        }

        // 获取目标显示器的 WindowManager
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            stopSelf();
            return;
        }

        // 使用正确的显示器上下文建 WindowManager
        Context displayContext = createDisplayContext(display);
        windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);

        try {
            windowManager.addView(floatingView, params);
            State.log("添加悬浮窗于 (" + params.x + "," + params.y + ")");
        } catch (Exception e) {
            State.log("添加悬浮窗失败: " + e.getMessage());
            stopSelf();
            return;
        }

        // 创建长按检测的Runnable
        longPressRunnable = () -> {
            isLongPress = true;
            State.log("返回被投的单引用");
            TouchpadActivity.launchLastPackage(FloatingButtonService.this, displayId);
        };

        // 修改触摸事件处理
        floatingView.setOnTouchListener((v, event) -> {
            resetButtonVisibility();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isLongPress = false;
                    handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float moveX = Math.abs(event.getRawX() - initialTouchX);
                    float moveY = Math.abs(event.getRawY() - initialTouchY);
                    
                    // 如果移动距离超过阈值，取消长按检测
                    if (moveX > MOVE_THRESHOLD || moveY > MOVE_THRESHOLD) {
                        handler.removeCallbacks(longPressRunnable);
                    }
                    
                    params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                    params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                    windowManager.updateViewLayout(floatingView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    float totalMoveX = Math.abs(event.getRawX() - initialTouchX);
                    float totalMoveY = Math.abs(event.getRawY() - initialTouchY);
                    
                    // 只有当移动距离小于阈值时才触发点击事件
                    if (!isReady) {
                        resetButtonVisibility();
                    } else if (!isLongPress && totalMoveX < MOVE_THRESHOLD && totalMoveY < MOVE_THRESHOLD) {
                        State.log("触发返回上一级");
                        TouchpadActivity.performBackGesture(ServiceUtils.getInputManager(), displayId);
                    }
                    return true;
            }
            return false;
        });

        // 初始启动淡出计时器
        if (autoHide) {
            startFadeOutTimer();
        }
    }

    private void startFadeOutTimer() {
        handler.removeCallbacks(fadeOutRunnable);
        handler.postDelayed(fadeOutRunnable, FADE_DELAY);
    }

    private void fadeOutButton() {
        floatingView.animate()
                .alpha(0.0f)
                .setDuration(500)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        isReady = false;
                    }
                })
                .start();
    }

    private void resetButtonVisibility() {
        if (autoHide) {
            handler.removeCallbacks(fadeOutRunnable);
            floatingView.animate()
                    .alpha(1.0f)
                    .setDuration(200)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            isReady = true;
                        }
                    })
                    .start();
            startFadeOutTimer();
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(longPressRunnable);
            handler.removeCallbacks(fadeOutRunnable);
        }
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
        State.floatingButtonService = null;
    }

    public void onDisplayChanged(int displayId) {
        if (this.displayId == displayId) {
            resetButtonVisibility();
        }
    }
}