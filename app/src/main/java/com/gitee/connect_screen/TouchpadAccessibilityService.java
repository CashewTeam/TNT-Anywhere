package com.gitee.connect_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Binder;
import android.os.IBinder;
import android.view.accessibility.AccessibilityEvent;

public class TouchpadAccessibilityService extends AccessibilityService {
    private static TouchpadAccessibilityService instance;
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }
    
    public static TouchpadAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 处理辅助功能事件
    }

    @Override
    public void onInterrupt() {
        // 服务中断时的处理
    }

    // 模拟点击事件
    public void performClick(int displayId, float x, float y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
        
        // 设置目标显示器
        builder.setDisplayId(displayId);
        
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
    }

    // 模拟返回手势
    public void performBackGesture(int displayId) {
        performClick(displayId, 0, 0);
        performGlobalAction(GLOBAL_ACTION_BACK);
    }
}