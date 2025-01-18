package com.gitee.connect_screen;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
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
    private int inputTextureId = -1;
    private SurfaceTexture inputSurfaceTexture = null;
    private Surface inputSurface = null;
    private Handler renderHandler;
    private HandlerThread renderThread;

    private EGLDisplay eglDisplay;
    private android.opengl.EGLSurface eglOutputSurface;
    private android.opengl.EGLContext eglContext;
    private android.opengl.EGLConfig eglConfig;

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
        MyGLRenderer renderer = new MyGLRenderer();
        
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
                renderThread = new HandlerThread("GLRenderThread");
                renderThread.start();
                renderHandler = new Handler(renderThread.getLooper());
                renderer.onSurfaceCreated(holder.getSurface(), 
                    surfaceView.getWidth(), surfaceView.getHeight());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 可以在这里处理尺寸变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                renderHandler.post(() -> {
                    // 清理OpenGL资源
                    renderer.release();
                    if (inputTextureId != -1) {
                        int[] textures = new int[]{inputTextureId};
                        GLES20.glDeleteTextures(1, textures, 0);
                        inputTextureId = -1;
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
                if (inputSurface != null) {
                    inputSurface.release();
                    inputSurface = null;
                }
                if (inputSurfaceTexture != null) {
                    inputSurfaceTexture.release();
                    inputSurfaceTexture = null;
                }
            }
        });
        
        setContentView(surfaceView);
        State.log("MirrorActivity created");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        State.log("MirrorActivity destroyed");
    }

    private class MyGLRenderer implements SurfaceTexture.OnFrameAvailableListener {

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

        private FloatBuffer textureBuffer;
        private int textureCoordHandle;
        private int positionHandle;
        private int textureHandle;

        private int mProgram;
        private FloatBuffer vertexBuffer;

        private int mvpMatrixHandle;
        private float[] mvpMatrix;

        public MyGLRenderer() {
            mvpMatrix = new float[16];

            // 设置基础矩阵
            android.opengl.Matrix.setIdentityM(mvpMatrix, 0);
            // 设置缩放
            android.opengl.Matrix.scaleM(mvpMatrix, 0, 1, 1, 1.0f);
            android.opengl.Matrix.setRotateM(mvpMatrix, 0, 90, 0, 0, 1.0f);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
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

            EGL14.eglSwapBuffers(eglDisplay, eglOutputSurface);
        }

        public void onSurfaceCreated(Surface outputSurface, int width, int height) {

            
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
                eglOutputSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, outputSurface, null, 0);
                
                // 设置当前 EGL 环境
                EGL14.eglMakeCurrent(eglDisplay, eglOutputSurface, eglOutputSurface, eglContext);
                GLES20.glViewport(0, 0, width, height);


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

                // 创建输入纹理
                int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);
                inputTextureId = textures[0];
                GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, inputTextureId);

                // 设置纹理参数
                GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);


                // 创建SurfaceTexture和Surface
                inputSurfaceTexture = new SurfaceTexture(inputTextureId);
                inputSurfaceTexture.setDefaultBufferSize(height, width);
                inputSurfaceTexture.setOnFrameAvailableListener(this);
                inputSurface = new Surface(inputSurfaceTexture);

                // 使用inputSurface创建虚拟显示器
                if (State.mirrorVirtualDisplay == null && State.mediaProjection != null) {
                    stopVirtualDisplay();
                    State.mirrorVirtualDisplay = State.mediaProjection.createVirtualDisplay("Mirror",
                            height, width, 160,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                            inputSurface, null, renderHandler);
                    State.mediaProjection = null;
                } else if (State.mirrorVirtualDisplay != null) {
                    State.mirrorVirtualDisplay.setSurface(inputSurface);
                }
            });
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
}