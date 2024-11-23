package com.gitee.connect_screen;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    private List<ApplicationInfo> appList;
    private PackageManager packageManager;

    public AppListAdapter(List<ApplicationInfo> appList, PackageManager packageManager) {
        this.appList = appList;
        this.packageManager = packageManager;
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
            Intent launchIntent = packageManager.getLaunchIntentForPackage(app.packageName);
            if (launchIntent != null) {
                v.getContext().startActivity(launchIntent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1;
        TextView text2;
        Button btnLaunch;

        ViewHolder(View view) {
            super(view);
            text1 = view.findViewById(R.id.text1);
            text2 = view.findViewById(R.id.text2);
            btnLaunch = view.findViewById(R.id.btn_launch);
        }
    }
}