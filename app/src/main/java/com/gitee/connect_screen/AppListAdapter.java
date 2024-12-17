package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.method.Touch;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;


import com.gitee.connect_screen.job.BindAllExternalInputToDisplay;

import java.util.List;
import java.util.Collections;

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
            sharedPreferences.edit()
                    .putString("LAST_PACKAGE_NAME", app.packageName)
                    .apply();
            State.lastSingleAppDisplay = targetDisplayId;
            sharedPreferences.edit()
                .putLong(LAUNCH_TIME_PREFIX + app.packageName, System.currentTimeMillis())
                .apply();
            launchAppNormally(app.packageName, v.getContext());
            if (TouchpadActivity.startTouchpad(v.getContext(), targetDisplayId, true)) {
                TouchpadActivity.startTouchpad(v.getContext(), targetDisplayId, false);
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

    private void launchAppNormally(String packageName, Context context) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            context.startActivity(launchIntent, options.toBundle());
            State.startNewJob(new BindAllExternalInputToDisplay(targetDisplayId));
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