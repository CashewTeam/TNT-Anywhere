package com.connect_screen.extend.dialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AlertDialog;

import com.connect_screen.extend.BridgePref;

import com.connect_screen.extend.R;
import com.connect_screen.extend.State;
import com.connect_screen.extend.job.ProjectViaBridge;
import com.connect_screen.extend.job.VirtualDisplayArgs;
import com.connect_screen.extend.shizuku.ServiceUtils;

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

        Point initialSize = new Point();
        ServiceUtils.getWindowManager().getInitialDisplaySize(displayId, initialSize);
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

                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getMetrics(metrics);
                    State.startNewJob(new ProjectViaBridge(display, new VirtualDisplayArgs(
                            "桥接屏幕",
                            initialSize.x,
                            initialSize.y,
                            (int) display.getRefreshRate(),
                            metrics.densityDpi,
                            BridgePref.rotatesWithContent)));
                })
                .setNegativeButton("取消", null)
                .show();
    }
} 