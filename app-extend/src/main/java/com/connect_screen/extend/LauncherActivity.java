package com.connect_screen.extend;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.List;

import android.content.res.Resources;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.view.Menu;

import com.connect_screen.extend.shizuku.ShizukuUtils;

import java.util.stream.Collectors;
import java.util.ArrayList;

public class LauncherActivity extends AppCompatActivity {
    // 添加常量定义
    public static final String EXTRA_TARGET_DISPLAY_ID = "target_display_id";
    
    private AppListAdapter adapter;
    private Button floatingButtonToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 获取目标显示器ID
        int displayId = getIntent().getIntExtra(EXTRA_TARGET_DISPLAY_ID, Display.DEFAULT_DISPLAY);
        
        getSupportActionBar().hide();

        setContentView(R.layout.activity_launcher);

        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            finish();
            return;
        }
        floatingButtonToggle = findViewById(R.id.floating_button_toggle);
        if (displayId != Display.DEFAULT_DISPLAY) {
            floatingButtonToggle.setVisibility(View.VISIBLE);
            SharedPreferences appPreferences = this.getSharedPreferences("app_preferences", MODE_PRIVATE);
            updateFloatingBackButtonText(appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false));
            floatingButtonToggle.setOnClickListener(v -> {
                boolean isEnabled = appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false);
                if (isEnabled) {
                    Intent serviceIntent = new Intent(this, FloatingButtonService.class);
                    this.stopService(serviceIntent);
                    isEnabled = false;
                } else {
                    if (FloatingButtonService.startFloating(this, displayId, false)) {
                        isEnabled = true;
                    }
                }
                appPreferences.edit().putBoolean("FLOATING_BUTTON_" + display.getName(), isEnabled).apply();
                updateFloatingBackButtonText(isEnabled);
            });
        }
        Button touchpadButton = findViewById(R.id.btn_touchpad);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R || ShizukuUtils.hasPermission()) {
            touchpadButton.setVisibility(View.VISIBLE);
        }
        touchpadButton.setOnClickListener(v -> {
            TouchpadActivity.startTouchpad(this, displayId, false);
        });
        
        RecyclerView recyclerView = findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 获取已安装的应用并过滤系统应用
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = new ArrayList<>();
        try {
            packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (SecurityException e) {
            Toast.makeText(this, "需要 '查询所有应用' 权限", Toast.LENGTH_LONG).show();
            State.log("查询应用列表失败: " + e);
        }
        
        // 在获取应用列表之前添加菜单按钮逻辑
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> showMenu(v));
        
        // 修改获取应用列表的代码
        boolean showAllApps = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getBoolean("show_all_apps", false);
            
        List<ApplicationInfo> userApps = packages.stream()
            .filter(app -> showAllApps || (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
            .collect(Collectors.toList());
        
        // 设置搜索框监听器
        EditText searchBox = findViewById(R.id.search_box);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });
        
        // 创建适配器时保存引用
        adapter = new AppListAdapter(
            userApps, 
            pm, 
            displayId,
            getSharedPreferences("app_preferences", MODE_PRIVATE)
        );
        recyclerView.setAdapter(adapter);
        
        // 添加设置 DPI 的代码
        Resources resources = getResources();
        Configuration configuration = resources.getConfiguration();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        
        displayMetrics.densityDpi = 320; 
        configuration.densityDpi = 320;
        
        // 应用新的配置
        resources.updateConfiguration(configuration, displayMetrics);

        // 添加模拟熄屏按钮的点击监听器
        findViewById(R.id.btn_screen_off).setOnClickListener(v -> {
            Intent intent = new Intent(this, PureBlackActivity.class);
            ActivityOptions options = ActivityOptions.makeBasic();
            startActivity(intent, options.toBundle());
        });
    }
    private void updateFloatingBackButtonText(boolean isEnabled) {
        floatingButtonToggle.setText(isEnabled ? "关悬浮" : "开悬浮");
    }
    
    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, "显示所有应用")
            .setCheckable(true)
            .setChecked(getSharedPreferences("app_preferences", MODE_PRIVATE)
                .getBoolean("show_all_apps", false));
                
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                item.setChecked(!item.isChecked());
                getSharedPreferences("app_preferences", MODE_PRIVATE)
                    .edit()
                    .putBoolean("show_all_apps", item.isChecked())
                    .apply();
                    
                // 重新加载应用列表
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> packages = new ArrayList<>();
                try {
                    packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                } catch (SecurityException e) {
                    Toast.makeText(this, "需要 '查询所有应用' 权限", Toast.LENGTH_LONG).show();
                    State.log("查询应用列表失败: " + e);
                    return true;
                }
                
                List<ApplicationInfo> filteredApps = packages.stream()
                    .filter(app -> item.isChecked() || (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                    .collect(Collectors.toList());
                    
                adapter.updateAppList(filteredApps);
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    public static void start(Context context, int displayId) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = new ArrayList<>();
        try {
            packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (SecurityException e) {
            Toast.makeText(context, "需要 '查询所有应用' 权限", Toast.LENGTH_LONG).show();
            State.log("查询应用列表失败: " + e);
            return;
        }

        if (packages.size() <= 1) {
            Toast.makeText(context, "无法获取应用列表，请检查权限", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(context, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_TARGET_DISPLAY_ID, displayId);
        context.startActivity(intent);
    }
}