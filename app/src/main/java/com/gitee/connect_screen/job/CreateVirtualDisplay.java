package com.gitee.connect_screen.job;

import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.media.projection.IMediaProjection;
import android.media.projection.MediaProjectionHidden;
import android.os.Build;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.lang.reflect.Constructor;

import dev.rikka.tools.refine.Refine;

public class CreateVirtualDisplay {

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

    public static VirtualDisplay createVirtualDisplay(VirtualDisplayArgs virtualDisplayArgs, Surface surface) {
        int virtualDisplayWidth = virtualDisplayArgs.virtualDisplayWidth;
        if (ShizukuUtils.hasPermission()) {
            IDisplayManager displayManager = ServiceUtils.getDisplayManager();
            int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;
            //    | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;
            if (virtualDisplayArgs.rotatesWithContent) {
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
            VirtualDisplayConfig config = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                config = new VirtualDisplayConfig.Builder(
                        virtualDisplayArgs.virtualDisplayName,
                        virtualDisplayWidth, virtualDisplayArgs.monitorHeight, 160)
                        .setSurface(surface)
                        .setFlags(flags)
                        .setRequestedRefreshRate(virtualDisplayArgs.refreshRate)
                        .build();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                config = new VirtualDisplayConfig.Builder(
                        virtualDisplayArgs.virtualDisplayName,
                        virtualDisplayWidth, virtualDisplayArgs.monitorHeight, 160)
                        .setSurface(surface)
                        .setFlags(flags)
                        .build();
            } else {
                // config = null
            }
            IVirtualDisplayCallback callback = new ListenImageReaderAndPostFrame.VirtualDisplayCallback();
            IMediaProjection projection = null;
            if (State.mediaProjection != null) {
                MediaProjectionHidden mediaProjectionHidden = Refine.unsafeCast(State.mediaProjection);
                projection = mediaProjectionHidden.getProjection();
            }
            int displayId = -1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                displayId = displayManager.createVirtualDisplay(config, callback, projection, "com.android.shell");
            } else {
                displayId = displayManager.createVirtualDisplay(callback, projection, "com.android.shell", virtualDisplayArgs.virtualDisplayName, virtualDisplayWidth, virtualDisplayArgs.monitorHeight, 160, surface, flags, virtualDisplayArgs.virtualDisplayName);
            }
            DisplayInfo displayInfo = ServiceUtils.getDisplayManager().getDisplayInfo(displayId);
            State.log("创建虚拟显示成功，displayId: " + displayId + ", uniqueId: " + displayInfo.uniqueId);
            VirtualDisplay virtualDisplay = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                virtualDisplay = DisplayManagerGlobal.getInstance().createVirtualDisplayWrapper(config, callback, displayId);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                virtualDisplay = DisplayManagerGlobal.getInstance().createVirtualDisplayWrapper(config, null, callback, displayId);
            } else {
                try {
                    DisplayManagerGlobal displayManagerGlobal = DisplayManagerGlobal.getInstance();
                    Class<?> virtualDisplayClass = VirtualDisplay.class;
                    Constructor<?> constructor = virtualDisplayClass.getDeclaredConstructor(
                            DisplayManagerGlobal.class,
                            Display.class,
                            IVirtualDisplayCallback.class,
                            Surface.class
                    );
                    constructor.setAccessible(true);
                    Display display = displayManagerGlobal.getRealDisplay(displayId);
                    virtualDisplay = (VirtualDisplay) constructor.newInstance(
                            displayManagerGlobal,
                            display,
                            callback,
                            surface
                    );
                } catch(Throwable e) {
                    throw new RuntimeException(e);
                }
            }
            State.mediaProjection = null;
            return virtualDisplay;
        } else {
            VirtualDisplay virtualDisplay = State.mediaProjection.createVirtualDisplay(virtualDisplayArgs.virtualDisplayName,
                    virtualDisplayWidth, virtualDisplayArgs.monitorHeight, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null);
            State.mediaProjection = null;
            return virtualDisplay;
        }
    }
}
