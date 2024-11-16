package com.gitee.connect_screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";

    private LinearLayout breadcrumb;
    private LinearLayout buttonGroup;
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

        setContentView(R.layout.main);
        
        breadcrumb = findViewById(R.id.breadcrumb);
        buttonGroup = findViewById(R.id.buttonGroup);
        fragmentContainer = findViewById(R.id.fragmentContainer);
        Button virtualScreenBtn = findViewById(R.id.virtualScreenBtn);
        Button usbDeviceBtn = findViewById(R.id.usbDeviceBtn);
        
        buttonGroup.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
        
        virtualScreenBtn.setOnClickListener(v -> {
            pushBreadcrumb("虚拟屏幕");
            buttonGroup.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new VirtualScreenFragment())
                .addToBackStack(null)
                .commit();
        });
        
        usbDeviceBtn.setOnClickListener(v -> {
            pushBreadcrumb("USB设备");
            buttonGroup.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new UsbFragment())
                .addToBackStack(null)
                .commit();
        });

        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);

        // 注册 USB 权限广播接收器
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, filter, null, null, Context.RECEIVER_EXPORTED);
        State.log("MainActivity created");

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
    
    public void pushBreadcrumb(String newPath) {
        if (!newPath.isEmpty() && !navigationPath.contains(newPath)) {
            navigationPath.add(newPath);
        }
        updateBreadcrumbView();
    }
    
    public void popBreadcrumb() {
        if (!navigationPath.isEmpty()) {
            navigationPath.remove(navigationPath.size() - 1);
        }
        updateBreadcrumbView();
    }
    
    private void updateBreadcrumbView() {
        breadcrumb.removeAllViews();
        
        TextView homeView = new TextView(this);
        homeView.setText("首页");
        homeView.setClickable(true);
        homeView.setTextColor(getResources().getColor(R.color.blue));
        homeView.setOnClickListener(v -> {
            navigationPath.clear();
            buttonGroup.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            breadcrumb.removeAllViews();
            breadcrumb.addView(homeView);
        });
        breadcrumb.addView(homeView);
        
        for (int i = 0; i < navigationPath.size(); i++) {
            TextView separator = new TextView(this);
            separator.setText(" > ");
            breadcrumb.addView(separator);
            
            TextView pathView = new TextView(this);
            pathView.setText(navigationPath.get(i));
            pathView.setTextColor(getResources().getColor(R.color.blue));
            final int index = i;
            pathView.setClickable(true);
            pathView.setOnClickListener(v -> {
                while (navigationPath.size() > index + 1) {
                    navigationPath.remove(navigationPath.size() - 1);
                }
                updateBreadcrumbView();
            });
            breadcrumb.addView(pathView);
        }
    }
    
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
            getSupportFragmentManager().popBackStack();
            popBreadcrumb();
        } else if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            buttonGroup.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            navigationPath.clear();
            updateBreadcrumbView();
        } else {
            super.onBackPressed();
        }
    }

    // 更新日志列表的方法
    public void updateLogs() {
        if (logAdapter != null) {
            logAdapter.notifyDataSetChanged();
        }
    }
} 