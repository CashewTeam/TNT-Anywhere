package com.connect_screen.extend.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;


import com.connect_screen.extend.R;
import com.connect_screen.extend.State;
import com.connect_screen.extend.job.ChangeDPI;

public class DpiDialog {
    public static void show(Context context, int displayId, int currentDpi) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_dpi, null);
        EditText dpiInput = dialogView.findViewById(R.id.dpi_input);
        
        dpiInput.setText(String.valueOf(currentDpi));

        new AlertDialog.Builder(context)
                .setTitle("修改DPI")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int newDpi = Integer.parseInt(dpiInput.getText().toString());
                        
                        if (newDpi <= 0) {
                            Toast.makeText(context, "请输入有效的DPI值", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        State.startNewJob(new ChangeDPI(displayId, newDpi, currentDpi));
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
} 