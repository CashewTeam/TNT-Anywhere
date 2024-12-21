package com.gitee.connect_screen;

import static android.content.Context.MODE_PRIVATE;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.opengl.Visibility;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.net.ServerSocket;

import com.gitee.connect_screen.job.ChangeDPI;
import com.gitee.connect_screen.job.ChangeResolution;
import com.gitee.connect_screen.job.ChangeRotation;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

public class DisplayDetailFragment extends Fragment {
    private static final String ARG_DISPLAY_ID = "display_id";
    
    private TextView shizukuStatusText;
    private Button launchButton;
    private int displayId;
    private Display display;
    private Button supportedModesToggle;
    private TextView supportedModesText;
    private Button gotoDisplaylinkButton;
    private Button setImePolicyButton;
    private CheckBox autoOpenLastAppCheckbox;

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
            "状态: %s\n" +
            "HDR支持: %s\n" +
            "显示器标志: %s\n" +
            "凹口信息: %s",
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
            Context context = State.currentActivity.get();
            Intent intent = new Intent(context, LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
            context.startActivity(intent);
        });

        Button touchpadButton = view.findViewById(R.id.touchpad_button);
        if (displayId != Display.DEFAULT_DISPLAY) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R || ShizukuUtils.hasPermission()) {
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
                showResolutionDialog(display.getWidth(), display.getHeight());
            });
        }

        TextView dpiText = view.findViewById(R.id.dpi_text);
        dpiText.setText(String.format("DPI: %d", metrics.densityDpi));

        Button editDpiButton = view.findViewById(R.id.edit_dpi_button);
        if(ShizukuUtils.hasShizukuStarted()) {
            editDpiButton.setVisibility(View.VISIBLE);
            editDpiButton.setOnClickListener(v -> {
                showDpiDialog(metrics.densityDpi);
            });
        }

        TextView userRotationText = view.findViewById(R.id.user_rotation_text);
        Button editRotationButton = view.findViewById(R.id.edit_rotation_button);

        // 更新当前旋转状态
        updateUserRotationText(userRotationText);

        if(ShizukuUtils.hasShizukuStarted()) {
            editRotationButton.setVisibility(View.VISIBLE);
            editRotationButton.setOnClickListener(v -> {
                showRotationDialog();
            });
        }

        updateShizukuStatus();

        // 添加 Displaylink 按钮相关逻辑
        gotoDisplaylinkButton = view.findViewById(R.id.goto_displaylink_button);
        UsbDevice usbDevice = State.virtualDisplayIds.get(displayId);
        if(usbDevice == null) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                autoOpenLastAppCheckbox.setVisibility(View.VISIBLE);
                SharedPreferences appPreferences = getActivity().getSharedPreferences("app_preferences", MODE_PRIVATE);
                autoOpenLastAppCheckbox.setChecked(appPreferences.getBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), false));
                autoOpenLastAppCheckbox.setOnClickListener(v -> {
                    boolean isChecked = autoOpenLastAppCheckbox.isChecked();
                    appPreferences.edit().putBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), isChecked).apply();
                });
            }
        } else {
            gotoDisplaylinkButton.setVisibility(View.VISIBLE);
            gotoDisplaylinkButton.setOnClickListener(v -> {
                MainActivity activity = (MainActivity) getActivity();
                activity.pushBreadcrumb("Displaylink", () -> 
                    DisplaylinkFragment.newInstance(usbDevice)
                );
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
                               windowManager.setDisplayImePolicy(displayId, 1);
                               windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 0);
                               State.breadcrumbManager.refreshCurrentFragment();
                           });
                       } else {
                           setImePolicyButton.setText("在此屏幕显示输入法");
                           setImePolicyButton.setOnClickListener(v -> {
                               windowManager.setDisplayImePolicy(displayId, 0);
                               windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 1);
                               State.breadcrumbManager.refreshCurrentFragment();
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
            }
            shizukuStatusText.setText(statusText);
        } catch(Exception e) {
            shizukuStatusText.setText("Shizuku权限状态: 未授权");
            State.log("获取 Shizuku 权限失败：" + e.getMessage());
        }
    }

    private void showResolutionDialog(int currentWidth, int currentHeight) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_resolution, null);
        EditText widthInput = dialogView.findViewById(R.id.width_input);
        EditText heightInput = dialogView.findViewById(R.id.height_input);
        
        // 设置当前分辨率作为默认值
        widthInput.setText(String.valueOf(currentWidth));
        heightInput.setText(String.valueOf(currentHeight));

        new AlertDialog.Builder(getContext())
                .setTitle("修改分辨率（大概率无效）")
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

    private void showDpiDialog(int currentDpi) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_dpi, null);
        EditText dpiInput = dialogView.findViewById(R.id.dpi_input);
        
        dpiInput.setText(String.valueOf(currentDpi));

        new AlertDialog.Builder(getContext())
                .setTitle("修改DPI")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int newDpi = Integer.parseInt(dpiInput.getText().toString());
                        
                        if (newDpi <= 0) {
                            showToast("请输入有效的DPI值");
                            return;
                        }
                        State.startNewJob(new ChangeDPI(displayId, newDpi));
                    } catch (NumberFormatException e) {
                        showToast("请输入有效的数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupDisplayModes(Display.Mode[] supportedModes) {
        // 设置支持的显示模式文本
        StringBuilder supportedModesStr = new StringBuilder();
        for (Display.Mode mode : supportedModes) {
            supportedModesStr.append(String.format("模式ID: %d, 分辨率: %dx%d, 刷新率: %.1f Hz\n",
                    mode.getModeId(),
                    mode.getPhysicalWidth(),
                    mode.getPhysicalHeight(),
                    mode.getRefreshRate()));
        }
        supportedModesText.setText(supportedModesStr.toString());
        // 设置点击展开/收起事件
        supportedModesToggle.setOnClickListener(v -> {
            boolean isVisible = supportedModesText.getVisibility() == View.VISIBLE;
            supportedModesText.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            supportedModesToggle.setText("支持的显示模式 " + (isVisible ? "▼" : "▲"));
            supportedModesText.requestLayout();
        });
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
    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_rotation, null);
    Spinner rotationSpinner = dialogView.findViewById(R.id.rotation_spinner);
    
    String[] rotationOptions = new String[]{"不强制", "0°", "90°", "180°", "270°"};
    ArrayAdapter<String> adapter = new ArrayAdapter<>(
        getContext(),
        android.R.layout.simple_spinner_item,
        rotationOptions
    );
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    rotationSpinner.setAdapter(adapter);

    new AlertDialog.Builder(getContext())
            .setTitle("修改旋转方向（仅在澎湃2安卓15上测试有效）")
            .setView(dialogView)
            .setPositiveButton("确定", (dialog, which) -> {
                int position = rotationSpinner.getSelectedItemPosition();
                int rotation;
                switch (position) {
                    case 0: // 不强制
                        rotation = -1;
                        break;
                    case 1: // 0°
                        rotation = Surface.ROTATION_0;
                        break;
                    case 2: // 90°
                        rotation = Surface.ROTATION_90;
                        break;
                    case 3: // 180°
                        rotation = Surface.ROTATION_180;
                        break;
                    case 4: // 270°
                        rotation = Surface.ROTATION_270;
                        break;
                    default:
                        rotation = -1;
                }
                State.startNewJob(new ChangeRotation(displayId, rotation));
            })
            .setNegativeButton("取消", null)
            .show();
}
}