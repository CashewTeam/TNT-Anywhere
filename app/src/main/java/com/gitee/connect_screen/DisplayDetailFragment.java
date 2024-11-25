package com.gitee.connect_screen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import rikka.shizuku.Shizuku;

public class DisplayDetailFragment extends Fragment {
    private static final String ARG_DISPLAY_ID = "display_id";
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    
    private TextView shizukuStatusText;
    
    public static DisplayDetailFragment newInstance(int displayId) {
        DisplayDetailFragment fragment = new DisplayDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DISPLAY_ID, displayId);
        fragment.setArguments(args);
        return fragment;
    }
    
    private String getDisplayFlags(Display display) {
        int flags = display.getFlags();
        StringBuilder flagsStr = new StringBuilder();
        
        if ((flags & Display.FLAG_SECURE) != 0) flagsStr.append("FLAG_SECURE, ");
        if ((flags & Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) != 0) flagsStr.append("FLAG_SUPPORTS_PROTECTED_BUFFERS, ");
        if ((flags & Display.FLAG_PRIVATE) != 0) flagsStr.append("FLAG_PRIVATE, ");
        if ((flags & Display.FLAG_PRESENTATION) != 0) flagsStr.append("FLAG_PRESENTATION, ");
        if ((flags & Display.FLAG_ROUND) != 0) flagsStr.append("FLAG_ROUND, ");

        if (flagsStr.length() > 0) {
            flagsStr.setLength(flagsStr.length() - 2);
        }
        
        return flagsStr.length() > 0 ? flagsStr.toString() : "无";
    }
    
    private boolean checkShizukuPermission() {
        try {
            // 检查是否已经有权限
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            
            // 请求权限
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            return false;
        } catch (Exception e) {
            showToast("Shizuku 权限检查失败");
            State.log("Shizuku 权限检查失败: " + e.getMessage());
            return false;
        }
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                showToast("已获得 Shizuku 权限");
            } else {
                showToast("Shizuku 权限被拒绝");
            }
            updateShizukuStatus();
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = 
        this::onRequestPermissionsResult;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_display_detail, container, false);
        
        int displayId = getArguments().getInt(ARG_DISPLAY_ID);
        DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);

        DisplayCutout cutout = display.getCutout();
        String cutoutInfo = "无凹口";
        if (cutout != null) {
            StringBuilder cutoutDetails = new StringBuilder("凹口边界:\n");
            for (android.graphics.Rect rect : cutout.getBoundingRects()) {
                cutoutDetails.append(String.format("左:%d 上:%d 右:%d 下:%d\n",
                    rect.left, rect.top, rect.right, rect.bottom));
            }
            cutoutInfo = cutoutDetails.toString();
        }
        
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        
        TextView detailText = view.findViewById(R.id.detail_text);
        String details = String.format(
            "显示器 ID: %d\n" +
            "名称: %s\n" +
            "分辨率: %dx%d\n" +
            "刷新率: %.1f Hz\n" +
            "DPI: %d\n" +
            "状态: %s\n" +
            "HDR支持: %s\n" +
            "显示器标志: %s\n" +
            "凹口信息: %s",
            display.getDisplayId(),
            display.getName(),
            display.getWidth(),
            display.getHeight(),
            display.getRefreshRate(),
            metrics.densityDpi,
            display.getState() == Display.STATE_ON ? "开启" : "关闭",
            display.isHdr() ? "是" : "否",
            getDisplayFlags(display),
            cutoutInfo
        );
        detailText.setText(details);

        shizukuStatusText = view.findViewById(R.id.shizuku_status);
        updateShizukuStatus();
        
        Button requestPermissionButton = view.findViewById(R.id.request_shizuku_permission);
        requestPermissionButton.setOnClickListener(v -> {
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    showToast("已经获得 Shizuku 权限");
                } else {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
                }
            } catch (Exception e) {
                showToast("请求 Shizuku 权限失败");
                State.log("请求 Shizuku 权限失败: " + e.getMessage());
            }
        });

        Button launchButton = view.findViewById(R.id.start_launcher_button);
        launchButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
            getContext().startActivity(intent);
        });

        Button touchpadButton = view.findViewById(R.id.touchpad_button);
        touchpadButton.setOnClickListener(v -> {
            checkOverlayPermission();
        });

        return view;
    }

    private void updateShizukuStatus() {
        try {
            boolean hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            shizukuStatusText.setText("Shizuku权限状态: " + (hasPermission ? "已授权" : "未授权"));
        } catch(Exception e) {
            shizukuStatusText.setText("Shizuku权限状态: 未授权");
            State.log("获取 Shizuku 权限失败：" + e.getMessage());
        }
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(getContext())) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getContext().getPackageName())
            );
            startActivity(intent);
            showToast("请授予悬浮窗权限");
        } else {
            Intent touchpadIntent = new Intent(getContext(), TouchpadActivity.class);
            touchpadIntent.putExtra("display_id", getArguments().getInt(ARG_DISPLAY_ID));
            startActivity(touchpadIntent);
        }
    }
}