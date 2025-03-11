package com.connect_screen.extend;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.connect_screen.extend.job.AcquireShizuku;
import com.connect_screen.extend.job.ExitAll;
import com.connect_screen.extend.shizuku.ShizukuUtils;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        TextView shizukuStatusPrefix = view.findViewById(R.id.shizukuStatusPrefix);
        shizukuStatusPrefix.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        shizukuStatusPrefix.setPaintFlags(shizukuStatusPrefix.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        shizukuStatusPrefix.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("什么是 Shizuku?", () -> new ShizukuFragment());
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
        Button simulateScreenOffBtn = view.findViewById(R.id.simulateScreenOffBtn);
        Button touchpadBtn = view.findViewById(R.id.touchpadBtn);
        Button inputDeviceBtn = view.findViewById(R.id.inputDeviceBtn);
        Button shizukuBtn = view.findViewById(R.id.shizukuBtn);
        Button aboutBtn = view.findViewById(R.id.aboutBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);

        exitBtn.setOnClickListener(v -> {
            ExitAll.execute(requireContext());
        });

        displayDeviceBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("屏幕", () -> new DisplayListFragment());
        });

        boolean useRealScreenOff = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("use_real_screen_off", false);
        if(useRealScreenOff) {
            simulateScreenOffBtn.setText("真实熄屏");
        }
        simulateScreenOffBtn.setOnClickListener(v -> {
            if (State.lastSingleAppDisplay <= 0) {
                showHelp();
            } else {
                Intent intent = new Intent(getActivity(), PureBlackActivity.class);
                ActivityOptions options = ActivityOptions.makeBasic();
                startActivity(intent, options.toBundle());
            }
        });

        touchpadBtn.setOnClickListener(v -> {
            if (State.lastSingleAppDisplay <= 0) {
                showHelp();
            } else {
                TouchpadActivity.startTouchpad(getContext(), State.lastSingleAppDisplay, false);
            }
        });

        inputDeviceBtn.setOnClickListener(v -> {
            if (ShizukuUtils.hasPermission()) {
                State.breadcrumbManager.pushBreadcrumb("设置", () -> new SettingsFragment());
            } else {
                Toast.makeText(requireContext(), "需要先授权 Shizuku", Toast.LENGTH_SHORT).show();
            }
        });

        shizukuBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("Shizuku", () -> new ShizukuFragment());
        });

        aboutBtn.setOnClickListener(v -> {
            // 处理关于按钮的点击事件
            State.breadcrumbManager.pushBreadcrumb("关于", () -> new AboutFragment());
        });

        return view;
    }

    private void showHelp() {
        new AlertDialog.Builder(requireContext())
            .setTitle("还没有投屏应用")
            .setMessage(
                    "• USB3.0手机：连接屏幕后进入屏幕列表选择屏幕开始投屏单个应用\n\n" +
                    "• 无线方式：使用安卓自带的无线投屏，然后进入屏幕列表找到无线屏幕，进行单应用投屏")
            .setPositiveButton("知道了", null)
            .show();
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