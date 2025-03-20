package com.connect_screen.extend;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.connect_screen.extend.shizuku.ServiceUtils;
import com.connect_screen.extend.shizuku.ShizukuUtils;
import com.connect_screen.extend.dialog.RotationDialog;
import com.connect_screen.extend.dialog.ResolutionDialog;
import com.connect_screen.extend.dialog.BridgeDialog;
import com.connect_screen.extend.dialog.DpiDialog;
import com.connect_screen.extend.shizuku.WindowingMode;

public class DisplayDetailFragment extends Fragment {
    private static final String ARG_DISPLAY_ID = "display_id";

    private TextView shizukuStatusText;
    private Button launchButton;
    private int displayId;
    private Display display;
    private Button supportedModesToggle;
    private TextView supportedModesText;
    private Button setImePolicyButton;
    private CheckBox autoOpenLastAppCheckbox;
    private Button floatingButtonToggle;
    private CheckBox forceLandscapeCheckbox;

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
        setImePolicyButton = view.findViewById(R.id.set_ime_policy_button);
        supportedModesToggle = view.findViewById(R.id.supported_modes_toggle);
        supportedModesText = view.findViewById(R.id.supported_modes_text);
        autoOpenLastAppCheckbox = view.findViewById(R.id.autoOpenLastAppCheckbox);
        displayId = getArguments().getInt(ARG_DISPLAY_ID);
        DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        display = displayManager.getDisplay(displayId);

        if(display == null) {
            State.currentActivity.get().onBackPressed();
            return view;
        }

        DisplayCutout cutout = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cutout = display.getCutout();
        }
        String cutoutInfo = "无刘海";
        if (cutout != null) {
            StringBuilder cutoutDetails = new StringBuilder("刘海边界:\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                for (Rect rect : cutout.getBoundingRects()) {
                    cutoutDetails.append(String.format("左:%d 上:%d 右:%d 下:%d\n",
                        rect.left, rect.top, rect.right, rect.bottom));
                }
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
            "状态: %s\n" +
            "HDR支持: %s\n" +
            "显示器标志: %s\n" +
            "刘海信息: %s",
            display.getDisplayId(),
            display.getName(),
            display.getRefreshRate(),
            display.getState() == Display.STATE_ON ? "开启" : "关闭",
            display.isHdr() ? "是" : "否",
            getDisplayFlags(display),
            cutoutInfo
        );

        // 添加显示模式信息到状态文本
        setupDisplayModes(display.getSupportedModes());
        detailText.setText(details);

        shizukuStatusText = view.findViewById(R.id.shizuku_status);

        launchButton = view.findViewById(R.id.start_launcher_button);
        if (displayId == 0) {
            launchButton.setVisibility(View.GONE);
        }
        launchButton.setOnClickListener(v -> {
            LauncherActivity.start(getContext(), displayId);
        });

        Button touchpadButton = view.findViewById(R.id.touchpad_button);
        if (displayId != Display.DEFAULT_DISPLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ShizukuUtils.hasPermission()) {
                touchpadButton.setVisibility(View.VISIBLE);
            }
        }
        touchpadButton.setOnClickListener(v -> {
            TouchpadActivity.startTouchpad(getContext(), displayId, false);
        });

        // 添加修改按钮点击事件
        Button editResolutionButton = view.findViewById(R.id.edit_resolution_button);
        if(ShizukuUtils.hasShizukuStarted()) {
            editResolutionButton.setVisibility(View.VISIBLE);
            editResolutionButton.setOnClickListener(v -> {
                ResolutionDialog.show(getContext(), displayId, display.getWidth(), display.getHeight());
            });
        }

        TextView dpiText = view.findViewById(R.id.dpi_text);
        dpiText.setText(String.format("DPI: %d", metrics.densityDpi));

        Button editDpiButton = view.findViewById(R.id.edit_dpi_button);
        if(ShizukuUtils.hasShizukuStarted()) {
            editDpiButton.setVisibility(View.VISIBLE);
            editDpiButton.setOnClickListener(v -> {
                DpiDialog.show(getContext(), displayId, metrics.densityDpi);
            });
        }

        TextView userRotationText = view.findViewById(R.id.user_rotation_text);
        Button editRotationButton = view.findViewById(R.id.edit_rotation_button);

        // 更新当前旋转状态
        updateUserRotationText(userRotationText);

        if(ShizukuUtils.hasShizukuStarted()) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                editRotationButton.setVisibility(View.VISIBLE);
            }
            editRotationButton.setOnClickListener(v -> {
                showRotationDialog();
            });
        }

        updateShizukuStatus();

        // 添加桥接按钮
        Button bridgeButton = view.findViewById(R.id.bridge_button);

        if (displayId == State.getBridgeVirtualDisplayId() || displayId == State.bridgeDisplayId) {
            bridgeButton.setVisibility(View.VISIBLE);
            bridgeButton.setText("退出桥接");
            bridgeButton.setOnClickListener(v -> {
                BridgeActivity.stopVirtualDisplay();
                if (BridgeActivity.getInstance() != null) {
                    BridgeActivity.getInstance().finish();
                }
            });
        } else if(displayId != Display.DEFAULT_DISPLAY && ShizukuUtils.hasShizukuStarted()) {
            bridgeButton.setVisibility(View.VISIBLE);
            bridgeButton.setOnClickListener(v -> showBridgeDialog());
        }

        floatingButtonToggle = view.findViewById(R.id.floating_button_toggle);
        forceLandscapeCheckbox = view.findViewById(R.id.force_landscape_checkbox);
        
        if (displayId != Display.DEFAULT_DISPLAY) {
            floatingButtonToggle.setVisibility(View.VISIBLE);
            forceLandscapeCheckbox.setVisibility(View.VISIBLE);
            SharedPreferences appPreferences = getActivity().getSharedPreferences("app_preferences", MODE_PRIVATE);
            boolean isEnabled = appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false);
            boolean forceLandscape = appPreferences.getBoolean("FLOATING_BUTTON_FORCE_LANDSCAPE", false);
            
            updateFloatingBackButtonText(isEnabled);
            forceLandscapeCheckbox.setChecked(forceLandscape);
            
            floatingButtonToggle.setOnClickListener(v -> {
                boolean newIsEnabled = !appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false);
                if (newIsEnabled) {
                    if (FloatingButtonService.startFloating(getContext(), displayId, false)) {
                        appPreferences.edit().putBoolean("FLOATING_BUTTON_" + display.getName(), true).apply();
                    }
                } else {
                    Intent serviceIntent = new Intent(getContext(), FloatingButtonService.class);
                    getContext().stopService(serviceIntent);
                    appPreferences.edit().putBoolean("FLOATING_BUTTON_" + display.getName(), false).apply();
                }
                updateFloatingBackButtonText(newIsEnabled);
            });

            forceLandscapeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                appPreferences.edit().putBoolean("FLOATING_BUTTON_FORCE_LANDSCAPE", isChecked).apply();
            });
        }

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
                Point baseSize = new Point();
                IWindowManager windowManager = ServiceUtils.getWindowManager();
                windowManager.getBaseDisplaySize(displayId, baseSize);
                statusText += String.format("\nOverride size: %dx%d", baseSize.x, baseSize.y);
                Point initialSize = new Point();
                windowManager.getInitialDisplaySize(displayId, initialSize);
                statusText += String.format("\nPhysical size: %dx%d", initialSize.x, initialSize.y);
               try {
                int imePolicy = windowManager.getDisplayImePolicy(displayId);
                switch (imePolicy) {
                    case 0:
                        statusText += "\n输入法策略: LOCAL";
                        break;
                    case 1:
                        statusText += "\n输入法策略: FALLBACK_DISPLAY";
                        break;
                    case 2:
                        statusText += "\n输入法策略: HIDE";
                        break;
                    default:
                        statusText += ("\n输入法策略: " + imePolicy);
                        break;
                }

                   if (displayId != Display.DEFAULT_DISPLAY) {
                       setImePolicyButton.setVisibility(View.VISIBLE);
                       if(imePolicy == 0) {
                           setImePolicyButton.setText("回主屏显示输入法");
                           setImePolicyButton.setOnClickListener(v -> {
                               windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 0);
                               windowManager.setDisplayImePolicy(displayId, 1);
                               try {
                                   State.breadcrumbManager.refreshCurrentFragment();
                               } catch (Throwable e) {
                                   State.log("回主屏显示输入法，设置失败" + e);
                               }
                           });
                       } else {
                           setImePolicyButton.setText("在此屏幕显示输入法");
                           setImePolicyButton.setOnClickListener(v -> {
                               windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 1);
                               try {
                                   windowManager.setDisplayImePolicy(displayId, 0);
                                   State.breadcrumbManager.refreshCurrentFragment();
                               } catch (Throwable e) {
                                   windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 0);
                                   State.log("在此屏幕显示输入法，设置失败" + e);
                               }
                           });
                       }
                   }
               } catch(Throwable e) {
                // ignore
               }

                DisplayInfo displayInfo = ServiceUtils.getDisplayManager().getDisplayInfo(displayId);
                statusText += String.format("\n默认模式ID: %d", displayInfo.defaultModeId);
                try {
                    statusText += String.format("\n刷新率覆盖: %.1f Hz", displayInfo.refreshRateOverride);
                } catch(Throwable e) {
                    // ignore
                }
                try {
                    statusText += String.format("\n安装方向: %d", displayInfo.installOrientation);
                } catch(Throwable e) {
                    // ignore
                }
                try {
                    String windowingMode = WindowingMode.getWindowingMode(displayId);
                    statusText += String.format("\n窗口模式: %s", windowingMode);
                } catch(Throwable e) {
                }
            }
            shizukuStatusText.setText(statusText);
        } catch(Exception e) {
            shizukuStatusText.setText("Shizuku权限状态: 未授权");
            State.log("获取 Shizuku 权限失败：" + e.getMessage());
        }
    }

    private void setupDisplayModes(Display.Mode[] supportedModes) {
        StringBuilder supportedModesStr = new StringBuilder();
        for (Display.Mode mode : supportedModes) {
            supportedModesStr.append(String.format("模式ID: %d, 分辨率: %dx%d, 刷新率: %.1f Hz\n",
                    mode.getModeId(),
                    mode.getPhysicalWidth(),
                    mode.getPhysicalHeight(),
                    mode.getRefreshRate()));
        }
        supportedModesText.setText(supportedModesStr.toString());

        // 添加双击事件
        supportedModesText.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < 300) { // 双击判断
                    showDisplayModeDialog(supportedModes);
                }
                lastClickTime = clickTime;
            }
        });

        // 设置点击展开/收起事件
        supportedModesToggle.setOnClickListener(v -> {
            boolean isVisible = supportedModesText.getVisibility() == View.VISIBLE;
            supportedModesText.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            supportedModesToggle.setText("支持的显示模式 " + (isVisible ? "▼" : "▲"));
            supportedModesText.requestLayout();
        });
    }

    private void showDisplayModeDialog(Display.Mode[] supportedModes) {
        if (!ShizukuUtils.hasShizukuStarted()) {
            showToast("需要 Shizuku 权限");
            return;
        }

        String[] items = new String[supportedModes.length];
        for (int i = 0; i < supportedModes.length; i++) {
            Display.Mode mode = supportedModes[i];
            items[i] = String.format("ID:%d %dx%d %.1fHz",
                    mode.getModeId(),
                    mode.getPhysicalWidth(),
                    mode.getPhysicalHeight(),
                    mode.getRefreshRate());
        }

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setTitle("选择显示模式")
            .setItems(items, (dialog, which) -> {
                Display.Mode selectedMode = supportedModes[which];
                try {
                    IDisplayManager displayManager = ServiceUtils.getDisplayManager();
                    displayManager.setUserPreferredDisplayMode(displayId, selectedMode);
                    showToast("设置是设置了，但是大概率无效");
                } catch (Exception e) {
                    State.log("设置显示模式失败: " + e);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    
// 添加新方法:
private void updateUserRotationText(TextView rotationText) {
    int rotation = display.getRotation();
    String rotationStr;
    switch(rotation) {
        case Surface.ROTATION_0:
            rotationStr = "0°";
            break;
        case Surface.ROTATION_90:
            rotationStr = "90°";
            break;
        case Surface.ROTATION_180:
            rotationStr = "180°";
            break;
        case Surface.ROTATION_270:
            rotationStr = "270°";
            break;
        default:
            rotationStr = "未知";
    }
    rotationText.setText("旋转角度: " + rotationStr);
}

private void showRotationDialog() {
    RotationDialog.show(getContext(), displayId);
}

private void showBridgeDialog() {
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        Toast.makeText(getContext(), "安卓15可以直接修改旋转角度，无需桥接", Toast.LENGTH_SHORT).show();
    }
    BridgeDialog.show(getContext(), display, displayId);
}

private void updateFloatingBackButtonText(boolean isEnabled) {
    floatingButtonToggle.setText(isEnabled ? "隐藏悬浮返回键" : "展示悬浮返回键");
}
}