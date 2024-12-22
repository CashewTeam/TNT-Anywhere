package com.gitee.connect_screen;

import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class BridgeActivity extends AppCompatActivity {

    private GLSurfaceView glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // 设置全屏
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
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
        
        // 创建并设置 GLSurfaceView
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(new OpenGLRenderer(glSurfaceView));
        setContentView(glSurfaceView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
    }

    private static class OpenGLRenderer implements GLSurfaceView.Renderer {
        private GLSurfaceView glSurfaceView;
        private int textureId;
        private SurfaceTexture surfaceTexture;
        private Surface surface;
        private final float[] mvpMatrix = new float[16];
        private int program;
        private int positionHandle;
        private int texcoordHandle;
        private int textureHandle;
        private int mvpMatrixHandle;
        private ByteBuffer vertexBuffer;
        private ByteBuffer texcoordBuffer;

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

        public OpenGLRenderer(GLSurfaceView glSurfaceView) {
            this.glSurfaceView = glSurfaceView;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // 初始化纹理
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            // 创建 SurfaceTexture 和 Surface
            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setOnFrameAvailableListener(surfaceTexture -> {
                // 请求重新绘制
                glSurfaceView.requestRender();
            });
            surface = new Surface(surfaceTexture);

            // 创建虚拟显示
            State.mediaProjection.createVirtualDisplay("Preview",
                    1920, 1080, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null);

            // 初始化顶点和纹理坐标
            float[] vertices = {
                    -1.0f, -1.0f,  // 左下
                    1.0f, -1.0f,   // 右下
                    -1.0f, 1.0f,   // 左上
                    1.0f, 1.0f     // 右上
            };

            float[] texCoords = {
                    0.0f, 0.0f,    // 左下
                    1.0f, 0.0f,    // 右下
                    0.0f, 1.0f,    // 左上
                    1.0f, 1.0f     // 右上
            };

            vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder());
            vertexBuffer.asFloatBuffer().put(vertices).position(0);

            texcoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                    .order(ByteOrder.nativeOrder());
            texcoordBuffer.asFloatBuffer().put(texCoords).position(0);

            // 初始化着色器程序
            program = createProgram();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            surfaceTexture.setDefaultBufferSize(width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            surfaceTexture.updateTexImage();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);


            // 设置顶点坐标数据
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            // 设置纹理坐标数据
            GLES20.glVertexAttribPointer(texcoordHandle, 2, GLES20.GL_FLOAT, false, 0, texcoordBuffer);

            // 设置MVP矩阵
            Matrix.setIdentityM(mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

            // 绑定纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(textureHandle, 0);

            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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
                return 0;
            }

            // 编译片段着色器
            int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
            GLES20.glCompileShader(fragmentShaderHandle);

            GLES20.glGetShaderiv(fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                return 0;
            }

            // 创建程序并链接着色器
            int programHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(programHandle, vertexShaderHandle);
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);
            GLES20.glLinkProgram(programHandle);

            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                return 0;
            }

            // 删除着色器
            GLES20.glDeleteShader(vertexShaderHandle);
            GLES20.glDeleteShader(fragmentShaderHandle);

            // 获取属性和uniform位置
            positionHandle = GLES20.glGetAttribLocation(programHandle, "position");
            texcoordHandle = GLES20.glGetAttribLocation(programHandle, "texcoord");
            textureHandle = GLES20.glGetUniformLocation(programHandle, "texture");
            mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");

            return programHandle;
        }
    }
}