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
import android.graphics.Matrix;
import android.view.WindowManager;
import android.view.View;
import android.graphics.Canvas;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

public class TouchscreenActivity extends AppCompatActivity {
    private Surface surface;
    private int displayId;
    private DirectDrawView drawView;
    private Bitmap currentBitmap;
    private Bitmap bufferBitmap;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // 添加计数器和时间记录变量
    private int updateCounter = 0;
    private long startTime = 0;
    private Display display;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 锁定屏幕方向为竖屏
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // 使用代码创建布局而不是加载XML
        RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));
        
        // 创建DirectDrawView
        drawView = new DirectDrawView(this);
        RelativeLayout.LayoutParams drawViewParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        drawView.setId(View.generateViewId());
        rootLayout.addView(drawView, drawViewParams);
        
        // 创建退出文本
        TextView exitText = new TextView(this);
        exitText.setId(View.generateViewId());
        RelativeLayout.LayoutParams exitTextParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        exitTextParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        exitTextParams.setMargins(0, 0, 0, 16);
        exitText.setGravity(android.view.Gravity.CENTER);
        exitText.setPadding(8, 8, 8, 8);
        exitText.setText("长按退出");
        rootLayout.addView(exitText, exitTextParams);
        
        // 更新drawView的布局参数，使其位于exitText上方
        drawViewParams.addRule(RelativeLayout.ABOVE, exitText.getId());
        drawView.setLayoutParams(drawViewParams);
        
        // 设置内容视图为代码创建的布局
        setContentView(rootLayout);

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (getIntent().hasExtra("display")) {
            displayId = getIntent().getIntExtra("display", -1);
            // 获取Display对象
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display = getDisplay();
            } else {
                display = wm.getDefaultDisplay();
            }
        }

        if (getIntent().hasExtra("surface")) {
            // the surface of the display
            surface = getIntent().getParcelableExtra("surface");
        }

        // 添加长按退出功能
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
            
            // 触发重绘而不是设置 ImageView
            drawView.setBitmap(currentBitmap);
            drawView.invalidate();
            
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

        if (finished) {
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
        // 移除所有回调
        finished = true;
        // 清理两个 bitmap
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        if (bufferBitmap != null && !bufferBitmap.isRecycled()) {
            bufferBitmap.recycle();
            bufferBitmap = null;
        }
    }
    
    // 添加直接绘制 Bitmap 的自定义 View
    public static class DirectDrawView extends View {
        private Bitmap bitmap;
        
        public DirectDrawView(android.content.Context context) {
            super(context);
        }
        
        public DirectDrawView(android.content.Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
        }
        
        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap != null && !bitmap.isRecycled()) {
                // 获取视图和位图的尺寸
                int viewWidth = getWidth();
                int viewHeight = getHeight();
                int bitmapWidth = bitmap.getWidth();
                int bitmapHeight = bitmap.getHeight();
                
                // 创建矩阵进行变换
                Matrix matrix = new Matrix();
                
                // 计算缩放比例以适应视图
                float scale;
                if (bitmapWidth > bitmapHeight) {
                    // 横屏输入
                    scale = Math.min((float) viewHeight / bitmapWidth, (float) viewWidth / bitmapHeight);
                    
                    // 旋转90度
                    matrix.postRotate(90);
                    
                    // 移动到正确位置
                    matrix.postTranslate(bitmapHeight * scale, 0);
                } else {
                    // 竖屏输入
                    scale = Math.min((float) viewWidth / bitmapWidth, (float) viewHeight / bitmapHeight);
                }
                
                // 应用缩放
                matrix.postScale(scale, scale);
                
                // 居中显示
                float dx = (viewWidth - bitmapWidth * scale) / 2;
                float dy = (viewHeight - bitmapHeight * scale) / 2;
                if (bitmapWidth > bitmapHeight) {
                    // 横屏输入已经旋转，需要调整偏移量
                    dy = (viewHeight - bitmapWidth * scale) / 2;
                } else {
                    dx = (viewWidth - bitmapWidth * scale) / 2;
                    dy = (viewHeight - bitmapHeight * scale) / 2;
                }
                matrix.postTranslate(dx, dy);
                
                // 使用矩阵绘制位图
                canvas.drawBitmap(bitmap, matrix, null);
            }
        }
    }
} 