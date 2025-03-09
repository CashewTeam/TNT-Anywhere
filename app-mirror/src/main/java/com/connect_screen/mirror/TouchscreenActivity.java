package com.connect_screen.mirror;

import android.graphics.Bitmap;
import android.os.Bundle;
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
    private ImageView imageView;
    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touchscreen);

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        imageView = findViewById(R.id.imageView);
        
        if (getIntent().hasExtra("surface")) {
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
        }
    }
    
    public void captureFromSurface() {
        if (surface == null || !surface.isValid()) {
            Log.e("TouchscreenActivity", "Surface 无效或为空");
            return;
        }
        
        // 获取 Surface 的尺寸
        Rect surfaceRect = new Rect(0, 0, 1920, 1080); // 假设尺寸，实际应该动态获取
        
        // 创建位图来存储捕获的图像
        Bitmap bitmap = Bitmap.createBitmap(
                surfaceRect.width(),
                surfaceRect.height(),
                Bitmap.Config.ARGB_8888
        );
        
        // 使用 PixelCopy API 从 Surface 复制到位图
        PixelCopy.request(
                surface,
                surfaceRect,
                bitmap,
                copyResult -> {
                    if (copyResult == PixelCopy.SUCCESS) {
                        updateImage(bitmap);
                    } else {
                        Log.e("TouchscreenActivity", "PixelCopy 失败: " + copyResult);
                        bitmap.recycle();
                    }
                },
                new Handler(Looper.getMainLooper())
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentBitmap != null) {
            currentBitmap.recycle();
        }
    }
} 