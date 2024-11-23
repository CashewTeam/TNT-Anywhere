package com.gitee.connect_screen;

import android.os.Bundle;
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
import java.util.stream.Collectors;

public class LauncherActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        
        getWindow().setDecorFitsSystemWindows(false);
        
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(lp);
        
        getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        
        setContentView(R.layout.activity_launcher);
        
        TextView textView = findViewById(R.id.demo_text);
        textView.setText("已安装的应用列表");
        
        RecyclerView recyclerView = findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 获取已安装的应用并过滤系统应用
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        // 过滤掉系统应用
        List<ApplicationInfo> userApps = packages.stream()
            .filter(app -> (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
            .collect(Collectors.toList());
        
        // 创建适配器（需要另外实现）
        AppListAdapter adapter = new AppListAdapter(userApps, pm);
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