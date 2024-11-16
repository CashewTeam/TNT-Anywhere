package com.gitee.connect_screen.job;

import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;

import java.nio.ByteBuffer;

public class ListenAndPostFrame implements ImageReader.OnImageAvailableListener {
    private final UsbState usbState;
    private boolean hasSetMode = false;
    private Image lastImage;
    private static byte[] rowDatas;
    private static int startX;
    private static int rowStride;
    private static int pixelStride;

    public ListenAndPostFrame(UsbState usbState) {
        this.usbState = usbState;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        usbState.frameCounter++;
        Image thisImage = usbState.imageReader.acquireNextImage();
        Image.Plane plane = thisImage.getPlanes()[0];
        if (!hasSetMode) {
            hasSetMode = true;
            usbState.nativeDriver.setMode(usbState.encoderId, usbState.monitorInfo.a[0], plane.getRowStride(), 1);
            Log.i("FixAspectRatio", "cropImageTo1080p: " + thisImage.getWidth() + "x" + thisImage.getHeight() + " pixelStride: " + plane.getPixelStride() + " rowStride: " + plane.getRowStride());
            ByteBuffer buffer = plane.getBuffer();
            Log.i("FixAspectRatio", "Buffer info - capacity: " + buffer.capacity() + " position: " + buffer.position() + " limit: " + buffer.limit() + " remaining: " + buffer.remaining());
            int imageWidth = thisImage.getWidth();
            pixelStride = plane.getPixelStride();
            rowStride = plane.getRowStride();
            startX = ((imageWidth - 1920) / 2) * pixelStride;
            rowDatas = new byte[1920 * pixelStride];
        }
        ByteBuffer buffer = plane.getBuffer();
        for (int row = 0; row < 1080; row++) {
            buffer.position(row * rowStride + startX);
            buffer.get(rowDatas, 0, 1920 * pixelStride);
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