package com.gitee.connect_screen.job;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjectionManager;

import com.displaylink.manager.NativeDriver;
import com.displaylink.manager.NativeDriverListener;
import com.gitee.connect_screen.MainActivity;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;

public class MirrorViaDisplaylink implements Job {
    private boolean usbRequested = false;
    private boolean mediaProjectionRequested = false;
    private final String deviceName;

    public MirrorViaDisplaylink(UsbDevice device) {
        this.deviceName = device.getDeviceName();
        State.getOrCreateUsbState(device);
    }

    public void start() throws YieldException {
        Context context = State.currentActivity.get();
        if (context == null) {
            State.log("Activity 不存在，跳过任务");
            return;
        }
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbState usbState = State.getUsbState(deviceName);

        if (usbState == null) {
            State.log("USB 设备 " + deviceName + " 状态不存在，跳过任务");
            return;
        }

        if (!requestUsbPermission(context, usbManager, usbState.device)) {
            return;
        }
        openUsbConnection(context, usbManager, usbState);
        initializeNativeDriver(context, usbState);
        if (!requestMediaProjectionPermission(context, usbState)) {
            return;
        }
        createVirtualDisplay(context, usbState);
    }

    private boolean requestUsbPermission(Context context, UsbManager usbManager, UsbDevice device) throws YieldException {
        if (usbManager.hasPermission(device)) {
            State.log("已经拥有USB设备权限: " + device.getDeviceName());
        } else if (usbRequested) {
            State.log("因为未授予USB设备权限: " + device.getDeviceName() + "，跳过任务");
            return false;
        } else {
            usbRequested = true;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, pendingIntent);
            throw new YieldException("等待用户USB授权");
        }
        return true;
    }

    private void openUsbConnection(Context context, UsbManager usbManager, UsbState usbState) {
        if (usbState.usbConnection == null) {
            usbState.usbConnection = usbManager.openDevice(usbState.device);
            if (usbState.usbConnection == null) {
                throw new RuntimeException("无法打开 USB 设备连接");
            } else {
                State.log("成功打开 USB 设备连接");
            }
        } else {
            State.log("USB 设备连接已存在");
        }
    }

    private void initializeNativeDriver(Context context, UsbState usbState) throws YieldException {
        if (usbState.nativeDriver == null) {
            usbState.nativeDriver = new NativeDriver();
            usbState.nativeDriverListener = new NativeDriverListener(deviceName);
            usbState.nativeDriver.destroy();
            int resultCode = usbState.nativeDriver.create(usbState.nativeDriverListener, context.getFilesDir().toString(), false);
            if (resultCode != 0) {
                throw new RuntimeException("创建NativeDriver失败: " + resultCode);
            } else {
                State.log("创建NativeDriver成功");
            }
            usbState.nativeDriver.usbDeviceDetached(deviceName);
            resultCode = usbState.nativeDriver.usbDeviceAttached(deviceName, usbState.usbConnection.getFileDescriptor(), usbState.usbConnection.getRawDescriptors(), usbState.usbConnection.getRawDescriptors().length);
            if (resultCode != 0) {
                throw new RuntimeException("附加USB设备失败: " + resultCode);
            } else {
                State.log("附加USB设备成功");
            }
        } else {
            State.log("NativeDriver 已经存在，跳过重复创建");
        }

        if (usbState.monitorInfo == null) {
            throw new YieldException("未找到显示器信息, 等待连接ing");
        }
    }

    private boolean requestMediaProjectionPermission(Context context, UsbState usbState) throws YieldException {
        if (State.mediaProjection != null) {
            State.log("MediaProjection 已经存在，跳过重复请求");
            return true;
        }
        if (mediaProjectionRequested) {
            if (!State.hasService) {
                throw new YieldException("等待服务启动");
            }
            State.log("因为未授予投屏权限，跳过任务");
            return false;
        }
        usbState.stopVirtualDisplay();
        mediaProjectionRequested = true;
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            State.currentActivity.get().startActivityForResult(captureIntent, MainActivity.REQUEST_CODE_MEDIA_PROJECTION);
            throw new YieldException("等待用户投屏授权");
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }

    private void createVirtualDisplay(Context context, UsbState usbState) {
        if (usbState.getVirtualDisplay() != null) {
            State.log("虚拟显示已存在，跳过重复创建");
            return;
        }
        new ListenImageReaderAndPostFrame().startVirtualDisplay(usbState);
//        new ListenOpenglAndPostFrame().startVirtualDisplay(usbState);
        State.mediaProjection = null;
        State.log("虚拟显示已创建");
    }
}