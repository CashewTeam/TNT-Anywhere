package com.gitee.connect_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

public class TouchpadAccessibilityService extends AccessibilityService {
    private static TouchpadAccessibilityService instance;
    
    private final Queue<ScrollRequest> scrollQueue = new LinkedList<>();
    private boolean isScrolling = false;
    private static final int SCROLL_DURATION = 50;

    private static class ScrollRequest {
        final int displayId;
        final float x;
        final float deltaY;

        ScrollRequest(int displayId, float x, float deltaY) {
            this.displayId = displayId;
            this.x = x;
            this.deltaY = deltaY;
        }
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        State.log("TouchpadAccessibilityService 已连接");
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        State.log("TouchpadAccessibilityService 已断开");
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
        setFocus(displayId);
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

    // 修改 findFocusableNode 方法，返回所有可获取焦点的节点列表
    private List<AccessibilityNodeInfo> findFocusableNodes(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> results) {
        if (root == null) return results;
        
        if (root.isFocusable()) {
            results.add(root);
        }
        
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                findFocusableNodes(child, results);
                child.recycle();
            }
        }
        
        return results;
    }

    public boolean setFocus(int displayId) {
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
                        // 获取所有可获取焦点的节点
                        List<AccessibilityNodeInfo> focusableNodes = findFocusableNodes(rootNode, new ArrayList<>());
                        android.util.Log.d("AccessibilityService", "找到 " + focusableNodes.size() + " 个可获取焦点的节点");
                        
                        boolean focusSuccess = false;
                        // 遍历所有可获取焦点的节点，直到成功设置焦点
                        for (AccessibilityNodeInfo node : focusableNodes) {
                            boolean focusResult = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                            android.util.Log.d("AccessibilityService", "尝试设置焦点: " + (focusResult ? "成功" : "失败"));
                            
                            if (focusResult) {
                                focusSuccess = true;
                                break;
                            }
                        }
                        
                        // 回收所有节点
                        for (AccessibilityNodeInfo node : focusableNodes) {
                            node.recycle();
                        }
                        
                        if (focusSuccess) {
                            return true;
                        } else {
                            android.util.Log.d("AccessibilityService", "所有节点都无法获取焦点");
                        }
                    } finally {
                        rootNode.recycle();
                        android.util.Log.d("AccessibilityService", "回收根节点");
                    }
                }
            }
        } else {
            android.util.Log.d("AccessibilityService", "未找到显示器 " + displayId + " 上的窗口");
        }
        return false;
    }

    // 修改 performBackGesture 方法中的相关部分
    public void performBackGesture(int displayId) {
        android.util.Log.d("AccessibilityService", "开始执行返回手势，显示器ID: " + displayId);
        if (setFocus(displayId)) {
            // 执行返回操作
            boolean backResult = performGlobalAction(GLOBAL_ACTION_BACK);
            android.util.Log.d("AccessibilityService", "执行返回操作: " + (backResult ? "成功" : "失败"));
        }
    }

    // 修改后的滚动方法，接收增量值
    public void performScroll(int displayId, float x, float deltaY) {
        android.util.Log.d("AccessibilityService", 
            String.format("添加滚动请求 - x: %.1f, deltaY: %.1f", x, deltaY));
        scrollQueue.offer(new ScrollRequest(displayId, x, deltaY));
        
        if (!isScrolling) {
            processNextScroll();
        }
    }

    private void processNextScroll() {
        ScrollRequest request = scrollQueue.poll();
        if (request == null) {
            isScrolling = false;
            return;
        }

        isScrolling = true;
        float startY = 500; // 固定起始点在屏幕中间位置
        float endY;
        
        // 确保路径始终是从上到下
        if (request.deltaY > 0) {
            // 向下滚动
            endY = startY + request.deltaY;
        } else {
            // 向上滚动：交换起点和终点
            endY = startY;
            startY = endY - request.deltaY; // 减去负值相当于加上其绝对值
        }

        Path scrollPath = new Path();
        scrollPath.moveTo(request.x, startY);
        scrollPath.lineTo(request.x, endY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        // 修改手势描述，使用较长的按压时间
        builder.addStroke(new GestureDescription.StrokeDescription(
            scrollPath, 
            0,                    // 开始时间
            SCROLL_DURATION,      // 持续时间
            true                  // 按压结束时不立即松开
        ));
        builder.setDisplayId(request.displayId);

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                android.util.Log.d("AccessibilityService", "滚动手势执行完成");
                processNextScroll(); // 处理队列中的下一个滚动请求
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                android.util.Log.d("AccessibilityService", "滚动手势被取消");
                isScrolling = false;
            }
        }, null);
    }

    public void cancelScroll() {
        android.util.Log.d("AccessibilityService", "取消所有滚动请求");
        scrollQueue.clear();
        isScrolling = false;
    }

    public void performFling(int displayId, float x, boolean isUpward) {
        float startY, endY;
        if (isUpward) {
            startY = 800;  // 从屏幕下方开始
            endY = 200;    // 滑动到屏幕上方
        } else {
            startY = 200;  // 从屏幕上方开始
            endY = 800;    // 滑动到屏幕下方
        }

        Path flingPath = new Path();
        flingPath.moveTo(x, startY);
        flingPath.lineTo(x, endY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(
            flingPath,
            0,      // 开始时间
            100     // 持续时间 - 快速滑动使用较短的时间
        ));
        builder.setDisplayId(displayId);

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                android.util.Log.d("AccessibilityService", "快速滑动手势执行完成");
            }
        }, null);
    }
}