package com.easycast.source;

import android.os.Build;
import android.os.Process;

import java.lang.reflect.Method;

public final class SmartisanPerformanceHelper {
    private static final String LOG_PREFIX = "[SmartisanCpuset]";
    private static final int CPUSET_LEVEL_NONE = 0;
    private static final int CPUSET_LEVEL_STREAMING = 1;
    private static final long STREAMING_TIMEOUT_MS = 15 * 60 * 1000L;

    private static Method getSmtExMethod;
    private static Method setProcessRunningCpusetMethod;
    private static boolean reflectionInitialized;
    private static boolean reflectionAvailable;
    private static volatile int lastAppliedCpusetLevel = CPUSET_LEVEL_NONE;
    private static volatile long lastAppliedTimeoutMs;
    private static volatile boolean lastApplySucceeded;
    private static volatile String lastReason = "idle";

    private SmartisanPerformanceHelper() {
    }

    public static void updateStreamingBoost(boolean enabled, String reason) {
        int cpusetLevel = enabled && Pref.getSmartisanCpusetBoost()
                ? CPUSET_LEVEL_STREAMING
                : CPUSET_LEVEL_NONE;
        long timeoutMs = cpusetLevel == CPUSET_LEVEL_NONE ? 0L : STREAMING_TIMEOUT_MS;
        applyProcessRunningCpuset(cpusetLevel, timeoutMs, reason);
    }

    public static String getDebugStatusLine() {
        StringBuilder builder = new StringBuilder("Smartisan boost: ");
        if (!Pref.getSmartisanCpusetBoost()) {
            builder.append("off");
            return builder.toString();
        }
        if (!reflectionInitialized) {
            builder.append("pending");
            return builder.toString();
        }
        if (!reflectionAvailable) {
            builder.append("unsupported");
            return builder.toString();
        }
        builder.append(lastApplySucceeded ? "active" : "idle");
        builder.append(" level=").append(lastAppliedCpusetLevel);
        if (lastAppliedTimeoutMs > 0) {
            builder.append(" timeout=").append(lastAppliedTimeoutMs / 1000).append("s");
        }
        if (lastReason != null && !lastReason.isEmpty()) {
            builder.append(" reason=").append(lastReason);
        }
        return builder.toString();
    }

    private static void applyProcessRunningCpuset(int cpusetLevel, long timeoutMs, String reason) {
        lastReason = reason;
        try {
            if (!ensureReflection()) {
                lastApplySucceeded = false;
                lastAppliedCpusetLevel = CPUSET_LEVEL_NONE;
                lastAppliedTimeoutMs = 0L;
                return;
            }
            Object smtEx = getSmtExMethod.invoke(null);
            if (smtEx == null) {
                lastApplySucceeded = false;
                lastAppliedCpusetLevel = CPUSET_LEVEL_NONE;
                lastAppliedTimeoutMs = 0L;
                State.log(LOG_PREFIX + " ActivityManager.getSmtEx() returned null");
                return;
            }
            setProcessRunningCpusetMethod.invoke(
                    smtEx,
                    Process.myPid(),
                    cpusetLevel,
                    timeoutMs,
                    false);
            lastAppliedCpusetLevel = cpusetLevel;
            lastAppliedTimeoutMs = timeoutMs;
            lastApplySucceeded = cpusetLevel != CPUSET_LEVEL_NONE;
            State.log(LOG_PREFIX + " setProcessRunningCpuset level=" + cpusetLevel
                    + " timeoutMs=" + timeoutMs
                    + " manufacturer=" + Build.MANUFACTURER
                    + " reason=" + reason);
        } catch (Throwable e) {
            lastApplySucceeded = false;
            lastAppliedCpusetLevel = CPUSET_LEVEL_NONE;
            lastAppliedTimeoutMs = 0L;
            State.log(LOG_PREFIX + " apply failed: " + e.getClass().getSimpleName()
                    + " " + e.getMessage());
        }
    }

    private static boolean ensureReflection() {
        if (reflectionInitialized) {
            return reflectionAvailable;
        }
        reflectionInitialized = true;
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            getSmtExMethod = activityManagerClass.getMethod("getSmtEx");
            Class<?> smtExClass = Class.forName("android.app.ActivityManagerSmtEx");
            setProcessRunningCpusetMethod = smtExClass.getMethod(
                    "setProcessRunningCpuset",
                    int.class,
                    int.class,
                    long.class,
                    boolean.class);
            reflectionAvailable = true;
            State.log(LOG_PREFIX + " reflection ready");
        } catch (Throwable e) {
            reflectionAvailable = false;
            State.log(LOG_PREFIX + " Smartisan ActivityManager extension unavailable: "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        return reflectionAvailable;
    }
}
