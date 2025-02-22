package com.connect_screen.extend.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;


import com.connect_screen.extend.R;
import com.connect_screen.extend.State;
import com.connect_screen.extend.job.ChangeRotation;

public class RotationDialog {
    public interface OnRotationSelectedListener {
        void onRotationSelected(int rotation);
    }

    public static void show(Context context, int displayId) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_rotation, null);
        Spinner rotationSpinner = dialogView.findViewById(R.id.rotation_spinner);
        
        String[] rotationOptions = new String[]{"不强制", "0°", "90°", "180°", "270°"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            context,
            android.R.layout.simple_spinner_item,
            rotationOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rotationSpinner.setAdapter(adapter);

        new AlertDialog.Builder(context)
                .setTitle("修改旋转方向（仅在安卓15上测试有效）")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    int position = rotationSpinner.getSelectedItemPosition();
                    int rotation;
                    switch (position) {
                        case 0: rotation = -1; break;
                        case 1: rotation = Surface.ROTATION_0; break;
                        case 2: rotation = Surface.ROTATION_90; break;
                        case 3: rotation = Surface.ROTATION_180; break;
                        case 4: rotation = Surface.ROTATION_270; break;
                        default: rotation = -1;
                    }
                    State.startNewJob(new ChangeRotation(displayId, rotation));
                })
                .setNegativeButton("取消", null)
                .show();
    }
} 