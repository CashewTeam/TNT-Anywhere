package com.gitee.connect_screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.job.MirrorViaDisplaylink;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";

    private LinearLayout breadcrumb;
    private FrameLayout fragmentContainer;
    private List<String> navigationPath = new ArrayList<>();
    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                State.resumeJob();
            }
        }
    };

    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.main);
        
        breadcrumb = findViewById(R.id.breadcrumb);
        fragmentContainer = findViewById(R.id.fragmentContainer);
        
        pushBreadcrumb("首页", new HomeFragment());
        
        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);
        
        // 获取启动 Intent 并打印其 Action 到日志
        Intent intent = getIntent();
        String action = intent.getAction();
        State.log("MainActivity created with action: " + action);
        
        // 查是否是 USB 设备连接的 Intent
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                // 处理 USB 设备连接的逻辑
                State.log("USB 设备已连接: " + device.getDeviceName());
                State.startNewJob(new MirrorViaDisplaylink(device));
            }
        }
        
        // 注册 USB 权限广播接收器
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter, null, null, Context.RECEIVER_EXPORTED);
        
        // 初始化日志列表
        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        navigationPath.clear();
        // 清除弱引用
        State.currentActivity = null;

        // 注销 USB 权限广播接收器
        unregisterReceiver(usbPermissionReceiver);
    }
    
    public void pushBreadcrumb(String newPath, Fragment fragment) {
        if (!newPath.isEmpty() && !navigationPath.contains(newPath)) {
            navigationPath.add(newPath);
        }
        updateBreadcrumbView();
        // 替换 Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
    
    public void popBreadcrumb() {
        if (navigationPath.size() > 1) {
            navigationPath.remove(navigationPath.size() - 1);
        } else {
            finish();
        }
        updateBreadcrumbView();
        // 回退 Fragment
        getSupportFragmentManager().popBackStack();
    }
    
    private void updateBreadcrumbView() {
        breadcrumb.removeAllViews();
        
        for (int i = 0; i < navigationPath.size(); i++) {
            TextView separator = new TextView(this);
            separator.setText(" > ");
            breadcrumb.addView(separator);
            
            TextView pathItem = new TextView(this);
            pathItem.setText(navigationPath.get(i));
            breadcrumb.addView(pathItem);
        }
    }
    
    @Override
    public void onBackPressed() {
        popBreadcrumb();
    }
    
    // 更新日志列表的方法
    public void updateLogs() {
        if (logAdapter != null) {
            logAdapter.notifyDataSetChanged();
            logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
        }
    }
} 