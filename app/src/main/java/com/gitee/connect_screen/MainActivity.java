package com.gitee.connect_screen;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private LinearLayout breadcrumb;
    private LinearLayout buttonGroup;
    private FrameLayout fragmentContainer;
    private List<String> navigationPath = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        
        breadcrumb = findViewById(R.id.breadcrumb);
        buttonGroup = findViewById(R.id.buttonGroup);
        fragmentContainer = findViewById(R.id.fragmentContainer);
        Button virtualScreenBtn = findViewById(R.id.virtualScreenBtn);
        Button usbDeviceBtn = findViewById(R.id.usbDeviceBtn);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new VirtualScreenFragment())
                .addToBackStack(null)
                .commit();
        
        virtualScreenBtn.setOnClickListener(v -> {
            pushBreadcrumb("虚拟屏幕");
            buttonGroup.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new VirtualScreenFragment())
                .addToBackStack(null)
                .commit();
        });
        
        usbDeviceBtn.setOnClickListener(v -> {
            pushBreadcrumb("USB设备");
            buttonGroup.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new UsbFragment())
                .addToBackStack(null)
                .commit();
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        navigationPath.clear();
    }
    
    public void pushBreadcrumb(String newPath) {
        if (!newPath.isEmpty() && !navigationPath.contains(newPath)) {
            navigationPath.add(newPath);
        }
        updateBreadcrumbView();
    }
    
    public void popBreadcrumb() {
        if (!navigationPath.isEmpty()) {
            navigationPath.remove(navigationPath.size() - 1);
        }
        updateBreadcrumbView();
    }
    
    private void updateBreadcrumbView() {
        breadcrumb.removeAllViews();
        
        TextView homeView = new TextView(this);
        homeView.setText("首页");
        homeView.setClickable(true);
        homeView.setOnClickListener(v -> {
            navigationPath.clear();
            buttonGroup.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            breadcrumb.removeAllViews();
            breadcrumb.addView(homeView);
        });
        breadcrumb.addView(homeView);
        
        for (int i = 0; i < navigationPath.size(); i++) {
            TextView separator = new TextView(this);
            separator.setText(" > ");
            breadcrumb.addView(separator);
            
            TextView pathView = new TextView(this);
            pathView.setText(navigationPath.get(i));
            final int index = i;
            pathView.setClickable(true);
            pathView.setOnClickListener(v -> {
                while (navigationPath.size() > index + 1) {
                    navigationPath.remove(navigationPath.size() - 1);
                }
                updateBreadcrumbView();
            });
            breadcrumb.addView(pathView);
        }
    }
    
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            buttonGroup.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            popBreadcrumb();
        } else {
            super.onBackPressed();
        }
    }
} 