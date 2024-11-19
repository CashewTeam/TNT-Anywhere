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

        Button virtualScreenBtn = view.findViewById(R.id.virtualScreenBtn);
        Button usbDeviceBtn = view.findViewById(R.id.usbDeviceBtn);
        Button aboutBtn = view.findViewById(R.id.aboutBtn);

        // 暂时还不需要独立创建虚拟显示器
        virtualScreenBtn.setVisibility(View.GONE);
        virtualScreenBtn.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.pushBreadcrumb("虚拟屏幕", () -> new VirtualScreenFragment());
            }
        });

        usbDeviceBtn.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.pushBreadcrumb("USB设备", () -> new UsbFragment());
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