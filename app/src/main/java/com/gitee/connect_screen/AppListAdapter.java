package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.shizuku.ServiceUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    private static final String LAUNCH_TIME_PREFIX = "launch_time_";
    private List<ApplicationInfo> appList;
    private PackageManager packageManager;
    private int targetDisplayId;
    private SharedPreferences sharedPreferences;

    public AppListAdapter(List<ApplicationInfo> appList, PackageManager packageManager, int targetDisplayId, SharedPreferences sharedPreferences) {
        this.appList = appList;
        this.packageManager = packageManager;
        this.targetDisplayId = targetDisplayId;
        this.sharedPreferences = sharedPreferences;
        sortAppList();
    }

    private void sortAppList() {
        Collections.sort(appList, (app1, app2) -> {
            Long time1 = sharedPreferences.getLong(LAUNCH_TIME_PREFIX + app1.packageName, 0L);
            Long time2 = sharedPreferences.getLong(LAUNCH_TIME_PREFIX + app2.packageName, 0L);
            
            if (time1.equals(time2)) {
                return app1.loadLabel(packageManager)
                    .toString()
                    .compareToIgnoreCase(app2.loadLabel(packageManager).toString());
            }
            return time2.compareTo(time1);
        });
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ApplicationInfo app = appList.get(position);
        holder.text1.setText(app.loadLabel(packageManager));
        holder.text2.setText(app.packageName);
        
        holder.btnLaunch.setOnClickListener(v -> {
            State.lastPackageName = app.packageName;
            sharedPreferences.edit()
                .putLong(LAUNCH_TIME_PREFIX + app.packageName, System.currentTimeMillis())
                .apply();
            if (hasShizukuPermission()) {
                launchAppWithShizuku(app.packageName, v.getContext());
            } else {
                launchAppNormally(app.packageName, v.getContext());
            }
        });
        holder.btnLaunchToDefaultDisplay.setOnClickListener(v -> {
            Intent launchIntent = packageManager.getLaunchIntentForPackage(app.packageName);
            if (launchIntent != null) {
                sharedPreferences.edit()
                    .putLong(LAUNCH_TIME_PREFIX + app.packageName, System.currentTimeMillis())
                    .apply();
                ActivityOptions options = ActivityOptions.makeBasic();
                v.getContext().startActivity(launchIntent, options.toBundle());
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    private boolean hasShizukuPermission() {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void launchAppWithShizuku(String packageName, Context context) {
        try {
            ServiceUtils.initWithShizuku();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            ComponentName componentName = packageManager.getLaunchIntentForPackage(packageName).getComponent();
            intent.setComponent(componentName);
            intent.setPackage(packageName);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            int result = ServiceUtils.startActivity(intent, options);
            if (result < 0) {
                Toast.makeText(context, "使用 Shizuku 启动应用失败", Toast.LENGTH_SHORT).show();
                State.log("使用 Shizuku 启动应用失败，返回值: " + result);
            } else {
                State.log("使用 Shizuku 启动应用成功: " + packageName);
            }
        } catch (Exception e) {
            Toast.makeText(context, "使用 Shizuku 启动应用失败", Toast.LENGTH_SHORT).show();
            State.log("使用 Shizuku 启动应用失败: " + e.getMessage());
        }
    }

    private void launchAppNormally(String packageName, Context context) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            context.startActivity(launchIntent, options.toBundle());
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1;
        TextView text2;
        Button btnLaunch;
        Button btnLaunchToDefaultDisplay;

        ViewHolder(View view) {
            super(view);
            text1 = view.findViewById(R.id.text1);
            text2 = view.findViewById(R.id.text2);
            btnLaunch = view.findViewById(R.id.btn_launch);
            btnLaunchToDefaultDisplay = view.findViewById(R.id.btn_launch_to_default_display);
        }
    }
}