package com.gitee.connect_screen;

import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
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

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

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

        textureView = new TextureView(this);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                EGLSurface eglSurface = null;
                EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                int[] version = new int[2];
                EGL14.eglInitialize(eglDisplay, version, 0, version, 1);

                int[] configAttribs = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                };
                EGLConfig[] configs = new EGLConfig[1];
                int[] numConfigs = new int[1];
                EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0);

                int[] contextAttribs = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                };
                EGLContext eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);

                Surface surface = new Surface(surfaceTexture);
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, new int[]{EGL14.EGL_NONE}, 0);
                
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

                MyGLRenderer renderer = new MyGLRenderer();
                renderer.onSurfaceCreated(null, null);
                renderer.onSurfaceChanged(null, width, height);
                renderer.onDrawFrame(null);

                EGL14.eglSwapBuffers(eglDisplay, eglSurface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

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

    private class MyGLRenderer implements GLSurfaceView.Renderer {
        private final float[] triangleCoords = {
            0.0f,  0.5f, 0.0f,  // top
           -0.5f, -0.5f, 0.0f,  // bottom left
            0.5f, -0.5f, 0.0f   // bottom right
        };

        private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
            "void main() {" +
            "  gl_Position = vPosition;" +
            "}";

        private final String fragmentShaderCode =
            "precision mediump float;" +
            "void main() {" +
            "  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);" +
            "}";

        private int mProgram;
        private FloatBuffer vertexBuffer;

        public void onSurfaceCreated(GL10 gl10, javax.microedition.khronos.egl.EGLConfig eglConfig) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            
            ByteBuffer bb = ByteBuffer.allocateDirect(triangleCoords.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(triangleCoords);
            vertexBuffer.position(0);

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, vertexShader);
            GLES20.glAttachShader(mProgram, fragmentShader);
            GLES20.glLinkProgram(mProgram);
        }

        public void onDrawFrame(GL10 unused) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(mProgram);

            int positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }
}