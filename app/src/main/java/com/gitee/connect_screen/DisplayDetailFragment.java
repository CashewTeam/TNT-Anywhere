package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.R;

public class DisplayDetailFragment extends Fragment {
    private static final String ARG_DISPLAY_ID = "display_id";
    
    public static DisplayDetailFragment newInstance(int displayId) {
        DisplayDetailFragment fragment = new DisplayDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DISPLAY_ID, displayId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_display_detail, container, false);
        
        int displayId = getArguments().getInt(ARG_DISPLAY_ID);
        DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(displayId);
        
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        
        TextView detailText = view.findViewById(R.id.detail_text);
        String details = String.format(
            "显示器 ID: %d\n" +
            "名称: %s\n" +
            "分辨率: %dx%d\n" +
            "刷新率: %.1f Hz\n" +
            "DPI: %d\n" +
            "状态: %s\n" +
            "HDR支持: %s",
            display.getDisplayId(),
            display.getName(),
            display.getWidth(),
            display.getHeight(),
            display.getRefreshRate(),
            metrics.densityDpi,
            display.getState() == Display.STATE_ON ? "开启" : "关闭",
            display.isHdr() ? "是" : "否"
        );
        detailText.setText(details);
        
        Button launchButton = view.findViewById(R.id.launch_demo_button);
        launchButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), DemoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            getContext().startActivity(intent, options.toBundle());
        });
        
        return view;
    }
}