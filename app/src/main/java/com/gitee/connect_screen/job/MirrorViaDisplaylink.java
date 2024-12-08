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
import com.gitee.connect_screen.ProjectionMode;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.UsbState;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class MirrorViaDisplaylink implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private boolean usbRequested = false;
    private boolean device2UsbRequested = false;
    private boolean mediaProjectionRequested = false;
    private final String deviceName;
    private boolean userServiceRequested = false;
    private final MirrorArgs mirrorArgs;

    public MirrorViaDisplaylink(UsbDevice device, MirrorArgs mirrorArgs) {
        this.deviceName = device.getDeviceName();
        this.mirrorArgs = mirrorArgs;
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

        if (usbState.projectionMode == ProjectionMode.SINGLE_APP) {
            if (!ShizukuUtils.hasPermission()) {
                acquireShizuku.start();
                if (!acquireShizuku.acquired) {
                    return;
                }
            }
            if (State.userService == null && !userServiceRequested) {
                userServiceRequested = true;
                Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
                Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
                State.resumeJobLater(1000);
                throw new YieldException("等待 user service 启动");
            }
        }

        if (usbState.displaylinkDevice2 != null && usbState.getVirtualDisplay() != null) {
            usbState.destroy();
        }

        if (!requestUsbPermission(context, usbManager, usbState.device)) {
            return;
        }
        if (!requestDevice2UsbPermission(context, usbManager, usbState)) {
            return;
        }
        openUsbConnection(context, usbManager, usbState);
        if (!initializeNativeDriver(context, usbState)) {
            return;
        }
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
        if (usbState.displaylinkDevice2 == null) {
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
        } else {
            if (usbState.displaylinkConnection2 == null || usbState.usbConnection == null || usbState.usbConnection.getRawDescriptors() == null) {
                if (usbState.usbConnection != null) {
                    usbState.usbConnection.close();
                }
                usbState.usbConnection = usbManager.openDevice(usbState.device);
                if (usbState.usbConnection == null) {
                    throw new RuntimeException("无法打开 USB 设备连接");
                } else {
                    State.log("成功打开 USB 设备连接");
                }
                usbState.displaylinkConnection2 = usbManager.openDevice(usbState.displaylinkDevice2);
                if (usbState.displaylinkConnection2 == null) {
                    throw new RuntimeException("无法打开第二个 USB 设备连接");
                } else {
                    State.log("成功打开第二个 USB 设备连接");
                }
            } else {
                State.log("第二个 USB 设备连接已存在");
            }
        }
        if (usbState.usbConnection.getRawDescriptors() == null) {
            throw new RuntimeException("USB 连接无法获得 raw descriptors");
        }
    }

    private boolean requestDevice2UsbPermission(Context context, UsbManager usbManager, UsbState usbState) throws YieldException {
        if (usbState.displaylinkDevice2 == null) {
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getDeviceName().equals(usbState.device.getDeviceName())) {
                    continue;
                }
                if (device.getVendorId() == 6121) {
                    usbState.displaylinkDevice2 = device;
                    break;
                }
            }
        }
        if (usbState.displaylinkDevice2 == null) {
            return true;
        }
        if (usbManager.hasPermission(usbState.displaylinkDevice2)) {
            State.log("已经拥有第二个 USB 设备权限: " + usbState.displaylinkDevice2.getDeviceName());
            return true;
        } else if (device2UsbRequested) {
            State.log("因为未授予第二个 USB 设备权限: " + usbState.displaylinkDevice2.getDeviceName() + "，跳过任务");
            return false;
        }
        device2UsbRequested = true;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(usbState.displaylinkDevice2, pendingIntent);
        throw new YieldException("等待用户第二个 USB 授权");
    }
    private boolean initializeNativeDriver(Context context, UsbState usbState) throws YieldException {
        if (usbState.displaylinkDevice2 != null && usbState.monitorInfo == null) {
            if (usbState.nativeDriver != null) {
                usbState.nativeDriver.destroy();
                usbState.nativeDriver = null;
            }
        }
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
            if (usbState.displaylinkDevice2 != null) {
                usbState.nativeDriver.usbDeviceDetached(usbState.displaylinkDevice2.getDeviceName());
            }
            resultCode = usbState.nativeDriver.usbDeviceAttached(deviceName, usbState.usbConnection.getFileDescriptor(), usbState.usbConnection.getRawDescriptors(), usbState.usbConnection.getRawDescriptors().length);
            if (resultCode != 0) {
                throw new RuntimeException("附加USB设备失败: " + resultCode);
            } else {
                State.log("附加USB设备成功");
            }
            if (usbState.displaylinkDevice2 != null) {
                resultCode = usbState.nativeDriver.usbDeviceAttached(usbState.displaylinkDevice2.getDeviceName(), usbState.displaylinkConnection2.getFileDescriptor(), usbState.displaylinkConnection2.getRawDescriptors(), usbState.displaylinkConnection2.getRawDescriptors().length);
                if (resultCode != 0) {
                    throw new RuntimeException("附加第二个USB设备失败: " + resultCode);
                } else {
                    State.log("附加第二个USB设备成功");
                }
            }
        } else {
            State.log("NativeDriver 已经存在，跳过重复创建");
        }

        if (usbState.monitorInfo == null) {
            State.log("未找到显示器信息, 请连接显示器之后重试");
            return false;
        }
        return true;
    }

    private boolean requestMediaProjectionPermission(Context context, UsbState usbState) throws YieldException {
        if (usbState.projectionMode == ProjectionMode.SINGLE_APP) {
            usbState.stopVirtualDisplay();
            return true;
        }
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
        new ListenImageReaderAndPostFrame().startVirtualDisplay(usbState, mirrorArgs);
//        new ListenOpenglAndPostFrame().startVirtualDisplay(usbState);
        State.mediaProjection = null;
        State.log("虚拟显示已创建");
    }
}