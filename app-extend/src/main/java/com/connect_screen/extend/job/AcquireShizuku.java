package com.connect_screen.extend.job;

import com.connect_screen.extend.State;
import com.connect_screen.extend.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class AcquireShizuku implements Job {
    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    private boolean hasRequestedPermission;
    public boolean acquired = false;

    @Override
    public void start() throws YieldException {
        if (!ShizukuUtils.hasShizukuStarted()) {
            return;
        }
        if (ShizukuUtils.hasPermission()) {
            State.log("已经获得 Shizuku 权限");
            acquired = true;
        } else {
            if (hasRequestedPermission) {
                State.log("获取 Shizuku 权限失败");
                return;
            }
            hasRequestedPermission = true;
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            throw new YieldException("等待 Shizuku 权限");
        }
    }
}
