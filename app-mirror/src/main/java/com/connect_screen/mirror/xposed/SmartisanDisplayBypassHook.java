package com.connect_screen.mirror.xposed;

import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 仅用于坚果 R1 / 坚果 Pro2S 的 SmartisanOS 8.1 虚拟显示白名单绕过模块。
 *
 * Android 8.1（API 27）的 SmartisanOS 会在开机时缓存
 * persist.sys.virtual_display_pkg，只允许一个包名通过虚拟显示校验。
 * 本 hook 仅在 Android 8.1 的 android 进程中拦截该校验，允许 TNT Anywhere
 * 和无线投屏包创建虚拟显示。其他 Android 版本不会启用此 hook。
 */
public class SmartisanDisplayBypassHook implements IXposedHookLoadPackage {

    private static final String TAG = "TNT-Anywhere-Xposed";
    private static final int TARGET_ANDROID_API = Build.VERSION_CODES.O_MR1;

    private static final String CLASS_SMT_PC_UTILS = "android.app.SmtPCUtils";

    /**
     * Packages allowed to create type==5 (VIRTUAL) displays that pass
     * Smartisan's external display validation.
     */
    private static final Set<String> WHITELIST = new HashSet<>(Arrays.asList(
            "com.smartisanos.tntanywhere",
            "com.bytedance.wirelesscast"
    ));

    private static final int DISPLAY_TYPE_VIRTUAL = 5;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT != TARGET_ANDROID_API
                || !"android".equals(lpparam.packageName)) {
            return;
        }
        hookIsValidExtDisplayType(lpparam);
    }

    private void hookIsValidExtDisplayType(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    CLASS_SMT_PC_UTILS,
                    lpparam.classLoader,
                    "isValidExtDisplayType",
                    int.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int type = (int) param.args[0];
                            String pkgName = (String) param.args[1];

                            if (type == DISPLAY_TYPE_VIRTUAL && pkgName != null
                                    && WHITELIST.contains(pkgName)) {
                                param.setResult(true);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + ": hooked " + CLASS_SMT_PC_UTILS + ".isValidExtDisplayType");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook failed - " + t.getMessage());
        }
    }
}
