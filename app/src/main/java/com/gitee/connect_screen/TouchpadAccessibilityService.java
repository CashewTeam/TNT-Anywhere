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

    // 添加新的辅助方法来查找可获取焦点的节点
    private AccessibilityNodeInfo findFocusableNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        
        // 检查当前节点是否可以获取焦点
        if (root.isFocusable()) {
            return root;
        }
        
        // 递归检查子节点
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo focusableNode = findFocusableNode(child);
                if (focusableNode != null) {
                    child.recycle();
                    return focusableNode;
                }
                child.recycle();
            }
        }
        
        return null;
    }

    // 修改 performBackGesture 方法中的相关部分
    public void performBackGesture(int displayId) {
        android.util.Log.d("AccessibilityService", "开始执行返回手势，显示器ID: " + displayId);
        
        // 获取指定显示器上的窗口
        SparseArray<List<AccessibilityWindowInfo>> windows = getWindowsOnAllDisplays();
        List<AccessibilityWindowInfo> targetDisplayWindows = windows.get(displayId);
        android.util.Log.d("AccessibilityService", "获取到窗口列表: " + (targetDisplayWindows != null ? targetDisplayWindows.size() : 0) + "个窗口");
        
        if (targetDisplayWindows != null && !targetDisplayWindows.isEmpty()) {
            // 找到目标显示器上最上层的窗口
            AccessibilityWindowInfo topWindow = null;
            int topLayer = -1;
            
            for (AccessibilityWindowInfo window : targetDisplayWindows) {
                if (window.getLayer() > topLayer) {
                    topLayer = window.getLayer();
                    topWindow = window;
                }
            }
            android.util.Log.d("AccessibilityService", "找到最上层窗口，层级: " + topLayer);
            
            if (topWindow != null) {
                // 获取并聚焦窗口的根节点
                AccessibilityNodeInfo rootNode = topWindow.getRoot();
                android.util.Log.d("AccessibilityService", "获取根节点: " + (rootNode != null ? "成功" : "失败"));
                
                if (rootNode != null) {
                    try {
                        // 查找可获取焦点的节点
                        AccessibilityNodeInfo focusableNode = findFocusableNode(rootNode);
                        android.util.Log.d("AccessibilityService", "查找可获取焦点的节点: " + 
                            (focusableNode != null ? "成功" : "失败"));
                        
                        if (focusableNode != null) {
                            boolean focusResult = focusableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                            android.util.Log.d("AccessibilityService", "设置焦点: " + 
                                (focusResult ? "成功" : "失败"));
                            focusableNode.recycle();
                        }
                        
                        // 执行返回操作
                        boolean backResult = performGlobalAction(GLOBAL_ACTION_BACK);
                        android.util.Log.d("AccessibilityService", "执行返回操作: " + 
                            (backResult ? "成功" : "失败"));
                    } finally {
                        rootNode.recycle();
                        android.util.Log.d("AccessibilityService", "回收根节点");
                    }
                }
            }
        } else {
            android.util.Log.d("AccessibilityService", "未找到显示器 " + displayId + " 上的窗口");
        }
    }
}