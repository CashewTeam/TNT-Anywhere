package com.connect_screen.extend.job;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.IAudioService;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Display;

import com.connect_screen.extend.BridgePref;
import com.connect_screen.extend.State;
import com.connect_screen.extend.shizuku.ServiceUtils;
import com.connect_screen.extend.shizuku.ShizukuUtils;

import java.util.List;

public class DisplayMonitor {
    private static boolean registered = false;
    public static void init(DisplayManager displayManager) {
        if (registered) {
            return;
        }
        registered = true;
        for (Display display : displayManager.getDisplays()) {
            handleNewDisplay(display);
        }
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                State.log("新增显示器，displayId: " + displayId);
                Display display = displayManager.getDisplay(displayId);
                if (display != null) {
                    handleNewDisplay(display);
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                State.log("移除显示器，displayId: " + displayId);
                if (State.floatingButtonService != null) {
                    State.floatingButtonService.onDisplayRemoved(displayId);
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // 显示器状态变化时的处理
                if (State.floatingButtonService != null) {
                    State.floatingButtonService.onDisplayChanged(displayId);
                }
            }
        }, null);
    }

    private static void handleNewDisplay(Display display) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return;
        }
        Context context = State.currentActivity.get();
        if (context == null) {
            return;
        }
        handleAutoOpenLastApp(context, display);
        handleDisableUsbAudio(context);
    }

    private static void handleDisableUsbAudio(Context context) {
        if (!ShizukuUtils.hasPermission()) {
            return;
        }
        boolean isDisabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("usb_audio_disabled", false);
        if (!isDisabled) {
            return;
        }
        IAudioService audioManager = ServiceUtils.getAudioManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<AudioDeviceAttributes> devices = audioManager.getDevicesForAttributes(new AudioAttributes.Builder().build());
            for (AudioDeviceAttributes device : devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_HDMI) {
                    try {
                        audioManager.setWiredDeviceConnectionState(device, 0, "com.android.shell");
                        State.log("禁用音频输出设备：" + device);
                    } catch(Throwable e) {
                        State.log("禁用音频输出设备失败: " + e);
                    }
                }
            }
        } else {
            AudioManager audioManager2 = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo device : audioManager2.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_HDMI) {
                    try {
                        audioManager.setWiredDeviceConnectionState(device.getType(), 0, device.getAddress(), "", "com.android.shell");
                        State.log("禁用音频输出设备：" + device.getType() + ", " + device.getProductName());
                    } catch(Throwable e) {
                        State.log("禁用音频输出设备失败: " + e);
                    }
                } else if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    try {
                        audioManager.setWiredDeviceConnectionState(device.getType(), 1, device.getAddress(), "", "com.android.shell");
                        State.log("启用音频输出设备：" + device.getType() + ", " + device.getProductName());
                    } catch(Throwable e) {
                        State.log("启用音频输出设备失败: " + e);
                    }
                }
            }
        }
    }

    private static void handleAutoOpenLastApp(Context context, Display display) {
        SharedPreferences appPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        boolean autoBridge = appPreferences.getBoolean("AUTO_BRIDGE_" + display.getName(), false);
        if (ShizukuUtils.hasPermission() && (autoBridge || display.getDisplayId() == State.bridgeDisplayId)) {
            BridgePref.load(context);
            new Handler().postDelayed(() -> {
                DisplayMetrics metrics = new DisplayMetrics();
                display.getMetrics(metrics);
                State.startNewJob(new ProjectViaBridge(display, new VirtualDisplayArgs("桥接屏幕", display.getWidth(), display.getHeight(), (int) display.getRefreshRate(), metrics.densityDpi, BridgePref.rotatesWithContent)));
            }, 500);
            return;
        }
        boolean autoOpen = appPreferences.getBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), false);
        if (!autoOpen) {
            return;
        }
        String lastPackageName = appPreferences.getString("LAST_PACKAGE_NAME", null);
        if (lastPackageName == null) {
            return;
        }
        State.log("尝试自动打开显示器 " + display.getName() + " 上投屏的应用 " + lastPackageName);
        new Handler().postDelayed(() -> {
            ServiceUtils.launchPackage(context, lastPackageName, display.getDisplayId());
        }, 500);
    }
}
