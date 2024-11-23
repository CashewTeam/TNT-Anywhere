package com.gitee.connect_screen.job;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;

import java.nio.ByteBuffer;

public class ListenImageReaderAndPostFrame implements ImageReader.OnImageAvailableListener {
    private UsbState usbState;
    private boolean hasSetMode = false;
    private Image lastImage;
    private static byte[] rowDatas;
    private static int startX;
    private static int rowStride;
    private static int pixelStride;
    private int monitorWidth;
    private int monitorHeight;

    public void startVirtualDisplay(UsbState usbState) {
        this.usbState = usbState;
        this.monitorWidth = usbState.getMonitorWidth();
        this.monitorHeight = usbState.getMonitorHeight();
        int height = usbState.getMonitorHeight();
        int dpi = 160;

        WindowManager windowManager = (WindowManager) State.currentActivity.get().getSystemService(Context.WINDOW_SERVICE);
        WindowMetrics windowMetrics = windowManager.getMaximumWindowMetrics();

        int pxHeight = windowMetrics.getBounds().height();
        int pxWidth = windowMetrics.getBounds().width();

        int targetWidth = height * Math.max(pxWidth, pxHeight) / Math.min(pxWidth, pxHeight);

        usbState.imageReader = ImageReader.newInstance(targetWidth, height, 1, 2);
        usbState.handlerThread = new HandlerThread("ImageAvailableListenerThread");
        usbState.handlerThread.start();
        usbState.handler = new Handler(usbState.handlerThread.getLooper());

        usbState.imageReader.setOnImageAvailableListener(this, usbState.handler);
        Surface surface = usbState.imageReader.getSurface();

        usbState.virtualDisplay = State.mediaProjection.createVirtualDisplay("DisplayLink",
                targetWidth, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, null, null);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        usbState.frameCounter++;
        Image thisImage = usbState.imageReader.acquireNextImage();
        Image.Plane plane = thisImage.getPlanes()[0];
        if (!hasSetMode) {
            hasSetMode = true;
            usbState.nativeDriver.setMode(usbState.encoderId, usbState.getDisplayMode(), plane.getRowStride(), 1);
            Log.i("FixAspectRatio", "cropImageTo1080p: " + thisImage.getWidth() + "x" + thisImage.getHeight() + " pixelStride: " + plane.getPixelStride() + " rowStride: " + plane.getRowStride());
            ByteBuffer buffer = plane.getBuffer();
            Log.i("FixAspectRatio", "Buffer info - capacity: " + buffer.capacity() + " position: " + buffer.position() + " limit: " + buffer.limit() + " remaining: " + buffer.remaining());
            int imageWidth = thisImage.getWidth();
            pixelStride = plane.getPixelStride();
            rowStride = plane.getRowStride();
            startX = ((imageWidth - monitorWidth) / 2) * pixelStride;
            rowDatas = new byte[monitorWidth * pixelStride];
        }
        ByteBuffer buffer = plane.getBuffer();
        for (int row = 0; row < monitorHeight; row++) {
            buffer.position(row * rowStride + startX);
            buffer.get(rowDatas, 0, monitorWidth * pixelStride);
            buffer.position(row * rowStride);
            buffer.put(rowDatas);
        }
        buffer.rewind();
        int resultCode = usbState.nativeDriver.postFrame(usbState.encoderId, buffer);
        usbState.recentPostFrameResultCodes[usbState.frameCounter % usbState.recentPostFrameResultCodes.length] = resultCode;
        if (resultCode < 0) {
            Log.e("displaylink", "postFrame failed, resultCode: " + resultCode);
        }
        boolean buffered = resultCode != 1 && resultCode != -2;
        if (buffered) {
            if (lastImage != null) {
                lastImage.close();
            }
            lastImage = thisImage;
        } else{
            thisImage.close();
        }
    }
}