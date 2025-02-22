package com.connect_screen.extend.job;

import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.displaylink.manager.display.DisplayMode;
import com.connect_screen.extend.State;
import com.connect_screen.extend.DisplaylinkState;

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
    private int frameCounter = 0;
    private long lastFrameTimeStamp = System.currentTimeMillis();

    public ListenImageReaderAndPostFrame(VirtualDisplayArgs virtualDisplayArgs) {
        this.displaylinkState = State.displaylinkState;
        this.monitorWidth = virtualDisplayArgs.width;
        this.monitorHeight = virtualDisplayArgs.height;
        this.refreshRate = virtualDisplayArgs.refreshRate;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        frameCounter++;
        if (frameCounter % 240 == 0) {
            long currentTime = System.currentTimeMillis();
            long timeElapsed = currentTime - lastFrameTimeStamp;
            float frameDuration = ((float)timeElapsed) / ((float)240);
            if (State.displaylinkState.frameDuration <= 0) {
                State.displaylinkState.frameDuration = frameDuration;
            } else if (frameDuration < State.displaylinkState.frameDuration) {
                State.displaylinkState.frameDuration = frameDuration;
            }
            lastFrameTimeStamp = currentTime;
        }
        try {
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
            int resultCode = displaylinkState.nativeDriver.postFrame(displaylinkState.encoderId, buffer);
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
}