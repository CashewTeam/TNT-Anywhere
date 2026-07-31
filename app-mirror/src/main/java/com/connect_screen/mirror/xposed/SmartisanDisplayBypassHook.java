package com.connect_screen.mirror.xposed;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed hook that bypasses SmartisanOS virtual display package whitelist.
 *
 * On Android 8.1 (API 27), SmartisanOS caches persist.sys.virtual_display_pkg
 * into a static field at boot. Only one package name can be whitelisted, so
 * switching between apps (e.g. LeBo and TNT Anywhere) requires a reboot.
 *
 * This hook intercepts SmtPCUtils.isValidExtDisplayType() and returns true
 * for type==5 (VIRTUAL) displays from any whitelisted package, eliminating
 * the need to change the property or reboot.
 */
public class SmartisanDisplayBypassHook implements IXposedHookLoadPackage {

    private static final String TAG = "TNT-Anywhere-Xposed";

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

