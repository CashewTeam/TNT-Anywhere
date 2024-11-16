package com.gitee.connect_screen.job;

import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;

public class ListenAndPostFrame implements ImageReader.OnImageAvailableListener {
    private final UsbState usbState;
    private boolean hasSetMode = false;
    private Image lastImage;

    public ListenAndPostFrame(UsbState usbState) {
        this.usbState = usbState;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image thisImage = usbState.imageReader.acquireNextImage();
        Image.Plane plane = thisImage.getPlanes()[0];
        if (!hasSetMode) {
            hasSetMode = true;
            usbState.nativeDriver.setMode(usbState.encoderId, usbState.monitorInfo.a[0], plane.getRowStride(), 1);
        }
        int resultCode = usbState.nativeDriver.postFrame(usbState.encoderId, plane.getBuffer());
        if (resultCode < 0) {
            Log.e("displaylink", "postFrame failed, resultCode: " + resultCode);
        }
        boolean buffered = resultCode != 1 && resultCode != -2;
        if (buffered) {
            if (lastImage != null) {
                lastImage.close();
            }
            lastImage = thisImage;
            return;
        }
        thisImage.close();
    }
}
