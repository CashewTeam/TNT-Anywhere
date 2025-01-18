package com.gitee.connect_screen;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.hardware.display.VirtualDisplay;
import android.hardware.usb.UsbDevice;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.util.Log;

import com.gitee.connect_screen.job.Job;
import com.gitee.connect_screen.job.YieldException;
import com.gitee.connect_screen.shizuku.IUserService;
import com.gitee.connect_screen.shizuku.UserService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rikka.shizuku.Shizuku;

public class State {
    // 弱引用保存当前的 MainActivity 实例
    public static WeakReference<Activity> currentActivity;
    public static BreadcrumbManager breadcrumbManager;
    public static boolean hasService = false;
    private static Job currentJob;
    public static List<String> logs = new ArrayList<>();
    public static DisplaylinkState displaylinkState = new DisplaylinkState();
    private static MediaProjection mediaProjection;
    public static MediaProjection mediaProjectionInUse;
    public static int lastSingleAppDisplay;
    public static String displaylinkDeviceName;
    public static volatile IUserService userService;
    public static VirtualDisplay bridgeVirtualDisplay;
    public static int bridgeDisplayId = -1;
    public static VirtualDisplay mirrorVirtualDisplay;
    public static int mirrorDisplayId = -1;
    public static FloatingButtonService floatingButtonService;
    public static Activity isInPureBlackActivity = null;
    public static String mirrorStatus = null;

    private static final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public static final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            State.log("user service connected");
            State.userService = IUserService.Stub.asInterface(binder);
            if (State.currentActivity.get() != null) {
                State.currentActivity.get().runOnUiThread(() -> {
                    State.resumeJob();
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            State.log("user service disconnected");
        }
    };

    public static final Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
                    .daemon(true)
                    .tag("temp6")
                    .processNameSuffix("connect-screen")
                    .debuggable(false)
                    .version(BuildConfig.VERSION_CODE);

    public static boolean isJobRunning() {
        return currentJob != null;
    }   

    public static void startNewJob(Job job) {
        if (currentJob != null) {
            if (currentActivity != null && currentActivity.get() != null) {
                State.log("当前任务 " + currentJob.getClass().getSimpleName() + " 正在进行中");
            }
            return;
        }
        currentJob = job;
        try {
            State.log("开始任务 " + job.getClass().getSimpleName());
            currentJob.start();
            State.log("任务 " + job.getClass().getSimpleName() + " 完成");
            currentJob = null;
        } catch (YieldException e) {
            State.log("任务 " + job.getClass().getSimpleName() + " 暂停, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("任务 " + job.getClass().getSimpleName() + " 启动失败");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("堆栈跟踪: " + stackTrace);
            currentJob = null;
        }
        breadcrumbManager.refreshCurrentFragment();
    }

    public static void resumeJob() {
        if (currentJob == null) {
            breadcrumbManager.refreshCurrentFragment();
            return;
        }
        try {
            State.log("恢复任务 " + currentJob.getClass().getSimpleName());
            currentJob.start();
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 完成");
            currentJob = null;
        } catch (YieldException e) {
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 暂停, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 恢复失败");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("堆栈跟踪: " + stackTrace);
            currentJob = null;
        }
        breadcrumbManager.refreshCurrentFragment();
    }

    public static void resumeJobLater(long delayMillis) {
        if (currentActivity.get() != null) {
            mainHandler.postDelayed(() -> {
                resumeJob();
            }, delayMillis);
        }
    }

    public static void log(String message) {
        logs.add(message);
        Log.i("ConnectScreen", message);
        if (currentActivity != null && currentActivity.get() != null) {
            IMainActivity  mainActivity = (IMainActivity) currentActivity.get();
            mainActivity.updateLogs();
        }
    }

    public static void unbindUserService() {
        try {
            if (userService == null) {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true); // 解绑用户服务
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    public static void setMediaProjection(MediaProjection newMediaProjection) {
        if (newMediaProjection == null) {
            mediaProjection = null;
        } else {
            mediaProjection = newMediaProjection;
            mediaProjectionInUse = newMediaProjection;
        }
    }

    public static int getDisplaylinkVirtualDisplayId() {
        if (displaylinkState.getVirtualDisplay() == null) {
            return -1;
        }
        return displaylinkState.getVirtualDisplay().getDisplay().getDisplayId();
    }

    public static int getBridgeVirtualDisplayId() {
        if (bridgeVirtualDisplay == null) {
            return -1;
        }
        return bridgeVirtualDisplay.getDisplay().getDisplayId();
    }

    public static int getMirrorVirtualDisplayId() {
        if (mirrorVirtualDisplay == null) {
            return -1;
        }
        return mirrorVirtualDisplay.getDisplay().getDisplayId();
    }
}
