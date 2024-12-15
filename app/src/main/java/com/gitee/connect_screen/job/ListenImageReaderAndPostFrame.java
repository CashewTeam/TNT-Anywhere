package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IInputManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;
import android.view.DisplayInfo;
import android.view.InputDevice;
import android.view.Surface;

import com.displaylink.manager.display.DisplayMode;
import com.gitee.connect_screen.DisplaylinkPref;
import com.gitee.connect_screen.LauncherActivity;
import com.gitee.connect_screen.ProjectionMode;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ListenImageReaderAndPostFrame implements ImageReader.OnImageAvailableListener {

    // Internal fields copied from android.hardware.display.DisplayManager
    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

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

    public void startVirtualDisplay(UsbState usbState, MirrorArgs mirrorArgs) {
        this.usbState = usbState;
        this.monitorWidth = mirrorArgs.monitorWidth;
        this.monitorHeight = mirrorArgs.monitorHeight;
        this.refreshRate = mirrorArgs.refreshRate;
        int virtualDisplayWidth = mirrorArgs.virtualDisplayWidth;

        usbState.imageReader = ImageReader.newInstance(virtualDisplayWidth, monitorHeight, 1, 2);
        usbState.handlerThread = new HandlerThread("ImageAvailableListenerThread");
        usbState.handlerThread.start();
        usbState.handler = new Handler(usbState.handlerThread.getLooper());

        usbState.imageReader.setOnImageAvailableListener(this, usbState.handler);
        Surface surface = usbState.imageReader.getSurface();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ShizukuUtils.hasPermission()) {
            IDisplayManager displayManager = ServiceUtils.getDisplayManager();
            int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
           | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;
        //    | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;
            if (DisplaylinkPref.rotatesWithContent) {
                flags |= VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT;
            }
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_33_ANDROID_13) {
                flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED
                       | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                       | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                       | VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED;
               if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                flags |= VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
                //    flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                //            | VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
               }
            }
            VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "DisplayLink",
                virtualDisplayWidth, monitorHeight, 160)
                .setSurface(surface)
                .setFlags(flags)
                .setRequestedRefreshRate(mirrorArgs.refreshRate)
                .build();
            IVirtualDisplayCallback callback = new VirtualDisplayCallback();
            int displayId = displayManager.createVirtualDisplay(config, callback, null, "com.android.shell");
            DisplayInfo displayInfo = ServiceUtils.getDisplayManager().getDisplayInfo(displayId);
            State.log("创建虚拟显示成功，displayId: " + displayId + ", uniqueId: " + displayInfo.uniqueId);
            VirtualDisplay virtualDisplay = DisplayManagerGlobal.getInstance().createVirtualDisplayWrapper(config, callback, displayId);
            usbState.createdVirtualDisplay(
                    virtualDisplay
            );
            if (DisplaylinkPref.projectionMode == ProjectionMode.SINGLE_APP) {
                Context context = State.currentActivity.get();
                Intent intent = new Intent(context, LauncherActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
                context.startActivity(intent);
            }
        } else {
            usbState.createdVirtualDisplay(
                State.mediaProjection.createVirtualDisplay("DisplayLink",
                    virtualDisplayWidth, monitorHeight, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null)
            );
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