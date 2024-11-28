package com.gitee.connect_screen.job;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.gitee.connect_screen.LauncherActivity;
import com.gitee.connect_screen.State;

import rikka.shizuku.Shizuku;

public class AcquireShizukuAndStartLauncher implements Job {

    private boolean hasRequestedPermission = false;
    
    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    private final int displayId;

    public AcquireShizukuAndStartLauncher(int displayId) {
        this.displayId = displayId;
    }

    public void start() throws YieldException {
        if (State.virtualDisplayIds.contains(displayId)) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                State.log("已经获得 Shizuku 权限");
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

        Context context = State.currentActivity.get();
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LauncherActivity.EXTRA_TARGET_DISPLAY_ID, displayId);
        context.startActivity(intent);
    }
}
