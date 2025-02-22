package com.connect_screen.extend.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;


import com.connect_screen.extend.R;
import com.connect_screen.extend.State;
import com.connect_screen.extend.job.ChangeResolution;

public class ResolutionDialog {
    public static void show(Context context, int displayId, int currentWidth, int currentHeight) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_resolution, null);
        EditText widthInput = dialogView.findViewById(R.id.width_input);
        EditText heightInput = dialogView.findViewById(R.id.height_input);
        
        widthInput.setText(String.valueOf(currentWidth));
        heightInput.setText(String.valueOf(currentHeight));

        new AlertDialog.Builder(context)
                .setTitle("修改分辨率")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int newWidth = Integer.parseInt(widthInput.getText().toString());
                        int newHeight = Integer.parseInt(heightInput.getText().toString());
                        
                        if (newWidth <= 0 || newHeight <= 0) {
                            Toast.makeText(context, "请输入有效的分辨率", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        State.startNewJob(new ChangeResolution(displayId, newWidth, newHeight));
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
} 