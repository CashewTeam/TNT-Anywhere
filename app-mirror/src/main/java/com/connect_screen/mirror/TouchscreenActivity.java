package com.connect_screen.mirror;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Display;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.graphics.Rect;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class TouchscreenActivity extends AppCompatActivity {
    private Surface surface;
    private int displayId;
    private ImageView imageView;
    private Bitmap currentBitmap;
    private Bitmap bufferBitmap;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // 添加计数器和时间记录变量
    private int updateCounter = 0;
    private long startTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touchscreen);

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        imageView = findViewById(R.id.imageView);


        if (getIntent().hasExtra("display")) {
            displayId = getIntent().getIntExtra("display", -1);
        }

        if (getIntent().hasExtra("surface")) {
            // the surface of the display
            surface = getIntent().getParcelableExtra("surface");
        }

        // 添加长按退出功能
        TextView exitText = findViewById(R.id.exitText);
        exitText.setOnLongClickListener(v -> {
            finish();
            return true;
        });

        captureFromSurface();
    }

    public void updateImage(Bitmap bitmap) {
        if (bitmap != null) {
            // 回收旧的位图以避免内存泄漏
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
            }
            
            currentBitmap = bitmap;
            imageView.setImageBitmap(currentBitmap);
            
            // 更新计数器并记录时间
            if (updateCounter == 0) {
                startTime = System.currentTimeMillis();
            }
            
            updateCounter++;
            
            if (updateCounter >= 60) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                Log.d("TouchscreenActivity", "60次图像更新耗时: " + duration + "毫秒，平均每次: " + (duration / 60.0) + "毫秒");
                updateCounter = 0;
            }
        }
    }
    
    public void captureFromSurface() {
        if (surface == null || !surface.isValid()) {
            Log.e("TouchscreenActivity", "Surface 无效或为空");
            return;
        }
        
        // 获取 Surface 的尺寸
        Rect surfaceRect = new Rect(0, 0, 1920, 1080); // 假设尺寸，实际应该动态获取
        
        // 初始化或复用 bufferBitmap
        if (bufferBitmap == null || bufferBitmap.isRecycled()) {
            bufferBitmap = Bitmap.createBitmap(
                    surfaceRect.width(),
                    surfaceRect.height(),
                    Bitmap.Config.ARGB_8888
            );
        }
        
        // 使用 PixelCopy API 从 Surface 复制到位图
        PixelCopy.request(
                surface,
                surfaceRect,
                bufferBitmap,
                copyResult -> {
                    if (copyResult == PixelCopy.SUCCESS) {
                        // 交换bitmap
                        Bitmap temp = currentBitmap;
                        updateImage(bufferBitmap);
                        bufferBitmap = temp;
                        
                        // 安排下一次捕获
                        captureFromSurface();
                    } else {
                        if (copyResult == 3) {
                            handler.postDelayed(this::captureFromSurface, 1000);
                        } else {
                            Log.e("TouchscreenActivity", "PixelCopy 失败: " + copyResult);
                        }
                    }
                },
                handler
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理两个 bitmap
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        if (bufferBitmap != null && !bufferBitmap.isRecycled()) {
            bufferBitmap.recycle();
            bufferBitmap = null;
        }
        // 移除所有回调
        handler.removeCallbacksAndMessages(null);
    }
} 