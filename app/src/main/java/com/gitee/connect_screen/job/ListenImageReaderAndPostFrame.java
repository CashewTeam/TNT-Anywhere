package com.gitee.connect_screen.job;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.displaylink.manager.display.DisplayMode;
import com.gitee.connect_screen.DisplaylinkPref;
import com.gitee.connect_screen.LauncherActivity;
import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.ProjectionMode;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

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
    private int refreshRate;

    public void startVirtualDisplay(UsbState usbState, VirtualDisplayArgs virtualDisplayArgs) {
        this.usbState = usbState;
        this.monitorWidth = virtualDisplayArgs.monitorWidth;
        this.monitorHeight = virtualDisplayArgs.monitorHeight;
        this.refreshRate = virtualDisplayArgs.refreshRate;
        int virtualDisplayWidth = virtualDisplayArgs.virtualDisplayWidth;

        usbState.imageReader = ImageReader.newInstance(virtualDisplayWidth, monitorHeight, 1, 2);
        usbState.handlerThread = new HandlerThread("ImageAvailableListenerThread");
        usbState.handlerThread.start();
        usbState.handler = new Handler(usbState.handlerThread.getLooper());

        usbState.imageReader.setOnImageAvailableListener(this, usbState.handler);
        Surface surface = usbState.imageReader.getSurface();
        VirtualDisplay virtualDisplay = CreateVirtualDisplay.createVirtualDisplay(virtualDisplayArgs, surface);
        usbState.createdVirtualDisplay(virtualDisplay);
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        if (ShizukuUtils.hasPermission() && DisplaylinkPref.projectionMode == ProjectionMode.SINGLE_APP) {
            MainActivity mainActivity = State.currentActivity.get();
            String lastPackageName = null;
            if (mainActivity != null) {
                SharedPreferences appPreferences = mainActivity.getSharedPreferences("app_preferences", MODE_PRIVATE);
                lastPackageName = appPreferences.getString("LAST_PACKAGE_NAME", null);
            }
            if (DisplaylinkPref.autoOpenLastApp && lastPackageName != null) {
                Context context = State.currentActivity.get();
                ServiceUtils.launchPackage(context, lastPackageName, displayId);
                InputRouting.bindAllExternalInputToDisplay(displayId);
            } else {
                Context context = State.currentActivity.get();
                Intent intent = new Intent(context, LauncherActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
                context.startActivity(intent);
            }
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        if (usbState.getVirtualDisplay() == null) {
            return;
        }
        usbState.frameCounter++;
        Image thisImage = usbState.imageReader.acquireNextImage();
        Image.Plane plane = thisImage.getPlanes()[0];
        if (!hasSetMode) {
            hasSetMode = true;
            usbState.nativeDriver.setMode(usbState.encoderId, new DisplayMode(monitorWidth, monitorHeight, refreshRate), plane.getRowStride(), 1);
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

    public static class VirtualDisplayCallback extends IVirtualDisplayCallback.Stub {
        public void onPaused() {
        }
        public void onResumed() {
        }
        public void onStopped() {
        }
    }
}