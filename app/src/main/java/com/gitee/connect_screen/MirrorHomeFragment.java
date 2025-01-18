package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Context;
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
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.concurrent.TimeUnit;

public class MirrorHomeFragment extends Fragment {
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_home, container, false);

        Button settingsBtn = view.findViewById(R.id.settingsBtn);
        Button exitBtn = view.findViewById(R.id.exitBtn);
        TextView mirrorStatus = view.findViewById(R.id.mirrorStatus);
        if (mirrorStatus != null) {
            mirrorStatus.setText(State.mirrorStatus);
        }

        settingsBtn.setOnClickListener(v -> {
            State.breadcrumbManager.pushBreadcrumb("设置", () -> new MirrorSettingsFragment());
        });

        exitBtn.setOnClickListener(v -> {
            if (State.mediaProjectionInUse != null) {
                State.mediaProjectionInUse.stop();
            }
            // 停止 MediaProjectionService
            Context context = requireContext();
            context.stopService(new Intent(context, MediaProjectionService.class));
            
            // 停止 FloatingButtonService
            context.stopService(new Intent(context, FloatingButtonService.class));
            
            // 停止 TouchpadAccessibilityService
            Intent touchpadIntent = new Intent(context, TouchpadAccessibilityService.class);
            touchpadIntent.setAction(TouchpadAccessibilityService.class.getName());
            context.stopService(touchpadIntent);
            
            // 原有的清理代码
            if (MirrorActivity.getInstance() != null) {
                MirrorActivity.getInstance().finish();
            }
            if (BridgeActivity.getInstance() != null) {
                BridgeActivity.getInstance().finish();
            }
            if (State.bridgeVirtualDisplay != null) {
                State.bridgeVirtualDisplay.release();
                State.bridgeVirtualDisplay = null;  
            }
            if (State.mirrorVirtualDisplay != null) {
                State.mirrorVirtualDisplay.release();
                State.mirrorVirtualDisplay = null;
            }
            State.displaylinkState.stopVirtualDisplay();
            State.displaylinkState.destroy();
            State.currentActivity.get().finish();
        });

        return view;
    }

    private void showHelp() {
        new AlertDialog.Builder(requireContext())
            .setTitle("还没有投屏应用")
            .setMessage(
                    "• USB3.0手机：连接屏幕后进入屏幕列表选择屏幕开始投屏单个应用\n\n" +
                    "• USB2.0手机：点击Displaylink按钮，选择单应用投屏模式\n\n" +
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