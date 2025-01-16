package com.gitee.connect_screen;

import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;

import androidx.appcompat.app.AppCompatActivity;

import android.view.Window;
import android.view.WindowManager;

import com.gitee.connect_screen.job.CreateVirtualDisplay;
import com.gitee.connect_screen.job.VirtualDisplayArgs;

import java.util.zip.Deflater;

public class MirrorActivity extends AppCompatActivity {
    
    private static MirrorActivity instance;
    private TextureView textureView;
    
    public static void stopVirtualDisplay() {
        if (State.mirrorVirtualDisplay == null) {
            return;
        }
        State.mirrorDisplayId = -1;
        State.mirrorVirtualDisplay.release();
        State.mirrorVirtualDisplay = null;
    }

    public static MirrorActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        // 隐藏标题栏
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }

        // 设置全屏
//        getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN);
//
//        Window window = getWindow();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            window.setDecorFitsSystemWindows(false);
//        }
//
//        // 支持刘海屏
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            WindowManager.LayoutParams layoutParams = window.getAttributes();
//            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
//            window.setAttributes(layoutParams);
//        }
//
//        // 设置状态栏和导航栏透明
//        window.setStatusBarColor(Color.TRANSPARENT);
//        window.setNavigationBarColor(Color.TRANSPARENT);

        // 创建 TextureView 实例
        textureView = new TextureView(this);

        VirtualDisplayArgs args = getIntent().getParcelableExtra("virtualDisplayArgs");

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                State.log("on surface texture available");
                Surface surface = new Surface(surfaceTexture);
                
                if (State.mirrorVirtualDisplay == null) {
                    stopVirtualDisplay();
                    State.mirrorVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(args, surface);
                    State.log("Mirror Activity 创建了新的虚拟显示器");
                } else {
                    State.mirrorVirtualDisplay.setSurface(surface);
                    State.log("Mirror Activity 复用了已有的虚拟显示器");
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (State.mirrorVirtualDisplay != null) {
                    ImageReader imageReader = ImageReader.newInstance(args.monitorWidth, args.monitorHeight, 1, 2);
                    State.mirrorVirtualDisplay.setSurface(imageReader.getSurface());
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        // 直接将 TextureView 设置为 content view
        setContentView(textureView);
        State.log("MirrorActivity created");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        State.log("MirrorActivity destroyed");
    }

}