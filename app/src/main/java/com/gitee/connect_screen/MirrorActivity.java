package com.gitee.connect_screen;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.appcompat.app.AppCompatActivity;

import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.view.Window;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MirrorActivity extends AppCompatActivity {
    
    private static MirrorActivity instance;
    private SurfaceView surfaceView;
    private int portraitInputTextureId = -1;
    private SurfaceTexture portraitInputSurfaceTexture = null;
    private Surface portraitInputSurface = null;
    private Handler renderHandler;
    private HandlerThread renderThread;

    private EGLDisplay eglDisplay;
    private android.opengl.EGLSurface eglOutputSurface;
    private android.opengl.EGLContext eglContext;
    private android.opengl.EGLConfig eglConfig;
    private PortraitRenderer portraitRenderer;

    private int landscapeInputTextureId = -1;
    private SurfaceTexture landscapeInputSurfaceTexture = null;
    private Surface landscapeInputSurface = null;
    private PortraitRenderer landscapeRenderer;

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

    private class OrientationChangeCallback implements DisplayManager.DisplayListener {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                renderHandler.post(() -> {
                    DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                    Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getRealMetrics(metrics);
                    
                    boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
                    Surface targetSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
                    
                    if (State.mirrorVirtualDisplay != null) {
                        if (isLandscape) {
                            State.mirrorVirtualDisplay.resize(surfaceView.getWidth(), surfaceView.getHeight(), 160);
                        } else {
                            State.mirrorVirtualDisplay.resize(surfaceView.getHeight(), surfaceView.getWidth(), 160);
                        }
                        State.mirrorVirtualDisplay.setSurface(targetSurface);
                    }
                });
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

       if (getSupportActionBar() != null) {
           getSupportActionBar().hide();
       }

       getWindow().setFlags(
               WindowManager.LayoutParams.FLAG_FULLSCREEN,
               WindowManager.LayoutParams.FLAG_FULLSCREEN);

       Window window = getWindow();
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
           window.setDecorFitsSystemWindows(false);
       }

       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
           WindowManager.LayoutParams layoutParams = window.getAttributes();
           layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
           window.setAttributes(layoutParams);
       }

       window.setStatusBarColor(Color.TRANSPARENT);
       window.setNavigationBarColor(Color.TRANSPARENT);

        surfaceView = new SurfaceView(this);
        
        // 注册屏幕方向变化监听
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(new OrientationChangeCallback(), renderHandler);
        
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                // 获取手机主屏的完整显示信息
                DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                DisplayMetrics displayMetrics = new DisplayMetrics();
                Display display = displayManager.getDisplay(0);
                display.getRealMetrics(displayMetrics); // 使用getRealMetrics获取包含系统装饰(如状态栏、导航栏)的真实尺寸
                int defaultDisplayWidth = displayMetrics.widthPixels;  // 获取实际屏幕宽度
                int defaultDisplayHeight = displayMetrics.heightPixels; // 获取实际屏幕高度
                if (defaultDisplayHeight < defaultDisplayWidth) {
                    // 如果主屏幕是横屏,交换宽高
                    int temp = defaultDisplayWidth;
                    defaultDisplayWidth = defaultDisplayHeight;
                    defaultDisplayHeight = temp;
                }

                // 记录屏幕尺寸信息到日志
                android.util.Log.d("MirrorActivity", "主屏幕实际尺寸: " + defaultDisplayWidth + " x " + defaultDisplayHeight);
                android.util.Log.d("MirrorActivity", "外接显示器尺寸: " + surfaceView.getWidth() + " x " + surfaceView.getHeight());

                // 创建专用的渲染线程
                renderThread = new HandlerThread("MirrorActivityRenderThread");
                renderThread.start();
                renderHandler = new Handler(renderThread.getLooper());

                // 在渲染线程中初始化OpenGL
                renderHandler.post(() -> {
                    // 初始化 EGL
                    eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                    if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                        throw new RuntimeException("无法获取 EGL 显示连接");
                    }

                    int[] version = new int[2];
                    if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                        throw new RuntimeException("无法初始化 EGL");
                    }

                    // 配置 EGL
                    int[] configAttribs = {
                            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                            EGL14.EGL_RED_SIZE, 8,
                            EGL14.EGL_GREEN_SIZE, 8,
                            EGL14.EGL_BLUE_SIZE, 8,
                            EGL14.EGL_ALPHA_SIZE, 8,
                            EGL14.EGL_NONE
                    };

                    android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
                    int[] numConfigs = new int[1];
                    EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0);
                    eglConfig = configs[0];

                    // 创建 EGL 上下文
                    int[] contextAttribs = {
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                            EGL14.EGL_NONE
                    };
                    eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0);

                    // 创建 EGL Surface
                    eglOutputSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceView.getHolder().getSurface(), null, 0);

                    // 设置当前 EGL 环境
                    EGL14.eglMakeCurrent(eglDisplay, eglOutputSurface, eglOutputSurface, eglContext);
                    GLES20.glViewport(0, 0, surfaceView.getWidth(), surfaceView.getHeight());

                    // 一次性创建两个输入纹理
                    int[] textures = new int[2];
                    GLES20.glGenTextures(2, textures, 0);
                    portraitInputTextureId = textures[0];
                    landscapeInputTextureId = textures[1];

                    portraitRenderer = new PortraitRenderer(portraitInputTextureId, eglDisplay, eglOutputSurface);
                    landscapeRenderer = new LandscapeRenderer(landscapeInputTextureId, eglDisplay, eglOutputSurface, surfaceView.getWidth(), surfaceView.getHeight());

                    GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, portraitInputTextureId);

                    // 设置纹理参数
                    GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                    GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                    GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, landscapeInputTextureId);
                    GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                    GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);


                    // 创建SurfaceTexture和Surface
                    portraitInputSurfaceTexture = new SurfaceTexture(portraitInputTextureId);
                    portraitInputSurfaceTexture.setDefaultBufferSize(surfaceView.getHeight(), surfaceView.getWidth());
                    portraitInputSurfaceTexture.setOnFrameAvailableListener(portraitRenderer);
                    portraitInputSurface = new Surface(portraitInputSurfaceTexture);

                    landscapeInputSurfaceTexture = new SurfaceTexture(landscapeInputTextureId);
                    landscapeInputSurfaceTexture.setDefaultBufferSize(surfaceView.getWidth(), surfaceView.getHeight());
                    landscapeInputSurfaceTexture.setOnFrameAvailableListener(landscapeRenderer);
                    landscapeInputSurface = new Surface(landscapeInputSurfaceTexture);

                    // 使用inputSurface创建虚拟显示器
                    if (State.mirrorVirtualDisplay == null && State.mediaProjection != null) {
                        stopVirtualDisplay();
                        DisplayMetrics metrics = new DisplayMetrics();
                        display.getRealMetrics(metrics);
                        boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
                        Surface targetSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
                        
                        State.mirrorVirtualDisplay = State.mediaProjection.createVirtualDisplay("Mirror",
                                surfaceView.getHeight(), surfaceView.getWidth(), 160,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                                targetSurface, null, renderHandler);
                        State.mediaProjection = null;
                    } else if (State.mirrorVirtualDisplay != null) {
                        DisplayMetrics metrics = new DisplayMetrics();
                        display.getRealMetrics(metrics);
                        boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
                        Surface targetSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
                        
                        State.mirrorVirtualDisplay.setSurface(targetSurface);
                    }


                });
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 可以在这里处理尺寸变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                renderHandler.post(() -> {
                    // 清理OpenGL资源
                    if (portraitRenderer != null) {
                        portraitRenderer.release();
                    }
                    if (portraitInputTextureId != -1) {
                        int[] textures = new int[]{portraitInputTextureId};
                        GLES20.glDeleteTextures(1, textures, 0);
                        portraitInputTextureId = -1;
                    }

                    if (landscapeRenderer != null) {
                        landscapeRenderer.release();
                    }
                    if (landscapeInputTextureId != -1) {
                        int[] textures = new int[]{landscapeInputTextureId};
                        GLES20.glDeleteTextures(1, textures, 0);
                        landscapeInputTextureId = -1;
                    }

                    // 原有的清理代码
                    if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                        if (eglOutputSurface != EGL14.EGL_NO_SURFACE) {
                            EGL14.eglDestroySurface(eglDisplay, eglOutputSurface);
                        }
                        if (eglContext != EGL14.EGL_NO_CONTEXT) {
                            EGL14.eglDestroyContext(eglDisplay, eglContext);
                        }
                        EGL14.eglTerminate(eglDisplay);
                    }
                    eglDisplay = EGL14.EGL_NO_DISPLAY;
                    eglContext = EGL14.EGL_NO_CONTEXT;
                    eglOutputSurface = EGL14.EGL_NO_SURFACE;
                });

                // 清理线程
                if (renderThread != null) {
                    renderThread.quitSafely();
                    renderThread = null;
                }
                if (portraitInputSurface != null) {
                    portraitInputSurface.release();
                    portraitInputSurface = null;
                }
                if (portraitInputSurfaceTexture != null) {
                    portraitInputSurfaceTexture.release();
                    portraitInputSurfaceTexture = null;
                }
                if (landscapeInputSurface != null) {
                    landscapeInputSurface.release();
                    landscapeInputSurface = null;
                }
                if (landscapeInputSurfaceTexture != null) {
                    landscapeInputSurfaceTexture.release();
                    landscapeInputSurfaceTexture = null;
                }
            }
        });
        
        setContentView(surfaceView);
        State.log("MirrorActivity created");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销屏幕方向变化监听
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        displayManager.unregisterDisplayListener(new OrientationChangeCallback());
        State.log("MirrorActivity destroyed");
    }

    private static class PortraitRenderer implements SurfaceTexture.OnFrameAvailableListener {

        // 更新顶点坐标为全屏四边形
        private final float[] vertexCoords = {
            -1.0f, -1.0f, 0.0f,  // 左下
             1.0f, -1.0f, 0.0f,  // 右下
            -1.0f,  1.0f, 0.0f,  // 左上
             1.0f,  1.0f, 0.0f   // 右上
        };
        
        // 添加纹理坐标
        private final float[] textureCoords = {
            0.0f, 1.0f,  // 左下
            1.0f, 1.0f,  // 右下
            0.0f, 0.0f,  // 左上
            1.0f, 0.0f   // 右上
        };

        // 简化的顶点着色器代码
        private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;\n" +
            "attribute vec4 vPosition;\n" +
            "attribute vec2 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * vPosition;\n" +
            "  vTextureCoord = aTextureCoord;\n" +
            "}";

        // 简化的片段着色器代码
        private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
            "}";

        protected FloatBuffer textureBuffer;
        protected int textureCoordHandle;
        protected int positionHandle;
        protected int textureHandle;

        protected int mProgram;
        protected FloatBuffer vertexBuffer;

        protected int mvpMatrixHandle;
        protected float[] portraitMvpMatrix;
        protected final int inputTextureId;
        protected final EGLDisplay eglDisplay;
        protected final EGLSurface eglOutputSurface;

        public PortraitRenderer(int inputTextureId, EGLDisplay eglDisplay, EGLSurface eglOutputSurface) {
            this.inputTextureId = inputTextureId;
            this.eglDisplay = eglDisplay;
            this.eglOutputSurface = eglOutputSurface;
            portraitMvpMatrix = new float[16];
            android.opengl.Matrix.setIdentityM(portraitMvpMatrix, 0);
            android.opengl.Matrix.scaleM(portraitMvpMatrix, 0, 1, 1, 1.0f);
            android.opengl.Matrix.setRotateM(portraitMvpMatrix, 0, 90, 0, 0, 1.0f);

            // 初始化顶点缓冲
            ByteBuffer bb = ByteBuffer.allocateDirect(vertexCoords.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertexCoords);
            vertexBuffer.position(0);

            // 初始化纹理坐标缓冲
            ByteBuffer textureBB = ByteBuffer.allocateDirect(textureCoords.length * 4);
            textureBB.order(ByteOrder.nativeOrder());
            textureBuffer = textureBB.asFloatBuffer();
            textureBuffer.put(textureCoords);
            textureBuffer.position(0);

            // 创建着色器程序
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, vertexShader);
            GLES20.glAttachShader(mProgram, fragmentShader);
            GLES20.glLinkProgram(mProgram);

            // 获取着色器中的变量句柄
            positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
            textureCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            textureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture");
            mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            renderFrame(portraitMvpMatrix);
            EGL14.eglSwapBuffers(eglDisplay, eglOutputSurface);
        }

        protected void renderFrame(float[] mvpMatrix) {
            GLES20.glUseProgram(mProgram);

            // 设置MVP矩阵
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

            // 绑定顶点坐标
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

            // 绑定纹理坐标
            GLES20.glEnableVertexAttribArray(textureCoordHandle);
            GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

            // 绑定纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, inputTextureId);
            GLES20.glUniform1i(textureHandle, 0);

            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            // 解绑
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureCoordHandle);
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }

        // 添加清理方法
        public void release() {
            // 清理OpenGL资源
            if (mProgram != 0) {
                GLES20.glDeleteProgram(mProgram);
                mProgram = 0;
            }
        }
    }

    private static class LandscapeRenderer extends PortraitRenderer {
        private final int width;
        private final int height;
        private final float[] landscapeMvpMatrix;
        private final float[] identityMvpMatrix;
        private int frameCounter = 0;
        private int[] fbo = new int[1];
        private int[] tempTexture = new int[1];
        private boolean hasSymmetricBlackBar = false;
        private int topBottomBlackBarSize = 0;
        private int leftRightBlackBarSize = 0;

        public LandscapeRenderer(int inputTextureId, EGLDisplay eglDisplay, EGLSurface eglOutputSurface, int width, int height) {
            super(inputTextureId, eglDisplay, eglOutputSurface);
            this.width = width;
            this.height = height;

            landscapeMvpMatrix = new float[16];
            android.opengl.Matrix.setIdentityM(landscapeMvpMatrix, 0);
            android.opengl.Matrix.scaleM(landscapeMvpMatrix, 0, 1, 1, 1.0f);

            identityMvpMatrix = new float[16];
            android.opengl.Matrix.setIdentityM(identityMvpMatrix, 0);
            android.opengl.Matrix.scaleM(identityMvpMatrix, 0, 1, 1, 1.0f);

            // 创建临时纹理
            GLES20.glGenTextures(1, tempTexture, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tempTexture[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,  // 修改高度为完整高度
                               GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            // 创建并设置FBO
            GLES20.glGenFramebuffers(1, fbo, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, 
                                         GLES20.GL_TEXTURE_2D, tempTexture[0], 0);

            // 检查FBO状态
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                android.util.Log.e("MirrorActivity", "FBO创建失败，状态: " + status);
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        }

        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();
            renderFrame(landscapeMvpMatrix);
            EGL14.eglSwapBuffers(eglDisplay, eglOutputSurface);
            if (frameCounter == 0) {
                adjustLandscapeMvpMatrix();
            }
            frameCounter = (frameCounter + 1) % 300;
        }

        private void adjustLandscapeMvpMatrix() {
            detectBlackBar();
            if (hasSymmetricBlackBar) {
                // 计算缩放比例
                float scaleX = (float)(width) / (width - 2 * leftRightBlackBarSize);
                float scaleY = (float)(height) / (height - 2 * topBottomBlackBarSize);
                float scale = Math.min(scaleX, scaleY);
                
                // 重置矩阵
                android.opengl.Matrix.setIdentityM(landscapeMvpMatrix, 0);
                
                // 应用缩放
                android.opengl.Matrix.scaleM(landscapeMvpMatrix, 0, scale, scale, 1.0f);
                
                android.util.Log.d("MirrorActivity", String.format(
                    "应用缩放变换: scaleX=%.2f, scaleY=%.2f, 最终scale=%.2f",
                    scaleX, scaleY, scale
                ));
            } else {
                // 如果没有对称黑边，使用单位矩阵
                for(int i = 0; i < identityMvpMatrix.length; i++) {
                    landscapeMvpMatrix[i] = identityMvpMatrix[i];
                }
            }
        }

        private void detectBlackBar() {
            // 切换到FBO
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);

            // 清除缓冲区
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 渲染到FBO
            renderFrame(identityMvpMatrix);

            // 确保渲染完成
            GLES20.glFinish();


            // 检测左右黑边
            ByteBuffer horizontalPixelBuffer = ByteBuffer.allocateDirect(width * 4);
            horizontalPixelBuffer.order(ByteOrder.nativeOrder());

            // 检测上下黑边
            ByteBuffer verticalPixelBuffer = ByteBuffer.allocateDirect(height * 4);
            verticalPixelBuffer.order(ByteOrder.nativeOrder());
            int middleY = height / 2;
            GLES20.glReadPixels(0, middleY, width, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, horizontalPixelBuffer);

            int middleX = width / 2;
            GLES20.glReadPixels(middleX, 0, 1, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, verticalPixelBuffer);

            // 分析水平和垂直像素
            byte[] horizontalPixels = new byte[width * 4];
            byte[] verticalPixels = new byte[height * 4];
            horizontalPixelBuffer.get(horizontalPixels);
            verticalPixelBuffer.get(verticalPixels);

            // 计算左右黑边宽度
            int leftBlackWidth = 0;
            int rightBlackWidth = 0;
            // 计算上下黑边高度
            int topBlackHeight = 0;
            int bottomBlackHeight = 0;

            // 从左向右扫描左黑边
            for (int i = 0; i < width * 4; i += 4) {
                if (isBlackPixel(horizontalPixels[i], horizontalPixels[i+1], horizontalPixels[i+2])) {
                    leftBlackWidth++;
                } else {
                    break;
                }
            }

            // 从右向左扫描右黑边
            for (int i = width * 4 - 4; i >= 0; i -= 4) {
                if (isBlackPixel(horizontalPixels[i], horizontalPixels[i+1], horizontalPixels[i+2])) {
                    rightBlackWidth++;
                } else {
                    break;
                }
            }

            // 从下向上扫描底部黑边
            for (int i = 0; i < height * 4; i += 4) {
                if (isBlackPixel(verticalPixels[i], verticalPixels[i+1], verticalPixels[i+2])) {
                    bottomBlackHeight++;
                } else {
                    break;
                }
            }

            // 从上向下扫描顶部黑边
            for (int i = height * 4 - 4; i >= 0; i -= 4) {
                if (isBlackPixel(verticalPixels[i], verticalPixels[i+1], verticalPixels[i+2])) {
                    topBlackHeight++;
                } else {
                    break;
                }
            }

            // 判断是否存在对称的黑边
            boolean hasSymmetricHorizontalBars = Math.abs(leftBlackWidth - rightBlackWidth) <= 1
                && leftBlackWidth > 0 && rightBlackWidth > 0;
            boolean hasSymmetricVerticalBars = Math.abs(topBlackHeight - bottomBlackHeight) <= 1
                && topBlackHeight > 0 && bottomBlackHeight > 0;

            android.util.Log.d("MirrorActivity", String.format(
                "左黑边: %d, 右黑边: %d, 上黑边: %d, 下黑边: %d, 水平对称: %b, 垂直对称: %b",
                leftBlackWidth, rightBlackWidth, topBlackHeight, bottomBlackHeight,
                hasSymmetricHorizontalBars, hasSymmetricVerticalBars));

            int horizontalThreshold = (int) (width * 0.3);
            int verticalThreshold = (int) (height * 0.3);
            if (leftBlackWidth < horizontalThreshold && rightBlackWidth < horizontalThreshold && topBlackHeight < verticalThreshold && bottomBlackHeight < verticalThreshold) {
                if (hasSymmetricHorizontalBars && hasSymmetricVerticalBars) {
                    hasSymmetricBlackBar = true;
                    leftRightBlackBarSize = Math.min(leftBlackWidth, rightBlackWidth);
                    topBottomBlackBarSize = Math.min(topBlackHeight, bottomBlackHeight);
                } else {
                    if (hasSymmetricHorizontalBars && Math.min(leftBlackWidth, rightBlackWidth) >= leftRightBlackBarSize && Math.min(topBlackHeight, bottomBlackHeight) >= topBottomBlackBarSize) {
                        // keep old value
                    } else {
                        hasSymmetricBlackBar = false;
                        leftRightBlackBarSize = 0;
                        topBottomBlackBarSize = 0;
                    }
                }
            }

            // 切回默认帧缓冲
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        private boolean isBlackPixel(byte r, byte g, byte b) {
            return (r & 0xFF) == 0 && (g & 0xFF) == 0 && (b & 0xFF) == 0;
        }

        @Override
        public void release() {
            super.release();
            // 清理额外的资源
            GLES20.glDeleteFramebuffers(1, fbo, 0);
            GLES20.glDeleteTextures(1, tempTexture, 0);
        }
    }
}