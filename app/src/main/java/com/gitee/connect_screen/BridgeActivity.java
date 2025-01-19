package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.MotionEventHidden;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.TextureView;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.job.CreateVirtualDisplay;
import com.gitee.connect_screen.job.VirtualDisplayArgs;
import com.gitee.connect_screen.shizuku.ServiceUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dev.rikka.tools.refine.Refine;

public class BridgeActivity extends AppCompatActivity {

    private static BridgeActivity instance;

    public static BridgeActivity getInstance() {
        return instance;
    }

    private TextureView textureView;

    // 添加坐标调整方法
    private static float[] adjustTouchCoordinates(float x, float y, int rotation,
                                                  int targetWidth, int targetHeight, int sourceWidth, int sourceHeight) {
        // 计算缩放比例
        float scaleX = (float) targetWidth / sourceWidth;
        float scaleY = (float) targetHeight / sourceHeight;

        // 应用缩放
        x *= scaleX;
        y *= scaleY;

        // 根据旋转角度调整坐标
        float[] result = new float[2];
        switch (rotation) {
            case Surface.ROTATION_0:
                result[0] = x;
                result[1] = y;
                break;
            case Surface.ROTATION_90:
                result[0] = y;
                result[1] = targetWidth - x;
                break;
            case Surface.ROTATION_180:
                result[0] = targetWidth - x;
                result[1] = targetHeight - y;
                break;
            case Surface.ROTATION_270:
                result[0] = targetHeight - y;
                result[1] = x;
                break;
        }
        return result;
    }

    public static void stopVirtualDisplay() {
        if (State.bridgeVirtualDisplay == null) {
            return;
        }
        State.bridgeDisplayId = -1;
        State.bridgeVirtualDisplay.release();
        State.bridgeVirtualDisplay = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 设置全屏
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        // 支持刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(layoutParams);
        }

        // 设置状态栏和导航栏透明
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // 创建并设置 TextureView 替代 SurfaceView
        textureView = new TextureView(this);
        VirtualDisplayArgs args = getIntent().getParcelableExtra("virtualDisplayArgs");
        
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                Surface surface = new Surface(surfaceTexture);
                
                if (State.bridgeVirtualDisplay == null) {
                    stopVirtualDisplay();
                    State.bridgeVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(args, surface);
                    State.log("Bridge Activity 创建了新的虚拟显示器");
                } else {
                    State.bridgeVirtualDisplay.setSurface(surface);
                    State.log("Bridge Activity 复用了已有的虚拟显示器");
                }
                
                State.breadcrumbManager.popBreadcrumb();
                Display jumpToDisplay = State.bridgeVirtualDisplay.getDisplay();
                State.breadcrumbManager.pushBreadcrumb("屏幕 " + jumpToDisplay.getDisplayId(), 
                    () -> DisplayDetailFragment.newInstance(jumpToDisplay.getDisplayId()));
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (State.bridgeVirtualDisplay != null) {
                    ImageReader imageReader = ImageReader.newInstance(args.width, args.height, 1, 2);
                    State.bridgeVirtualDisplay.setSurface(imageReader.getSurface());
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
        
        // 修改触摸事件监听器中的引用
        textureView.setOnTouchListener((v, event) -> {
            if (State.bridgeVirtualDisplay != null) {
                // 获取显示ID和旋转角度
                Display virtualDisplay = State.bridgeVirtualDisplay.getDisplay();
                int rotation = virtualDisplay.getRotation();
                int displayId = virtualDisplay.getDisplayId();

                // 获取原始坐标
                float x = event.getX();
                float y = event.getY();
                
                // 根据旋转角度调整坐标
                float[] adjustedCoords = adjustTouchCoordinates(x, y, rotation, 
                    args.width, args.height,
                    textureView.getWidth(), textureView.getHeight());
                
                // 设置调整后的坐标
                event.setLocation(adjustedCoords[0], adjustedCoords[1]);
                
                // 设置显示ID并注入事件
                MotionEventHidden motionEventHidden = Refine.unsafeCast(event);
                motionEventHidden.setDisplayId(displayId);
                try {
                    ServiceUtils.getInputManager().injectInputEvent(event, 0);
                } catch (Exception e) {
                    Log.e("BridgeActivity", "注入触摸事件失败", e);
                }
            }
            return true;
        });
        
        setContentView(textureView);
    }

    @Override
    protected void onDestroy() {
        Log.i("BridgeActivity", "BridgeActivity onDestroy");
        super.onDestroy();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(0);
        this.startActivity(intent, options.toBundle());
        instance = null;
    }
}