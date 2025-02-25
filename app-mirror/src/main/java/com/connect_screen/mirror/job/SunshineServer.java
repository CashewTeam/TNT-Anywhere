package com.connect_screen.mirror.job;

import android.app.Activity;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Display;
import android.view.Surface;
import android.widget.EditText;
import android.widget.Toast;

import com.connect_screen.mirror.State;

// 代码拷贝自 v2025.122.141614
public class SunshineServer {
    static {
        System.loadLibrary("sunshine");
    }

    public static native void start();
    
    public static native void setPkeyPath(String path);
    public static native void setCertPath(String path);
    public static native void setFileStatePath(String path);
    
    // 添加新的回调方法，当需要 PIN 码时被 C++ 代码调用
    public static void onPinRequested() {
        // 使用 Handler 将回调切换到主线程
        new Handler(Looper.getMainLooper()).post(() -> {
            Context context = State.currentActivity.get();
            if (context == null) {
                return;
            }
            
            // 创建一个输入框
            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            // 限制输入长度为4位
            InputFilter[] filters = new InputFilter[1];
            filters[0] = new InputFilter.LengthFilter(4);
            input.setFilters(filters);
            
            // 创建对话框
            // AlertDialog.Builder builder = new AlertDialog.Builder(context);
            // builder.setTitle("请输入PIN码")
            //        .setMessage("请输入4位数字PIN码")
            //        .setView(input)
            //        .setPositiveButton("确定", (dialog, which) -> {
            //            String pin = input.getText().toString();
            //            if (pin.length() == 4) {
            //                // 这里添加处理PIN码的逻辑
            //                // 例如：调用native方法将PIN码传递给C++代码
            //                submitPin(pin);
            //            } else {
            //                Toast.makeText(context, "请输入4位数字PIN码", Toast.LENGTH_SHORT).show();
            //            }
            //        })
            //        .setNegativeButton("取消", (dialog, which) -> dialog.cancel())
            //        .show();
            submitPin("1234");
        });
    }
    
    // 添加提交PIN码的native方法
    public static native void submitPin(String pin);

    
    public static void createVirtualDisplay(Surface surface) {
        new Handler(Looper.getMainLooper()).post(() -> {
            State.startNewJob(new ProjectViaMoonlight(surface));
        });
    }
}
