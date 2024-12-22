package com.gitee.connect_screen;

import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.List;
import android.view.WindowManager;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.stream.Collectors;
import java.util.ArrayList;

public class LauncherActivity extends AppCompatActivity {
    // 添加常量定义
    public static final String EXTRA_TARGET_DISPLAY_ID = "target_display_id";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 获取目标显示器ID
        int targetDisplayId = getIntent().getIntExtra(EXTRA_TARGET_DISPLAY_ID, Display.DEFAULT_DISPLAY);
        
        getSupportActionBar().hide();

        setContentView(R.layout.activity_launcher);
        
        // 添加退出按钮的点击监听器
        findViewById(R.id.btn_exit).setOnClickListener(v -> finish());

        Button touchpadButton = findViewById(R.id.btn_touchpad);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R || ShizukuUtils.hasPermission()) {
            touchpadButton.setVisibility(View.VISIBLE);
        }
        touchpadButton.setOnClickListener(v -> {
            TouchpadActivity.startTouchpad(this, targetDisplayId, false);
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
        
        // 过滤掉系统应用
        List<ApplicationInfo> userApps = packages.stream()
            .filter(app -> (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
            .collect(Collectors.toList());
        
        // 创建适配器时传入目标显示器ID和 SharedPreferences
        AppListAdapter adapter = new AppListAdapter(
            userApps, 
            pm, 
            targetDisplayId,
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
    }
}