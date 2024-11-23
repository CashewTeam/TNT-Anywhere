package com.gitee.connect_screen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_button_group, container, false);

        Button displayDeviceBtn = view.findViewById(R.id.displayDeviceBtn);
        Button usbDeviceBtn = view.findViewById(R.id.usbDeviceBtn);
        Button aboutBtn = view.findViewById(R.id.aboutBtn);

        displayDeviceBtn.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.pushBreadcrumb("屏幕", () -> new DisplayListFragment());
            }
        });

        usbDeviceBtn.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.pushBreadcrumb("USB设备", () -> new UsbListFragment());
            }
        });

        aboutBtn.setOnClickListener(v -> {
            // 处理关于按钮的点击事件
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.pushBreadcrumb("关于", () -> new AboutFragment());
            }
        });

        return view;
    }
}