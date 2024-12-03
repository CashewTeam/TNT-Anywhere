package com.gitee.connect_screen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import rikka.shizuku.Shizuku;
import android.graphics.Color;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.AcquireShizukuAndTouchPad;
import com.gitee.connect_screen.job.ChangeResolution;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

public class DisplayDetailFragment extends Fragment {
    private static final String ARG_DISPLAY_ID = "display_id";
    
    private TextView shizukuStatusText;
    private Button launchButton;
    private int displayId;
    
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

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_display_detail, container, false);
        
        displayId = getArguments().getInt(ARG_DISPLAY_ID);
        DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);

        if(display == null) {
            return view;
        }

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
        TextView resolutionText = view.findViewById(R.id.resolution_text);
        
        // 设置分辨率文本
        String resolution = String.format("分辨率: %dx%d", display.getWidth(), display.getHeight());
        resolutionText.setText(resolution);
        
        String details = String.format(
            "显示器 ID: %d\n" +
            "名称: %s\n" +
            "刷新率: %.1f Hz\n" +
            "DPI: %d\n" +
            "状态: %s\n" +
            "HDR支持: %s\n" +
            "显示器标志: %s\n" +
            "凹口信息: %s",
            display.getDisplayId(),
            display.getName(),
            display.getRefreshRate(),
            metrics.densityDpi,
            display.getState() == Display.STATE_ON ? "开启" : "关闭",
            display.isHdr() ? "是" : "否",
            getDisplayFlags(display),
            cutoutInfo
        );
        detailText.setText(details);

        shizukuStatusText = view.findViewById(R.id.shizuku_status);

        launchButton = view.findViewById(R.id.start_launcher_button);
        launchButton.setOnClickListener(v -> {
            Context context = State.currentActivity.get();
            Intent intent = new Intent(context, LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
            context.startActivity(intent);
        });

        Button touchpadButton = view.findViewById(R.id.touchpad_button);
        touchpadButton.setOnClickListener(v -> {
            startTouchpad();
        });

        // 添加修改按钮点击事件
        Button editResolutionButton = view.findViewById(R.id.edit_resolution_button);
        if(ShizukuUtils.hasShizukuStarted()) {
            editResolutionButton.setVisibility(View.VISIBLE);
            editResolutionButton.setOnClickListener(v -> {
                showResolutionDialog(display.getWidth(), display.getHeight());
            });
        }

        updateShizukuStatus();
        return view;
    }

    private void updateShizukuStatus() {
        if (shizukuStatusText == null) {
            return;
        }
        if (!ShizukuUtils.hasShizukuStarted()) {
            shizukuStatusText.setText("Shizuku权限状态: 未启动");
            return;
        }
        try {
            boolean hasPermission = ShizukuUtils.hasPermission();
            String statusText = "Shizuku权限状态: " + (hasPermission ? "已授权" : "未授权");
            if (hasPermission) {
                ServiceUtils.initWithShizuku();
                Point baseSize = new Point();
                ServiceUtils.getWindowManager().getBaseDisplaySize(displayId, baseSize);
                statusText += String.format("\nOverride size: %dx%d", baseSize.x, baseSize.y);
                Point initialSize = new Point();
                ServiceUtils.getWindowManager().getInitialDisplaySize(displayId, initialSize);
                statusText += String.format("\nPhysical size: %dx%d", initialSize.x, initialSize.y);
            }
            shizukuStatusText.setText(statusText);
        } catch(Exception e) {
            shizukuStatusText.setText("Shizuku权限状态: 未授权");
            State.log("获取 Shizuku 权限失败：" + e.getMessage());
        }
    }

    private void startTouchpad() {
        // if (ShizukuUtils.hasShizukuStarted()) {
        //     State.startNewJob(new AcquireShizukuAndTouchPad(displayId));
        //     return;
        // }
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(getContext())) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getContext().getPackageName())
            );
            startActivity(intent);
            showToast("请授予悬浮窗权限");
            return;
        }
        
        // 检查无障碍服务权限并尝试启动服务
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            showToast("请开启无障碍服务权限");
            return;
        }
        
        // 启动无障碍服务
        Intent serviceIntent = new Intent(getContext(), TouchpadAccessibilityService.class);
        getContext().startService(serviceIntent);
        
        // 权限都具备且服务启动后启动触控板
        Intent touchpadIntent = new Intent(getContext(), TouchpadActivity.class);
        touchpadIntent.putExtra("display_id", getArguments().getInt(ARG_DISPLAY_ID));
        startActivity(touchpadIntent);
    }

    // 检查无障碍服务是否启用
    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getContext().getPackageName() + "/" + TouchpadAccessibilityService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(
            getContext().getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        
        if (enabledServices != null) {
            return enabledServices.contains(serviceName);
        }
        return false;
    }

    private void showResolutionDialog(int currentWidth, int currentHeight) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_resolution, null);
        EditText widthInput = dialogView.findViewById(R.id.width_input);
        EditText heightInput = dialogView.findViewById(R.id.height_input);
        
        // 设置当前分辨率作为默认值
        widthInput.setText(String.valueOf(currentWidth));
        heightInput.setText(String.valueOf(currentHeight));

        new AlertDialog.Builder(getContext())
                .setTitle("修改分辨率")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int newWidth = Integer.parseInt(widthInput.getText().toString());
                        int newHeight = Integer.parseInt(heightInput.getText().toString());
                        
                        if (newWidth <= 0 || newHeight <= 0) {
                            showToast("请输入有效的分辨率");
                            return;
                        }
                        State.startNewJob(new ChangeResolution(displayId, newWidth, newHeight));
                    } catch (NumberFormatException e) {
                        showToast("请输入有效的数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}