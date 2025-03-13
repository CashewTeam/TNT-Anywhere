package com.connect_screen.mirror;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.MirrorDisplayMonitor;
import com.connect_screen.mirror.job.MirrorDisplaylinkMonitor;
import com.connect_screen.mirror.job.SunshineServer;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import rikka.shizuku.Shizuku;

public class MirrorMainActivity extends AppCompatActivity implements IMainActivity {
    public static final String ACTION_USB_PERMISSION = "com.connect_screen.mirror.USB_PERMISSION";
    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001; // 定义一个请求码
    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    public static final String TAG = "MirrorMainActivity";

    private BreadcrumbManager breadcrumbManager;
    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

    // 添加时间戳和延迟常量
    private static final long MONITOR_INIT_DELAY = 3000; // 5秒延迟
    private long lastCheckTime = 0;
    private JmDNS jmdns;

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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
        } else {
            State.log("未知权限请求代码: " + requestCode);
        }
    }

    private void onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
        } else {
            State.log("未知 Shizuku 请求代码: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::onRequestShizukuPermissionsResult;

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
        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);
        
        // 检查 SunshineService 是否已经在运行，如果没有运行才启动
        if (!isServiceRunning(SunshineService.class)) {
            Intent sunshineServiceIntent = new Intent(this, SunshineService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(sunshineServiceIntent);
            } else {
                startService(sunshineServiceIntent);
            }
            State.log("启动 SunshineService 服务");
        } else {
            State.log("SunshineService 服务已在运行");
        }
        
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        if (ShizukuUtils.hasPermission()) {
            if (State.userService == null) {
                Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
                Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
            }
            TouchpadAccessibilityService.startServiceByShizuku(this);
        } else if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            this.startService(serviceIntent);
        }

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        breadcrumbManager = new BreadcrumbManager(getSupportFragmentManager());
        State.breadcrumbManager = breadcrumbManager;
        BreadcrumbManager.homeFragmentFactory = () -> new MirrorHomeFragment();
        breadcrumbManager.pushBreadcrumb(BreadcrumbManager.homeFragmentFactory);

        // 初始化日志列表
        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);

        // 获取启动 Intent 并打印其 Action 到日志
        Intent intent = getIntent();
        String action = intent.getAction();
        State.log("MainActivity created with action: " + action);

        if (State.mirrorVirtualDisplay != null || State.displaylinkState.getVirtualDisplay() != null) {
            return;
        }

        // 查是否是 USB 设备连接的 Intent
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            MirrorDisplaylinkMonitor.onUsbDeviceAttached(this, device);
        }

        // 注册 USB 权限广播接收器
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter, null, null, Context.RECEIVER_EXPORTED);

        // 监听显示器变化
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        lastCheckTime = System.currentTimeMillis();
        MirrorDisplayMonitor.init(displayManager);
        MirrorDisplaylinkMonitor.init(this);
    }


    @Override
    protected void onResume() {
        super.onResume();
        State.currentActivity = new WeakReference<>(this);

        // 检查时间间隔
        if (MirrorActivity.getInstance() == null && 
            (System.currentTimeMillis() - lastCheckTime > MONITOR_INIT_DELAY)) {
            lastCheckTime = System.currentTimeMillis(); // 记录时间戳
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            MirrorDisplayMonitor.init(displayManager);
            MirrorDisplaylinkMonitor.init(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        State.unbindUserService();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        State.currentActivity = null;
        try {
            unregisterReceiver(usbPermissionReceiver);
        } catch (Exception e) {
            // ignore
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
       
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                lastCheckTime = System.currentTimeMillis(); // 记录时间戳
                if (MediaProjectionService.instance != null) {
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
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
                MediaProjectionService.isStarting = false;
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

    public void startMediaProjectionService() {
        MediaProjectionService.isStarting = true;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            State.currentActivity.get().startActivityForResult(captureIntent, MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            TouchpadAccessibilityService.grantPermissionByClick(State.currentActivity.get());
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }
}