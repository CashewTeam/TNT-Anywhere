package com.gitee.connect_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Binder;
import android.os.IBinder;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

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

    // 修改返回手势方法
    public void performBackGesture(int displayId) {
        // 打印所有显示器上的窗口信息
        SparseArray<List<AccessibilityWindowInfo>> windows = getWindowsOnAllDisplays();
        android.util.Log.d("AccessibilityService", "Total windows found: " + windows.size());

        for (int i = 0; i < windows.size(); i++) {
            List<AccessibilityWindowInfo> windowsList = windows.valueAt(i);
            for (AccessibilityWindowInfo window : windowsList) {
                android.util.Log.d("AccessibilityService", 
                    "Window Info - Display ID: " + window.getDisplayId() + 
                    ", Type: " + window.getType() + 
                    ", Layer: " + window.getLayer());
            }
        }

        // 使用 getRootInActiveWindow() 获取活动窗口
        AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
        if (rootInActiveWindow != null) {
            try {
                // 记录当前窗口信息
                android.util.Log.d("AccessibilityService", 
                    "Active Window - Package: " + rootInActiveWindow.getPackageName() + 
                    " Class: " + rootInActiveWindow.getClassName());
                
                // 尝试查找可聚焦的节点
                List<AccessibilityNodeInfo> focusableNodes = rootInActiveWindow.findAccessibilityNodeInfosByViewId("android:id/content");
                for (AccessibilityNodeInfo node : focusableNodes) {
                    if (node.isFocusable()) {
                        // 尝试设置焦点
                        boolean focusResult = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        android.util.Log.d("AccessibilityService", "Focus attempt result: " + focusResult);
                        node.recycle();
                    }
                }
                
                // 执行返回操作
                performGlobalAction(GLOBAL_ACTION_BACK);
            } finally {
                rootInActiveWindow.recycle();
            }
        } else {
            android.util.Log.d("AccessibilityService", "No active window found");
        }
    }
}