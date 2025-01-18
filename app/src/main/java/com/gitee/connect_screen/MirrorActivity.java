package com.gitee.connect_screen;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;

import androidx.appcompat.app.AppCompatActivity;

import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MirrorActivity extends AppCompatActivity {
    
    private static MirrorActivity instance;
    private TextureView textureView;
    private int inputTextureId = -1;
    private SurfaceTexture inputSurfaceTexture = null;
    private Surface inputSurface = null;
    
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

//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }
//
//        getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN);
//
//        Window window = getWindow();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            window.setDecorFitsSystemWindows(false);
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            WindowManager.LayoutParams layoutParams = window.getAttributes();
//            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
//            window.setAttributes(layoutParams);
//        }
//
//        window.setStatusBarColor(Color.TRANSPARENT);
//        window.setNavigationBarColor(Color.TRANSPARENT);

        textureView = new TextureView(this);
        MyGLRenderer renderer = new MyGLRenderer();
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                renderer.onSurfaceCreated(new Surface(surfaceTexture), width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                renderer.release();
                if (inputSurface != null) {
                    inputSurface.release();
                    inputSurface = null;
                }
                if (inputSurfaceTexture != null) {
                    inputSurfaceTexture.release();
                    inputSurfaceTexture = null;
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

        // 更新顶点着色器，添加纹理坐标支持
        private final String vertexShaderCode =
            "attribute vec4 vPosition;\n" +
            "attribute vec2 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = vPosition;\n" +
            "  vTextureCoord = aTextureCoord;\n" +
            "}";

        // 更新片段着色器，添加外部纹理支持
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

        private EGLDisplay eglDisplay;
        private android.opengl.EGLSurface eglSurface;
        private android.opengl.EGLContext eglContext;
        private android.opengl.EGLConfig eglConfig;

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(mProgram);

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

            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        }

        public void onSurfaceCreated(Surface surface, int width, int height) {

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
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null, 0);
            
            // 设置当前 EGL 环境
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
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
            inputSurfaceTexture.setDefaultBufferSize(width, height);
            inputSurfaceTexture.setOnFrameAvailableListener(this);
            inputSurface = new Surface(inputSurfaceTexture);

            // 使用inputSurface创建虚拟显示器
            if (State.mirrorVirtualDisplay == null && State.mediaProjection != null) {
                stopVirtualDisplay();
                State.mirrorVirtualDisplay = State.mediaProjection.createVirtualDisplay("Mirror",
                        width, height, 160,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                        inputSurface, null, null);
                State.mediaProjection = null;
                State.log("Mirror Activity 创建了新的虚拟显示器");
            } else if (State.mirrorVirtualDisplay != null) {
                State.mirrorVirtualDisplay.setSurface(inputSurface);
                State.log("Mirror Activity 复用了已有的虚拟显示器");
            }
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }

        // 添加清理方法
        public void release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface);
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext);
                }
                EGL14.eglTerminate(eglDisplay);
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
    }
}