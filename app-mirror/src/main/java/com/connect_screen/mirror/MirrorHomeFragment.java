package com.connect_screen.mirror;

import static com.connect_screen.mirror.MirrorSettingsFragment.KEY_SINGLE_APP_MODE;
import static com.connect_screen.mirror.MirrorSettingsFragment.PREF_NAME;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
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
import com.connect_screen.mirror.job.VirtualDisplayArgs;
import com.connect_screen.mirror.shizuku.ServiceUtils;

public class MirrorHomeFragment extends Fragment {
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_home, container, false);

        SharedPreferences preferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean singleAppMode = preferences.getBoolean(KEY_SINGLE_APP_MODE, false);
        Button settingsBtn = view.findViewById(R.id.settingsBtn);
        Button screenOffBtn = view.findViewById(R.id.screenOffBtn);
        Button touchScreenBtn = view.findViewById(R.id.touchScreenBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);
        TextView mirrorStatus = view.findViewById(R.id.mirrorStatus);
        if (State.mirrorVirtualDisplay != null || State.displaylinkState.getVirtualDisplay() != null) {
            mirrorStatus.setText("镜像投屏中，请在系统设置中为屏易连关闭省电，并在任务列表中锁定任务防止被杀");
            screenOffBtn.setVisibility(View.VISIBLE);
            if (singleAppMode) {
                touchScreenBtn.setVisibility(View.VISIBLE);
            }
        } else {
            mirrorStatus.setText("请连接屏幕，如果接口是USB2.0的手机需要Displaylink扩展坞或者Moonlight无线投屏");
            settingsBtn.setVisibility(View.VISIBLE);
            touchScreenBtn.setVisibility(View.VISIBLE);
        }

        settingsBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("设置", () -> new MirrorSettingsFragment());
        });

        screenOffBtn.setOnClickListener(v -> {
            CreateVirtualDisplay.doPowerOffScreen(requireContext());
        });

        touchScreenBtn.setOnClickListener(v -> {
//            ImageReader imageReader = ImageReader.newInstance(1920, 1080, 1, 2);
//            Surface surface = imageReader.getSurface();
//            VirtualDisplay virtualDisplay = CreateVirtualDisplay.createVirtualDisplay(new VirtualDisplayArgs("test", 1920, 1080, 60, 160, true), surface);
//            State.mirrorVirtualDisplay = virtualDisplay;
//            ServiceUtils.launchPackage(requireContext(), "com.microsoft.launcher", virtualDisplay.getDisplay().getDisplayId());
//            // 启动线程读取ImageReader
//            new Thread(() -> {
//                while (true) {
//                    try {
//                        Image image = imageReader.acquireLatestImage();
//                        if (image != null) {
//                            image.close();
//                        }
//                    } catch (Exception e) {
//                        break;
//                    }
//                }
//            }).start();
            Intent intent = new Intent(requireContext(), TouchscreenActivity.class);
            intent.putExtra("surface", State.mirrorVirtualDisplay.getSurface());
            intent.putExtra("display", State.mirrorVirtualDisplay.getDisplay().getDisplayId());
            startActivity(intent);
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