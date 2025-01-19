package com.gitee.connect_screen.job;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

import com.displaylink.manager.display.DisplayMode;
import com.gitee.connect_screen.DisplaylinkState;
import com.gitee.connect_screen.State;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ListenOpenglAndPostFrame {
    public static ListenOpenglAndPostFrame instance;
    private final VirtualDisplayArgs virtualDisplayArgs;
    private final Display defaultDisplay;
    private EGLDisplay eglDisplay;
    private EGLConfig eglConfig;
    private EGLContext eglContext;
    private int[] fbo = new int[1];
    private int[] tempTexture = new int[1];
    private int portraitInputTextureId;
    private int landscapeInputTextureId;
    private PortraitRenderer portraitRenderer;
    private LandscapeRenderer landscapeRenderer;
    private boolean autoRotate = true;
    private boolean autoScale = true;
    private SurfaceTexture portraitInputSurfaceTexture;
    private Surface portraitInputSurface;
    private SurfaceTexture landscapeInputSurfaceTexture;
    private Surface landscapeInputSurface;

    public ListenOpenglAndPostFrame(VirtualDisplayArgs virtualDisplayArgs, Context context) {
        if (instance != null) {
            instance.release();
        }
        instance = this;
        this.virtualDisplayArgs = virtualDisplayArgs;
        DisplaylinkState displaylinkState = State.displaylinkState;
        displaylinkState.handlerThread = new HandlerThread("ListenOpenglAndPostFrame");
        displaylinkState.handlerThread.start();
        displaylinkState.handler = new Handler(displaylinkState.handlerThread.getLooper());
        displaylinkState.handler.post(this::start);
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    public void release() {
        instance = null;
    }

    public void start() {
        int width = virtualDisplayArgs.width;
        int height = virtualDisplayArgs.height;
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
        int[] surfaceAttribs = {
            EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0);
        if (eglSurface == null) {
            throw new RuntimeException("无法创建 EGL 离屏缓冲表面");
        }

        // 设置当前上下文
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("无法设置 EGL 上下文为当前上下文");
        }
        GLES20.glViewport(0, 0, width, height);

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
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);

        int[] textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);
        portraitInputTextureId = textures[0];
        landscapeInputTextureId = textures[1];

        portraitRenderer = new PortraitRenderer(portraitInputTextureId, width, height);
        landscapeRenderer = new LandscapeRenderer(landscapeInputTextureId, eglDisplay, width, height, autoScale);

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
        portraitInputSurfaceTexture.setDefaultBufferSize(height, width);
        portraitInputSurfaceTexture.setOnFrameAvailableListener(portraitRenderer);
        portraitInputSurface = new Surface(portraitInputSurfaceTexture);

        landscapeInputSurfaceTexture = new SurfaceTexture(landscapeInputTextureId);
        landscapeInputSurfaceTexture.setDefaultBufferSize(width, height);
        landscapeInputSurfaceTexture.setOnFrameAvailableListener(landscapeRenderer);
        landscapeInputSurface = new Surface(landscapeInputSurfaceTexture);

        DisplaylinkState displaylinkState = State.displaylinkState;
        if (displaylinkState.getVirtualDisplay() == null && State.getMediaProjection() != null) {
            displaylinkState.stopVirtualDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            defaultDisplay.getRealMetrics(metrics);
            boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
            if (!autoRotate) {
                isLandscape = true;
            }
            Surface targetSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
            displaylinkState.createdVirtualDisplay(State.getMediaProjection().createVirtualDisplay("Displaylink Mirror",
                    isLandscape ? width : height, isLandscape ? height : width, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    targetSurface, null, displaylinkState.handler));
            State.setMediaProjection(null);
        } else if (displaylinkState.getVirtualDisplay() != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            defaultDisplay.getRealMetrics(metrics);
            boolean isLandscape = metrics.widthPixels > metrics.heightPixels;
            Surface targetSurface = isLandscape ? landscapeInputSurface : portraitInputSurface;
            displaylinkState.getVirtualDisplay().setSurface(targetSurface);
        }
    }

    private static class PortraitRenderer implements SurfaceTexture.OnFrameAvailableListener {
        private final DisplaylinkState displaylinkState;
        private final int width;
        private final int height;
        private final ExternalTextureRenderer externalTextureRenderer;
        private ByteBuffer[] buffers;
        private int buffersIndex;
        protected float[] portraitMvpMatrix;
        
        public PortraitRenderer(int inputTextureId, int width, int height) {
            this.externalTextureRenderer = new ExternalTextureRenderer(inputTextureId, true);
            this.width = width;
            this.height = height;
            buffers = new ByteBuffer[] {
                    ByteBuffer.allocateDirect(width * height * 4),
                    ByteBuffer.allocateDirect(width * height * 4),
                    ByteBuffer.allocateDirect(width * height * 4),
            };


            portraitMvpMatrix = new float[16];
            android.opengl.Matrix.setIdentityM(portraitMvpMatrix, 0);
            android.opengl.Matrix.scaleM(portraitMvpMatrix, 0, 1, 1, 1.0f);
            android.opengl.Matrix.setRotateM(portraitMvpMatrix, 0, 270, 0, 0, 1.0f);
            displaylinkState = State.displaylinkState;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();
            this.externalTextureRenderer.renderFrame(portraitMvpMatrix);
            GLES20.glFinish();
            ByteBuffer buffer = buffers[buffersIndex];
            buffer.position(0);
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            buffer.rewind();
            int resultCode = displaylinkState.nativeDriver.postFrame(displaylinkState.encoderId, buffer);
            android.util.Log.i("Opengl", "portrait onFrame!!! " + resultCode);
            boolean buffered = resultCode != 1 && resultCode != -2;
            if (buffered) {
                buffersIndex = (buffersIndex + 1) % buffers.length;
            }
        }

        public void release() {
            this.externalTextureRenderer.release();
        }
    }

    private static class LandscapeRenderer implements SurfaceTexture.OnFrameAvailableListener {
        private final int textureId;
        private final EGLDisplay eglDisplay;

        public LandscapeRenderer(int textureId, EGLDisplay eglDisplay, int width, int height, boolean autoScale) {
            this.textureId = textureId;
            this.eglDisplay = eglDisplay;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            surfaceTexture.updateTexImage();
            android.util.Log.i("Opengl", "landscape onFrame!!!");
        }
    }
}
