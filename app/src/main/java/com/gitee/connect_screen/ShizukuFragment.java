package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ShizukuFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shizuku, container, false);
        
        TextView descriptionText = view.findViewById(R.id.shizukuDescription);
        descriptionText.setText(
            "Shizuku 是一个帮助应用获取 adb 权限的工具。安卓屏连的 Displaylink 单应用投屏，竖屏旋转，以及绑定外设到指定显示器等功能需要获得 adb 权限才能工作。虚拟触控板，和悬浮返回键用无障碍权限也能工作，但是有 adb 权限之后会工作得更稳定。");

        return view;
    }
} 