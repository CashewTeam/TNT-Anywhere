package com.connect_screen.mirror;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.connect_screen.mirror.job.CreateVirtualDisplay;
import com.connect_screen.mirror.job.ExitAll;
import com.connect_screen.mirror.job.AutoRotateAndScaleForDisplaylink;

public class MirrorHomeFragment extends Fragment {
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_home, container, false);

        Button settingsBtn = view.findViewById(R.id.settingsBtn);
        Button screenOffBtn = view.findViewById(R.id.screenOffBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);
        TextView mirrorStatus = view.findViewById(R.id.mirrorStatus);
        if (State.mirrorVirtualDisplay != null || State.displaylinkState.getVirtualDisplay() != null) {
            mirrorStatus.setText("镜像投屏中，请在系统设置中为屏易连关闭省电，并在任务列表中锁定任务防止被杀");
            screenOffBtn.setVisibility(View.VISIBLE);
        } else {
            mirrorStatus.setText("请连接屏幕，如果接口是USB2.0的手机需要Displaylink扩展坞或者Moonlight无线投屏");
            settingsBtn.setVisibility(View.VISIBLE);
        }

        settingsBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("设置", () -> new MirrorSettingsFragment());
        });

        screenOffBtn.setOnClickListener(v -> {
            CreateVirtualDisplay.doPowerOffScreen(requireContext());
        });

        exitBtn.setOnClickListener(v -> {
            if (AutoRotateAndScaleForDisplaylink.instance != null) {
                AutoRotateAndScaleForDisplaylink.instance.release();
            }
            ExitAll.execute(requireContext(), false);
        });

        return view;
    }
}