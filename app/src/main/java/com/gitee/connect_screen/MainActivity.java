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


public class MainActivity extends AppCompatActivity {
    private LinearLayout breadcrumb;
    private LinearLayout buttonGroup;
    private FrameLayout fragmentContainer;

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
            // 更新面包屑
            updateBreadcrumb("虚拟屏幕");
            
            // 隐藏按钮组，显示Fragment容器
            buttonGroup.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            
            // 切换到虚拟屏幕Fragment
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new VirtualScreenFragment())
                .addToBackStack(null)
                .commit();
        });
        
        usbDeviceBtn.setOnClickListener(v -> {
            // 更新面包屑
            updateBreadcrumb("USB设备");
            
            // 隐藏按钮组，显示Fragment容器
            buttonGroup.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            
            // 切换到USB Fragment
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
    }
    
    private void updateBreadcrumb(String newPath) {
        // 清除除首页外的所有面包屑
        int childCount = breadcrumb.getChildCount();
        if (childCount > 1) {
            breadcrumb.removeViews(1, childCount - 1);
        }
        
        // 添加分隔符
        TextView separator = new TextView(this);
        separator.setText(" > ");
        breadcrumb.addView(separator);
        
        // 添加新路径
        TextView pathView = new TextView(this);
        pathView.setText(newPath);
        breadcrumb.addView(pathView);
    }
    
    // 处理返回事件
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // 如果Fragment栈中有内容，执行返回
            getSupportFragmentManager().popBackStack();
            // 显示按钮组，隐藏Fragment容器
            buttonGroup.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            // 更新面包屑回到首页状态
            updateBreadcrumb("");
        } else {
            super.onBackPressed();
        }
    }
} 