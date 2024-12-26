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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        TextView shizukuStatusPrefix = view.findViewById(R.id.shizukuStatusPrefix);
        shizukuStatusPrefix.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        shizukuStatusPrefix.setPaintFlags(shizukuStatusPrefix.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        shizukuStatusPrefix.setOnClickListener(v -> {
            String url = "https://shizuku.rikka.app/";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        });

        // 添加授权按钮
        Button shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> {
            State.startNewJob(new AcquireShizuku());
        });

        // 更新Shizuku状态
        TextView shizukuStatus = view.findViewById(R.id.shizukuStatus);
        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);

        Button displayDeviceBtn = view.findViewById(R.id.displayDeviceBtn);
        Button displaylinkBtn = view.findViewById(R.id.displaylinkBtn);
        Button simulateScreenOffBtn = view.findViewById(R.id.simulateScreenOffBtn);
        Button inputDeviceBtn = view.findViewById(R.id.inputDeviceBtn);
        Button aboutBtn = view.findViewById(R.id.aboutBtn);

        displayDeviceBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("屏幕", () -> new DisplayListFragment());
        });

        displaylinkBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("Displaylink", () -> new DisplaylinkFragment());
        });

        simulateScreenOffBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PureBlackActivity.class);
            ActivityOptions options = ActivityOptions.makeBasic();
            startActivity(intent, options.toBundle());
        });

        inputDeviceBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("输入设备", () -> new InputDeviceListFragment());
        });

        aboutBtn.setOnClickListener(v -> {
            // 处理关于按钮的点击事件
            State.breadcrumbManager.pushBreadcrumb("关于", () -> new AboutFragment());
        });

        return view;
    }

    private void updateShizukuStatus(TextView statusView, Button permissionBtn) {
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();
        
        String status;
        if (!started) {
            status = "未启动";
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            status = "已启动未授权";
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            status = "已授权";
            permissionBtn.setVisibility(View.GONE);
        }
        
        statusView.setText(status);
    }
}