package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
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

import androidx.appcompat.app.AppCompatActivity;

import com.gitee.connect_screen.job.CreateVirtualDisplay;
import com.gitee.connect_screen.job.VirtualDisplayArgs;
import com.gitee.connect_screen.shizuku.ServiceUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import dev.rikka.tools.refine.Refine;

public class BridgeActivity extends AppCompatActivity {

    private static BridgeActivity instance;

    public static BridgeActivity getInstance() {
        return instance;
    }

    private GLSurfaceView glSurfaceView;
    private OpenGLRenderer renderer;


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

        // 创建并设置 GLSurfaceView
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);
        VirtualDisplayArgs args = getIntent().getParcelableExtra("virtualDisplayArgs");
        renderer = new OpenGLRenderer(glSurfaceView, args);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        
        // 添加触摸事件监听
        glSurfaceView.setOnTouchListener((v, event) -> {
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
                    args.monitorWidth, args.monitorHeight, 
                    glSurfaceView.getWidth(), glSurfaceView.getHeight());
                
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
        
        setContentView(glSurfaceView);
    }

    @Override
    protected void onDestroy() {
        Log.i("BridgeActivity", "BridgeActivity onDestroy");
        if (renderer != null) {
            renderer.release();
            renderer = null;
        }
        super.onDestroy();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(0);
        this.startActivity(intent, options.toBundle());
        instance = null;
    }

    private static class OpenGLRenderer implements GLSurfaceView.Renderer {
        private final float[] mvpMatrix = new float[16];
        private int program;
        private int positionHandle;
        private int texcoordHandle;
        private int textureHandle;
        private int mvpMatrixHandle;
        private ByteBuffer vertexBuffer;
        private ByteBuffer texcoordBuffer;
        private final GLSurfaceView glSurfaceView;
        private final VirtualDisplayArgs args;
        private int textureId;
        private Surface surface;
        private SurfaceTexture surfaceTexture;

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

        public OpenGLRenderer(GLSurfaceView glSurfaceView, VirtualDisplayArgs args) {
            this.glSurfaceView = glSurfaceView;
            this.args = args;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // 初始化纹理
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            // 创建 SurfaceTexture 和 Surface
            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setDefaultBufferSize(args.monitorWidth, args.monitorHeight);
            surface = new Surface(surfaceTexture);
            surfaceTexture.setOnFrameAvailableListener(surfaceTexture -> {
                // 请求重新绘制
                glSurfaceView.requestRender();
            });
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            // 初始化顶点和纹理坐标
            float[] vertices = {
                    -1.0f, -1.0f, // 左下
                    1.0f, -1.0f, // 右下
                    -1.0f, 1.0f, // 左上
                    1.0f, 1.0f // 右上
            };

            float[] texCoords = {
                    0.0f, 1.0f, // 左下
                    1.0f, 1.0f, // 右下
                    0.0f, 0.0f, // 左上
                    1.0f, 0.0f  // 右上
            };

            vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder());
            vertexBuffer.asFloatBuffer().put(vertices).position(0);

            texcoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                    .order(ByteOrder.nativeOrder());
            texcoordBuffer.asFloatBuffer().put(texCoords).position(0);

            // 初始化着色器程序
            program = createProgram();

            
            GLES20.glUseProgram(program);

            // 启用顶点属性数组
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(texcoordHandle);

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
            instance.runOnUiThread(() -> {
                startProjection();
            });
        }

        private void startProjection() {
            if (State.bridgeVirtualDisplay == null) {
                // 使用传入的参数创建虚拟显示
                stopVirtualDisplay();
                State.bridgeVirtualDisplay = CreateVirtualDisplay.createVirtualDisplay(args, surface);
                State.log("Bridge Activity 创建了新的虚拟显示器");
            } else {
                State.bridgeVirtualDisplay.setSurface(surface);
                State.log("Bridge Activity 复用了已有的虚拟显示器");
            }
            State.breadcrumbManager.popBreadcrumb();
            Display jumpToDisplay = State.bridgeVirtualDisplay.getDisplay();
            State.breadcrumbManager.pushBreadcrumb("屏幕 " + jumpToDisplay.getDisplayId(), () -> DisplayDetailFragment.newInstance(jumpToDisplay.getDisplayId()));
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (State.bridgeVirtualDisplay == null) {
                return;
            }
            try {
                surfaceTexture.updateTexImage();
                // 绘制
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            } catch (Throwable e) {
                Log.e("BridgeActivity", "Failed to draw frame", e);
            }
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

        public void release() {
            ImageReader imageReader = ImageReader.newInstance(args.monitorWidth, args.monitorHeight, 1, 2);
            if (State.bridgeVirtualDisplay != null) {
                State.bridgeVirtualDisplay.setSurface(imageReader.getSurface());
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
            if (surfaceTexture != null) {
                surfaceTexture.setOnFrameAvailableListener(null);
                surfaceTexture.release();
                surfaceTexture = null;
            }
            if (textureId != 0) {
                int[] textures = {textureId};
                GLES20.glDeleteTextures(1, textures, 0);
                textureId = 0;
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }

    }
}