package com.gitee.connect_screen.shizuku;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityOptionsHidden;
import android.app.PendingIntentHidden;
import android.content.ComponentName;
import android.content.ContextHidden;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayManager;
import android.hardware.input.IInputManager;
import android.os.Build;
import android.view.WindowManager;
import android.view.IWindowManager;
import android.widget.Toast;

import com.gitee.connect_screen.BridgeActivity;
import com.gitee.connect_screen.State;
import com.gitee.connect_screen.job.BindAllExternalInputToDisplay;

import dev.rikka.tools.refine.Refine;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

import java.util.List;

public class ServiceUtils {
    private static final int WINDOWING_MODE_FULLSCREEN = 1;

    private static IActivityManager activityManager;
    private static IActivityTaskManager activityTaskManager;
    private static IWindowManager windowManager;
    private static IDisplayManager displayManager;
    private static IInputManager inputManager;

    private static void initWithShizuku() {
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
            initWithShizuku();
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                return activityManager.startActivityAsUserWithFeature(
                        null, "com.android.shell", null, intent,
                        intent.getType(), null, null, 0, 0,
                        null, options.toBundle(), 0
                );
            } else {
                return activityManager.startActivity(
                        null, "com.android.shell", intent,
                        intent.getType(), null, null, 0, 0,
                        null, options.toBundle()
                );
            }
        } catch (Exception e) {            
            State.log("failed to start activity: " + e.getMessage());
            return -1;
        }
    }

    public static void launchPackage(Context context, String packageName, int targetDisplayId) {
        if (ShizukuUtils.hasPermission()) {
            launchAppWithShizuku(packageName, context, targetDisplayId);
            return;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launchAppNormally(packageName, context, targetDisplayId);
            } else {
                if (State.getDisplaylinkVirtualDisplayId() == targetDisplayId || State.getBridgeVirtualDisplayId() == targetDisplayId) {
                    launchAppWithShizuku(packageName, context, targetDisplayId);
                } else {
                    launchAppNormally(packageName, context, targetDisplayId);
                }
            }
        } catch (Exception e) {
            launchAppWithShizuku(packageName, context, targetDisplayId);
        }
    }

    private static void launchAppNormally(String packageName, Context context, int targetDisplayId) {
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            context.startActivity(launchIntent, options.toBundle());
            State.startNewJob(new BindAllExternalInputToDisplay(targetDisplayId));
        }
    }

    private static void launchAppWithShizuku(String packageName, Context context, int targetDisplayId) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PackageManager packageManager = context.getPackageManager();
            ComponentName componentName = packageManager.getLaunchIntentForPackage(packageName).getComponent();
            intent.setComponent(componentName);
            intent.setPackage(packageName);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(targetDisplayId);
            ActivityOptionsHidden optionsHidden = Refine.unsafeCast(options);
            optionsHidden.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
            int result = ServiceUtils.startActivity(intent, options);
            if (result < 0) {
                Toast.makeText(context, "使用 Shizuku 启动应用失败", Toast.LENGTH_SHORT).show();
                State.log("使用 Shizuku 启动应用失败，返回值: " + result);
            } else {
                State.log("使用 Shizuku 启动应用成功: " + packageName);
            }
        } catch (Exception e) {
            Toast.makeText(context, "使用 Shizuku 启动应用失败", Toast.LENGTH_SHORT).show();
            State.log("使用 Shizuku 启动应用失败: " + e.getMessage());
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
            initWithShizuku();
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

    public static IActivityTaskManager getActivityTaskManager() {
        if (activityTaskManager == null) {
            initWithShizuku();
        }
        return activityTaskManager;
    }
}