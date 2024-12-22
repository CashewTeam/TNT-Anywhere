package com.gitee.connect_screen;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.view.Display;
import android.widget.FrameLayout;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.BindAllExternalInputToDisplay;
import com.gitee.connect_screen.job.ProjectViaBridge;
import com.gitee.connect_screen.job.ProjectViaDisplaylink;
import com.gitee.connect_screen.job.UsbMonitor;
import com.gitee.connect_screen.job.VirtualDisplayArgs;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.lang.ref.WeakReference;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    public static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";
    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001; // 定义一个请求码

    private FrameLayout fragmentContainer;
    private BreadcrumbManager breadcrumbManager;
    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

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

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && State.getUsbState(device.getDeviceName()) != null) {
                    State.log("USB 设备已断开: " + device.getDeviceName());
                    State.removeUsbState(device.getDeviceName());
                    State.resumeJob();
                }
                UsbMonitor.onUsbDeviceDetached(device);
            }
        }
    };

    // 添加一个新的广播接收器来处理 USB 设备连接
    private final BroadcastReceiver usbAttachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                UsbMonitor.onUsbDeviceAttached(device);
                if (device != null && device.getVendorId() == 6121) {
                    State.log("USB 设备已连接: " + device.getDeviceName());
                    if (device.getDeviceName().equals(State.displaylinkDeviceName)) {
                        State.log("识别为 Displaylink: " + device.getDeviceName());
                        State.startNewJob(new ProjectViaDisplaylink(device, State.getOrCreateUsbState(device).virtualDisplayArgs));
                    } else {
                        State.log("已有其他 Displaylink: " + State.displaylinkDeviceName);
                    }
                }
            }
        }
    };

    
    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
        } else {
            State.log("未知 Shizuku 请求代码: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::onRequestPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i("MainActivity", "成功添加隐藏API豁免");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "添加隐藏API豁免失败: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        if (ShizukuUtils.hasPermission() && State.userService == null) {
            Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
            Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
        }

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        breadcrumbManager = new BreadcrumbManager(this, getSupportFragmentManager(), findViewById(R.id.breadcrumb));
        State.breadcrumbManager = breadcrumbManager;
        breadcrumbManager.pushBreadcrumb("首页", () -> new HomeFragment());

        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);

        // 获取启动 Intent 并打印其 Action 到日志
        Intent intent = getIntent();
        String action = intent.getAction();
        State.log("MainActivity created with action: " + action);

        // 查是否是 USB 设备连接的 Intent
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            UsbMonitor.onUsbDeviceAttached(device);
            // 处理 USB 设备连接的逻辑
            if (State.displaylinkDeviceName.equals(device.getDeviceName())) {
                State.log("USB 设备已连接: " + device.getDeviceName());
                State.startNewJob(new ProjectViaDisplaylink(device, State.getOrCreateUsbState(device).virtualDisplayArgs));
            }
        }

        // 注册 USB 权限广播接收器
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter, null, null, Context.RECEIVER_EXPORTED);

        // 注册 USB 设备断开广播接收器
        IntentFilter detachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachedReceiver, detachedFilter, null, null, Context.RECEIVER_EXPORTED);

        // 注册 USB 设备连接广播接收器
        IntentFilter attachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbAttachedReceiver, attachedFilter, null, null, Context.RECEIVER_EXPORTED);

        // 监听显示器变化
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : displayManager.getDisplays()) {
            handleNewDisplay(display);
        }
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                State.log("新增显示器，displayId: " + displayId);
                Display display = displayManager.getDisplay(displayId);
                if (display != null) {
                    handleNewDisplay(display);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                State.log("移除显示器，displayId: " + displayId);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // 显示器状态变化时的处理
            }
        }, null);

        // 初始化日志列表
        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);
    }

    private void handleNewDisplay(Display display) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return;
        }
        SharedPreferences appPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE);
        boolean autoBridge = appPreferences.getBoolean("AUTO_BRIDGE_" + display.getName(), false);
        if (ShizukuUtils.hasPermission() && autoBridge) {
            new Handler().postDelayed(() -> {
                State.startNewJob(new ProjectViaBridge(display.getDisplayId(), new VirtualDisplayArgs("桥接屏幕", display.getWidth(), display.getHeight(), display.getWidth(), (int) display.getRefreshRate(), BridgePref.rotatesWithContent)));
            }, 500);
            return;
        }
        boolean autoOpen = appPreferences.getBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), false);
        if (!autoOpen) {
            return;
        }
        String lastPackageName = appPreferences.getString("LAST_PACKAGE_NAME", null);
        if (lastPackageName == null) {
            return;
        }
        State.log("尝试自动打开显示器 " + display.getName() + " 上投屏的应用 " + lastPackageName);
        new Handler().postDelayed(() -> {
            ServiceUtils.launchPackage(this, lastPackageName, display.getDisplayId());
        }, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);
        State.resumeJob();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        State.unbindUserService();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        State.currentActivity = null;
        unregisterReceiver(usbPermissionReceiver);
        unregisterReceiver(usbDetachedReceiver);
        unregisterReceiver(usbAttachedReceiver);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                if (State.hasService) {
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    State.mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK, data);
                    State.mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop 回调");
                        }
                    }, null);
                    State.resumeJob();
                } else {
                    Intent serviceIntent = new Intent(this, MediaProjectionService.class);
                    serviceIntent.putExtra("data", data);
                    startService(serviceIntent);
                }
            } else {
                State.log("用户拒绝了投屏权限");
                State.resumeJob();
            }
        }
    }

    // 添加一个方法来检查服务是否在运行
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void pushBreadcrumb(String newPath, BreadcrumbManager.FragmentFactory fragmentFactory) {
        breadcrumbManager.pushBreadcrumb(newPath, fragmentFactory);
    }
    
    @Override
    public void onBackPressed() {
        breadcrumbManager.popBreadcrumb();
    }
    
    // 更新日志列表的方法
    public void updateLogs() {
        if (logAdapter != null) {
            logAdapter.notifyDataSetChanged();
            logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
        }
    }
} 