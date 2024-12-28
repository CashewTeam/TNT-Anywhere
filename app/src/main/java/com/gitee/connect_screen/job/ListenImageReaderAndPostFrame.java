package com.gitee.connect_screen.job;

import android.hardware.display.IVirtualDisplayCallback;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.displaylink.manager.display.DisplayMode;
import com.gitee.connect_screen.DisplaylinkPref;
import com.gitee.connect_screen.ProjectionMode;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.DisplaylinkState;

import java.nio.ByteBuffer;

public class ListenImageReaderAndPostFrame implements ImageReader.OnImageAvailableListener {

    private DisplaylinkState displaylinkState;
    private boolean hasSetMode = false;
    private Image lastImage;
    private static byte[] rowDatas;
    private static int startX;
    private static int rowStride;
    private static int pixelStride;
    private int monitorWidth;
    private int monitorHeight;
    private int refreshRate;

    public ListenImageReaderAndPostFrame(VirtualDisplayArgs virtualDisplayArgs) {
        this.displaylinkState = State.displaylinkState;
        this.monitorWidth = virtualDisplayArgs.monitorWidth;
        this.monitorHeight = virtualDisplayArgs.monitorHeight;
        this.refreshRate = virtualDisplayArgs.refreshRate;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        if (displaylinkState.encoderId == 0) {
            displaylinkState.imageReader.acquireNextImage().close();
            return;
        }
        try {
            displaylinkState.frameCounter++;
            Image thisImage = displaylinkState.imageReader.acquireNextImage();
            Image.Plane plane = thisImage.getPlanes()[0];
            if (!hasSetMode) {
                hasSetMode = true;
                displaylinkState.nativeDriver.setMode(displaylinkState.encoderId, new DisplayMode(monitorWidth, monitorHeight, refreshRate), plane.getRowStride(), 1);
                int imageWidth = thisImage.getWidth();
                pixelStride = plane.getPixelStride();
                rowStride = plane.getRowStride();
                startX = ((imageWidth - monitorWidth) / 2) * pixelStride;
                rowDatas = new byte[monitorWidth * pixelStride];
            }
            ByteBuffer buffer = plane.getBuffer();
            if (DisplaylinkPref.projectionMode == ProjectionMode.MIRROR_AND_CROP_16_9) {
                for (int row = 0; row < monitorHeight; row++) {
                    buffer.position(row * rowStride + startX);
                    buffer.get(rowDatas, 0, monitorWidth * pixelStride);
                    buffer.position(row * rowStride);
                    buffer.put(rowDatas);
                }
                buffer.rewind();
            }
            int resultCode = displaylinkState.nativeDriver.postFrame(displaylinkState.encoderId, buffer);
            displaylinkState.recentPostFrameResultCodes[displaylinkState.frameCounter % displaylinkState.recentPostFrameResultCodes.length] = resultCode;
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
        } catch (Throwable e) {
            Log.e("ImageReader", "failed to post frame", e);
        }
    }

    public static class VirtualDisplayCallback extends IVirtualDisplayCallback.Stub {
        public void onPaused() {
        }
        public void onResumed() {
        }
        public void onStopped() {
        }
    }
}