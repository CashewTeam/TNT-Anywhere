package com.gitee.connect_screen.job;

import java.nio.ByteOrder;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.display.DisplayMode;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class ListenOpenglAndPostFrame implements SurfaceTexture.OnFrameAvailableListener {

    private int textureId;
    public SurfaceTexture surfaceTexture;
    private ByteBuffer[] buffers;
    private int buffersIndex;

    private int frameBuffer;
    private int frameBufferTexture;
    private final String vertexShader = "attribute vec4 position;\n" +
            "attribute vec2 texcoord;\n" +
            "varying vec2 v_texcoord;\n" +
            "uniform mat4 uMVPMatrix;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * position;\n" +
            "    v_texcoord = texcoord;\n" +
            "}";

    private final String fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 v_texcoord;\n" +
            "uniform samplerExternalOES texture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(texture, v_texcoord);\n" +
            "}";

    private int program;

    private ByteBuffer vertexBuffer;
    private ByteBuffer texcoordBuffer;

    private int positionHandle;
    private int texcoordHandle;
    private int textureHandle;
    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    public Surface surface;
    private EGLSurface eglSurface;
    private int frameCounter;
    private UsbState usbState;
    private int mvpMatrixHandle;

    public void startVirtualDisplay(UsbState usbState) {
        try {
            this.usbState = usbState;
            egl = (EGL10) EGLContext.getEGL();
            eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("无法获取 EGL 显示连接");
            }

            State.log("eglGetDisplay: " + eglDisplay);

            int[] version = new int[2];
            boolean initSuccess = egl.eglInitialize(eglDisplay, version);
            State.log("eglInitialize: " + initSuccess + ", version: " + version[0] + "." + version[1]);

            List<Integer> configAttribsList = Arrays.asList(
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_NONE);
            int[] configAttribs = new int[configAttribsList.size()];
            for (int i = 0; i < configAttribsList.size(); i++) {
                configAttribs[i] = configAttribsList.get(i);
            }

            javax.microedition.khronos.egl.EGLConfig[] configs = new javax.microedition.khronos.egl.EGLConfig[1];
            int[] numConfigs = new int[1];
            boolean configSuccess = egl.eglChooseConfig(eglDisplay, configAttribs, configs, 1, numConfigs);
            State.log("eglChooseConfig: " + configSuccess + ", numConfigs: " + numConfigs[0]);

            int[] contextAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL10.EGL_NONE
            };

            eglContext = egl.eglCreateContext(eglDisplay, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs);
            State.log("eglCreateContext: " + eglContext);

            int[] surfaceAttribs = {
                    EGL10.EGL_WIDTH, 1,
                    EGL10.EGL_HEIGHT, 1,
                    EGL10.EGL_NONE
            };
            eglSurface = egl.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs);
            if (eglSurface == EGL10.EGL_NO_SURFACE) {
                int error = egl.eglGetError();
                throw new RuntimeException("创建PBuffer表面失败，错误码: 0x" + Integer.toHexString(error));
            }

            boolean makeCurrent = egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
            State.log("eglMakeCurrent: " + makeCurrent);

            if (!makeCurrent) {
                int error = egl.eglGetError();
                throw new RuntimeException("eglMakeCurrent failed with error: 0x" + Integer.toHexString(error));
            }

            // 添加纹理初始化逻辑
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            // 创建虚拟显示
            int targetWidth = 1920; // 根据需要设置宽度
            int height = 1080; // 根据需要设置高度
            int dpi = 160; // 根据需要设置 DPI
            SurfaceTexture surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setDefaultBufferSize(targetWidth, height); // 使用 targetWidth 和 height 设置默认缓冲区大小
            buffers = new ByteBuffer[] {
                    ByteBuffer.allocateDirect(targetWidth * height * 4),
                    ByteBuffer.allocateDirect(targetWidth * height * 4),
                    ByteBuffer.allocateDirect(targetWidth * height * 4),
                    ByteBuffer.allocateDirect(targetWidth * height * 4),
            };
            surfaceTexture.setOnFrameAvailableListener(this);
            this.surface = new Surface(surfaceTexture);
            usbState.virtualDisplay = State.mediaProjection.createVirtualDisplay("DisplayLink",
                    targetWidth, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null); // 使用 surfaceTexture 而不是 surface
        } catch (Exception e) {
            // 清理资源
            if (eglSurface != null && eglSurface != EGL10.EGL_NO_SURFACE) {
                egl.eglDestroySurface(eglDisplay, eglSurface);
            }
            if (eglContext != null) {
                egl.eglDestroyContext(eglDisplay, eglContext);
            }
            if (eglDisplay != null) {
                egl.eglTerminate(eglDisplay);
            }
            State.log("OpenGL初始化失败");
            throw new RuntimeException("OpenGL初始化失败: " + e.getMessage(), e);
        }
    }

    private void setupOffscreenRendering() {
        // 清除可能存在的GL错误
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            State.log("初始化前清除GL错误: 0x" + Integer.toHexString(error));
        }

        // 创建FBO
        int[] fbos = new int[1];
        GLES20.glGenFramebuffers(1, fbos, 0);
        frameBuffer = fbos[0];

        // 创建FBO纹理
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        frameBufferTexture = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1920, 1080, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // 绑定FBO和纹理
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, frameBufferTexture, 0);

        // 检查FBO状态
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            State.log("FBO不完整，状态: 0x" + Integer.toHexString(status));
        }
    }

    private float divide(int a, int b) {
        return ((float) a) / ((float) b);
    }

    private float asFloat(int a) {
        return a;
    }
    
    private int createProgram() {
        // 编译顶点着色器
        int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShaderHandle, vertexShader);
        GLES20.glCompileShader(vertexShaderHandle);

        // 检查编译状态
        int[] status = new int[1];
        GLES20.glGetShaderiv(vertexShaderHandle, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            State.log("顶点着色器编译失败: " + GLES20.glGetShaderInfoLog(vertexShaderHandle));
            return 0;
        }

        // 编译片段着色器
        int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
        GLES20.glCompileShader(fragmentShaderHandle);

        // 检查编译状态
        GLES20.glGetShaderiv(fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            State.log("片段着色器编译失败: " + GLES20.glGetShaderInfoLog(fragmentShaderHandle));
            return 0;
        }

        // 创建程序并链接着色器
        int programHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(programHandle, vertexShaderHandle);
        GLES20.glAttachShader(programHandle, fragmentShaderHandle);
        GLES20.glLinkProgram(programHandle);

        // 检查链接状态
        GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            State.log("程序链接失败: " + GLES20.glGetProgramInfoLog(programHandle));
            return 0;
        }

        // 删除着色器，但保留程序
        GLES20.glDeleteShader(vertexShaderHandle);
        GLES20.glDeleteShader(fragmentShaderHandle);

        // 获取并缓存着色器属性位置
        positionHandle = GLES20.glGetAttribLocation(programHandle, "position");
        texcoordHandle = GLES20.glGetAttribLocation(programHandle, "texcoord");
        textureHandle = GLES20.glGetUniformLocation(programHandle, "texture");

        // 获取MVP矩阵uniform位置
        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");

        // 修改顶点坐标以保持原有输入
        List<Float> vertices = Arrays.asList(
                asFloat(-1), asFloat(-1), // 左下
                asFloat(1), asFloat(-1), // 右下
                asFloat(-1), asFloat(1), // 左上
                asFloat(1), asFloat(1) // 右上
        );

        // 修改纹理坐标以保持原有输入
        List<Float> textureCoords = Arrays.asList(
                asFloat(0), asFloat(0),    // 左下
                asFloat(1), asFloat(0),   // 右下
                asFloat(0), asFloat(1),   // 左上
                asFloat(1), asFloat(1)   // 右上
        );

        // 转换为ByteBuffer的代码
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size() * 4)
                .order(ByteOrder.nativeOrder());
        float[] verticesArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            verticesArray[i] = vertices.get(i);
        }
        vertexBuffer.asFloatBuffer().put(verticesArray).position(0);

        texcoordBuffer = ByteBuffer.allocateDirect(textureCoords.size() * 4)
                .order(ByteOrder.nativeOrder());
        float[] textureCoordsArray = new float[textureCoords.size()];
        for (int i = 0; i < textureCoords.size(); i++) {
            textureCoordsArray[i] = textureCoords.get(i);
        }
        texcoordBuffer.asFloatBuffer().put(textureCoordsArray).position(0);

        GLES20.glUseProgram(program);

        // 设置顶点坐标
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(positionHandle);

        // 设置纹理坐标
        GLES20.glVertexAttribPointer(texcoordHandle, 2, GLES20.GL_FLOAT, false, 0, texcoordBuffer);
        GLES20.glEnableVertexAttribArray(texcoordHandle);

        // 渲染到FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glViewport(0, 0, 1920, 1080);

        return programHandle;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        usbState.frameCounter++;
        try {
            if (program == 0) {
                // 初始化离屏渲染
                setupOffscreenRendering();
                // 创建着色器程序
                program = createProgram();
                if (program == 0) {
                    State.log("创建着色器程序失败");
                    return;
                }
                State.log("创建着色器程序成功");
                usbState.nativeDriver.setMode(usbState.encoderId, new DisplayMode(1920, 1080, 60), 7680, 1);
            }

            // 确保在更新纹理前清除所有GL错误
            int error;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                State.log("清除GL错误: 0x" + Integer.toHexString(error));
            }

            surfaceTexture.updateTexImage();

            // 绑定FBO
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);

            // 清除缓冲区
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 使用着色器程序
            GLES20.glUseProgram(program);

            // 绑定纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(textureHandle, 0);

            // 设置MVP矩阵为单位矩阵
            float[] mvpMatrix = new float[16];
            Matrix.setIdentityM(mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glFinish();

            // 读取像素数据到buffer
            ByteBuffer buffer = buffers[buffersIndex];
            buffersIndex = (buffersIndex + 1) % buffers.length;
            buffer.position(0);
            GLES20.glReadPixels(0, 0, 1920, 1080, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            buffer.rewind();
            int resultCode = usbState.nativeDriver.postFrame(usbState.encoderId, buffer);
            usbState.recentPostFrameResultCodes[usbState.frameCounter % usbState.recentPostFrameResultCodes.length] = resultCode;
            if (resultCode < 0) {
                Log.e("displaylink", "postFrame failed, resultCode: " + resultCode);
            }
        } catch (Exception e) {
            State.log("渲染帧时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}