package com.gitee.connect_screen.shizuku;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityOptionsHidden;
import android.app.PendingIntentHidden;
import android.content.ContextHidden;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayManager;
import android.hardware.input.IInputManager;
import android.os.Build;
import android.view.WindowManager;
import android.view.IWindowManager;

import com.gitee.connect_screen.State;

import dev.rikka.tools.refine.Refine;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

import java.util.List;

public class ServiceUtils {
    private static IActivityManager activityManager;
    private static IActivityTaskManager activityTaskManager;
    private static IWindowManager windowManager;
    private static IDisplayManager displayManager;
    private static IInputManager inputManager;

    public static void initWithShizuku() {
        activityTaskManager = IActivityTaskManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")));
        activityManager = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)));
        windowManager = IWindowManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.WINDOW_SERVICE)));
        displayManager = IDisplayManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE)));
        inputManager = IInputManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)));
    }

    /**
     * 判断某个服务是否正在运行的方法
     *
     * @param mContext    上下文
     * @param serviceName 是包名+服务的类名（例如：net.loonggg.testbackstage.TestService）
     * @return true代表正在运行，false代表服务没有正在运行
     */
    public static boolean isServiceWork(Context mContext, String serviceName) {
        boolean isWork = false;
        ActivityManager myAM = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> myList = myAM.getRunningServices(40);
        if (myList.isEmpty()) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            String mName = myList.get(i).service.getClassName();
            if (mName.equals(serviceName)) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }

    public static int startActivity(Intent intent, ActivityOptions options) {
        if (activityManager == null) {
            throw new IllegalStateException("ServiceUtils 未初始化，请先调用 initWithShizuku()");
        }
        
        try {
            return activityManager.startActivityAsUserWithFeature(
                null, "com.android.shell", null, intent,
                intent.getType(), null, null, 0, 0,
                null, options.toBundle(), 0
            );
        } catch (Exception e) {            
            State.log("failed to start activity: " + e.getMessage());
            return -1;
        }
    }

    public static int callPendingIntent(PendingIntent pendingIntent, ActivityOptions options, int displayId) {
        if (activityManager == null) {
            throw new IllegalStateException("ServiceUtils 未初始化，请先调用 initWithShizuku()");
        }
        
        try {
            PendingIntentHidden pendingIntentHidden = Refine.unsafeCast(pendingIntent);
            ActivityOptionsHidden optionsHidden = Refine.unsafeCast(options);
            optionsHidden.setCallerDisplayId(displayId);

            return activityManager.sendIntentSender(
                pendingIntentHidden.getTarget(), pendingIntentHidden.getWhitelistToken(), 0, null,
                null, null, null, optionsHidden.toBundle()
            );
        } catch (Exception e) {
            State.log("failed to send pending intent: " + e.getMessage());
            return -1;
        }
    }

    public static IWindowManager getWindowManager() {
        if (windowManager == null) {
            throw new IllegalStateException("ServiceUtils 未初始化，请先调用 initWithShizuku()");
        }
        return windowManager;
    }

    public static IDisplayManager getDisplayManager() {
        if (displayManager == null) {
            initWithShizuku();
        }
        return displayManager;
    }

    public static IInputManager getInputManager() {
        if (inputManager == null) {
            initWithShizuku();
        }
        return inputManager;
    }
}