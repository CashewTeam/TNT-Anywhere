package com.connect_screen.mirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;

public class MirrorSettingsFragment extends Fragment {
    private SharedPreferences preferences;
    public static final String PREF_NAME = "mirror_settings";
    public static final String KEY_AUTO_ROTATE = "auto_rotate";
    public static final String KEY_AUTO_SCALE = "auto_scale";
    public static final String KEY_SINGLE_APP_MODE = "single_app_mode";
    public static final String KEY_SELECTED_APP_PACKAGE = "selected_app_package";
    public static final String KEY_SELECTED_APP_NAME = "selected_app_name";
    public static final String KEY_SINGLE_APP_DPI = "single_app_dpi";
    public static final String KEY_FLOATING_BACK_BUTTON = "floating_back_button";
    public static final String KEY_AUTO_SCREEN_OFF = "auto_screen_off";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_settings, container, false);

        CheckBox singleAppModeCheckbox = view.findViewById(R.id.singleAppModeCheckbox);
        Button selectAppButton = view.findViewById(R.id.selectAppButton);
        View singleAppContainer = view.findViewById(R.id.singleAppContainer);
        CheckBox autoRotateCheckbox = view.findViewById(R.id.autoRotateCheckbox);
        CheckBox autoScaleCheckbox = view.findViewById(R.id.autoScaleCheckbox);
        EditText widthEditText = view.findViewById(R.id.widthEditText);
        EditText heightEditText = view.findViewById(R.id.heightEditText);
        EditText dpiEditText = view.findViewById(R.id.dpiEditText);
        LinearLayout dpiLayout = view.findViewById(R.id.dpiLayout);
        CheckBox floatingBackButtonCheckbox = view.findViewById(R.id.floatingBackButtonCheckbox);
        CheckBox autoScreenOffCheckbox = view.findViewById(R.id.autoScreenOffCheckbox);
        
        // 加载保存的设置
        boolean singleAppMode = preferences.getBoolean(KEY_SINGLE_APP_MODE, false);
        boolean autoRotate = preferences.getBoolean(KEY_AUTO_ROTATE, true);
        boolean autoScale = preferences.getBoolean(KEY_AUTO_SCALE, true);
        int singleAppDpi = preferences.getInt(KEY_SINGLE_APP_DPI, 160);
        boolean floatingBackButton = preferences.getBoolean(KEY_FLOATING_BACK_BUTTON, false);
        boolean autoScreenOff = preferences.getBoolean(KEY_AUTO_SCREEN_OFF, false);
        
        singleAppModeCheckbox.setChecked(singleAppMode);
        autoRotateCheckbox.setChecked(autoRotate);
        autoScaleCheckbox.setChecked(autoScale);
        dpiEditText.setText(String.valueOf(singleAppDpi));
        floatingBackButtonCheckbox.setChecked(floatingBackButton);
        autoScreenOffCheckbox.setChecked(autoScreenOff);

        // 设置分辨率初始值
        DisplaylinkPref.load(requireContext());
        widthEditText.setText(String.valueOf(DisplaylinkPref.monitorWidth));
        heightEditText.setText(String.valueOf(DisplaylinkPref.monitorHeight));

        
        // 显示已选择的应用名称（如果有）
        String selectedAppName = preferences.getString(KEY_SELECTED_APP_NAME, "");
        if (!selectedAppName.isEmpty() && singleAppMode) {
            singleAppModeCheckbox.setText("单应用投屏: " + selectedAppName);
        } else {
            singleAppModeCheckbox.setText("单应用投屏");
        }
        
        // 监听复选框变化
        singleAppModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_SINGLE_APP_MODE, isChecked).apply();
            autoRotateCheckbox.setEnabled(!isChecked);
            singleAppContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            
            if (!isChecked) {
                singleAppModeCheckbox.setText("单应用投屏");
            } else if (!selectedAppName.isEmpty()) {
                singleAppModeCheckbox.setText("单应用投屏: " + selectedAppName);
            }
        });
        
        autoRotateCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_ROTATE, isChecked).apply();
        });

        autoScaleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_SCALE, isChecked).apply();
        });
        autoScaleCheckbox.setEnabled(!singleAppMode);

        // 监听分辨率输入变化
        widthEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    Context context = requireContext();
                    DisplaylinkPref.load(context);
                    int width = Integer.parseInt(widthEditText.getText().toString());
                    DisplaylinkPref.monitorWidth = width;
                    DisplaylinkPref.save(context);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        });

        heightEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    Context context = requireContext();
                    DisplaylinkPref.load(context);
                    int height = Integer.parseInt(heightEditText.getText().toString());
                    DisplaylinkPref.monitorHeight = height;
                    DisplaylinkPref.save(context);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        });

        // 添加分辨率预设选项
        Spinner resolutionPresetSpinner = view.findViewById(R.id.resolutionPresetSpinner);
        String[] resolutionPresets = new String[]{"快捷设置", "1080p", "1440p", "2160p", "ipad4"};
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            resolutionPresets
        );
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionPresetSpinner.setAdapter(resolutionAdapter);
        
        resolutionPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    switch (position) {
                        case 1: // 1080p
                            widthEditText.setText("1920");
                            heightEditText.setText("1080");
                            break;
                        case 2: // 1440p
                            widthEditText.setText("2560");
                            heightEditText.setText("1440");
                            break;
                        case 3: // 2160p
                            widthEditText.setText("3840");
                            heightEditText.setText("2160");
                            break;
                        case 4: // ipad4
                            widthEditText.setText("2048");
                            heightEditText.setText("1536");
                            break;
                    }
                    
                    Context context = requireContext();
                    DisplaylinkPref.load(context);
                    int height = Integer.parseInt(heightEditText.getText().toString());
                    int width = Integer.parseInt(widthEditText.getText().toString());
                    DisplaylinkPref.monitorHeight = height;
                    DisplaylinkPref.monitorWidth = width;
                    DisplaylinkPref.save(context);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何操作
            }
        });

        // 添加关于按钮点击事件
        Button aboutButton = view.findViewById(R.id.aboutButton);
        aboutButton.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("关于", () -> new AboutFragment());
        });

        
        // 添加授权按钮
        Button shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> {
            State.startNewJob(new AcquireShizuku());
        });

        // 更新Shizuku状态
        TextView shizukuStatus = view.findViewById(R.id.shizukuStatus);
        TextView accessibilityStatus = view.findViewById(R.id.accessibilityStatus);
        TextView overlayStatus = view.findViewById(R.id.overlayStatus);
        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);
        updateAccessibilityStatus(accessibilityStatus);
        updateOverlayStatus(overlayStatus);

        // 添加选择应用按钮点击事件
        selectAppButton.setOnClickListener(v -> {
            showAppSelectionDialog();
        });

        // 监听DPI输入变化
        dpiEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int dpi = Integer.parseInt(dpiEditText.getText().toString());
                    // 限制DPI的合理范围，例如60-600
                    if (dpi < 60) dpi = 60;
                    if (dpi > 600) dpi = 600;
                    dpiEditText.setText(String.valueOf(dpi)); // 更新显示值
                    preferences.edit().putInt(KEY_SINGLE_APP_DPI, dpi).apply(); // 保存DPI设置
                } catch (NumberFormatException e) {
                    // 如果输入无效，恢复为默认值或上次保存的值
                    dpiEditText.setText(String.valueOf(preferences.getInt(KEY_SINGLE_APP_DPI, 160)));
                }
            }
        });

        // 添加悬浮返回键复选框监听器
        floatingBackButtonCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_FLOATING_BACK_BUTTON, isChecked).apply();
        });
        if (!ShizukuUtils.hasPermission()) {
            floatingBackButtonCheckbox.setEnabled(false);
        }

        // 设置单应用模式容器的可见性
        singleAppContainer.setVisibility(singleAppMode ? View.VISIBLE : View.GONE);

        // 添加自动熄屏复选框监听器
        autoScreenOffCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_SCREEN_OFF, isChecked).apply();
        });

        return view;
    }

    
    private void updateShizukuStatus(TextView statusView, Button permissionBtn) {
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();
        
        String status;
        if (!started) {
            status = "未启动";
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            status = "已启动未授权";
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            status = "已授权";
            permissionBtn.setVisibility(View.GONE);
        }
        
        statusView.setText(status);
    }

    private void updateAccessibilityStatus(TextView statusView) {
        boolean isEnabled = TouchpadAccessibilityService.isAccessibilityServiceEnabled(requireContext());
        statusView.setText(isEnabled ? "已授权" : "未授权");
        
        // 获取或创建授权按钮
        View parent = (View) statusView.getParent();
        Button accessibilityPermissionBtn = parent.findViewById(R.id.accessibilityPermissionBtn);
        
        // 根据授权状态显示或隐藏按钮
        if (accessibilityPermissionBtn != null) {
            accessibilityPermissionBtn.setVisibility(isEnabled ? View.GONE : View.VISIBLE);
            accessibilityPermissionBtn.setOnClickListener(v -> {
                // 跳转到系统无障碍设置页面
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
        }
    }

    private void updateOverlayStatus(TextView statusView) {
        boolean hasPermission = Settings.canDrawOverlays(requireContext());
        statusView.setText(hasPermission ? "已授权" : "未授权");
        
        // 获取或创建授权按钮
        View parent = (View) statusView.getParent();
        Button overlayPermissionBtn = parent.findViewById(R.id.overlayPermissionBtn);
        
        // 根据授权状态显示或隐藏按钮
        if (overlayPermissionBtn != null) {
            overlayPermissionBtn.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
            overlayPermissionBtn.setOnClickListener(v -> {
                // 跳转到悬浮窗权限设置页面
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            });
        }
    }

    private void showAppSelectionDialog() {
        // 获取所有桌面应用
        PackageManager pm = requireContext().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(intent, 0);
        
        // 创建自定义适配器来显示应用图标和名称
        AppListAdapter adapter = new AppListAdapter(requireContext(), launcherApps, pm);
        
        // 创建并显示选择对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择应用");
        builder.setAdapter(adapter, (dialog, which) -> {
            ResolveInfo selectedApp = launcherApps.get(which);
            String selectedPackage = selectedApp.activityInfo.packageName;
            String selectedName = selectedApp.loadLabel(pm).toString();
            
            // 保存选择的应用
            preferences.edit()
                    .putString(KEY_SELECTED_APP_PACKAGE, selectedPackage)
                    .putString(KEY_SELECTED_APP_NAME, selectedName)
                    .apply();
            
            // 更新UI显示
            CheckBox singleAppModeCheckbox = getView().findViewById(R.id.singleAppModeCheckbox);
            singleAppModeCheckbox.setText("单应用投屏: " + selectedName);
        });
        
        builder.show();
    }

    // 自定义适配器用于显示应用图标和名称
    private static class AppListAdapter extends ArrayAdapter<ResolveInfo> {
        private final PackageManager pm;
        private final int ICON_SIZE_DP = 36; // 统一图标大小为36dp
        
        public AppListAdapter(Context context, List<ResolveInfo> apps, PackageManager pm) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, apps);
            this.pm = pm;
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                        android.R.layout.simple_list_item_2, parent, false);
            }
            
            ResolveInfo app = getItem(position);
            if (app != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);
                
                text1.setText(app.loadLabel(pm));
                text2.setText(app.activityInfo.packageName);
                
                // 设置应用图标并统一大小
                try {
                    // 获取图标
                    android.graphics.drawable.Drawable icon = app.loadIcon(pm);
                    
                    // 计算图标大小（dp转px）
                    float density = getContext().getResources().getDisplayMetrics().density;
                    int iconSizePx = Math.round(ICON_SIZE_DP * density);
                    
                    // 设置图标大小
                    icon.setBounds(0, 0, iconSizePx, iconSizePx);
                    
                    // 设置图标到TextView
                    text1.setCompoundDrawables(icon, null, null, null);
                    text1.setCompoundDrawablePadding(10);
                } catch (Exception e) {
                    // 如果加载图标失败，忽略错误
                }
            }
            
            return convertView;
        }
    }
} 