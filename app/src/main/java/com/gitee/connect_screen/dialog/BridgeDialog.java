package com.gitee.connect_screen.dialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AlertDialog;

import com.gitee.connect_screen.BridgePref;
import com.gitee.connect_screen.R;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.job.ProjectViaBridge;
import com.gitee.connect_screen.job.VirtualDisplayArgs;

import static android.content.Context.MODE_PRIVATE;

public class BridgeDialog {
    public static void show(Context context, Display display, int displayId) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bridge, null);
        
        CheckBox rotatesWithContentCheckbox = dialogView.findViewById(R.id.rotatesWithContentCheckbox);
        CheckBox skipMediaProjectionPermissionCheckbox = dialogView.findViewById(R.id.skipMediaProjectionPermissionCheckbox);
        CheckBox autoBridgeCheckbox = dialogView.findViewById(R.id.autoBridgeCheckbox);
        
        BridgePref.load(context);
        rotatesWithContentCheckbox.setChecked(BridgePref.rotatesWithContent);
        skipMediaProjectionPermissionCheckbox.setChecked(BridgePref.skipMediaProjectionPermission);
        SharedPreferences appPreferences = context.getSharedPreferences("app_preferences", MODE_PRIVATE);
        autoBridgeCheckbox.setChecked(appPreferences.getBoolean("AUTO_BRIDGE_" + display.getName(), false));

        new AlertDialog.Builder(context)
                .setTitle("桥接设置")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    BridgePref.rotatesWithContent = rotatesWithContentCheckbox.isChecked();
                    BridgePref.skipMediaProjectionPermission = skipMediaProjectionPermissionCheckbox.isChecked();
                    BridgePref.save(context);
                    boolean autoBridge = autoBridgeCheckbox.isChecked();
                    appPreferences.edit().putBoolean("AUTO_BRIDGE_" + display.getName(), autoBridge).apply();
                    if (autoBridge) {
                        appPreferences.edit().putBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), false).apply();
                    }
                    State.startNewJob(new ProjectViaBridge(displayId, new VirtualDisplayArgs(
                            "桥接屏幕", 
                            display.getWidth(), 
                            display.getHeight(), 
                            display.getWidth(), 
                            (int) display.getRefreshRate(), 
                            BridgePref.rotatesWithContent)));
                })
                .setNegativeButton("取消", null)
                .show();
    }
} 