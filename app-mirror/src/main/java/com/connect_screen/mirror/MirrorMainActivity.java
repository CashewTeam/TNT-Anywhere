package com.connect_screen.mirror;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.viewpager2.widget.ViewPager2;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.AutoRotateAndScaleForDisplaylink;
import com.connect_screen.mirror.job.ExitAll;
import com.connect_screen.mirror.job.StartSunshineService;
import com.connect_screen.mirror.job.SunshineServer;
import com.connect_screen.mirror.job.TntDebugVirtualDisplayHelper;
import com.connect_screen.mirror.job.TntOverlayHelper;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.topjohnwu.superuser.Shell;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import rikka.shizuku.Shizuku;

public class MirrorMainActivity extends AppCompatActivity implements IMainActivity {

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }

    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    public static final String TAG = "MirrorMainActivity";

    private ViewPager2 mainPager;
    private BottomNavigationView bottomNavigation;
    private HomePageFragment homePageFragment;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
            if (InitializationGuideDialog.needsSetup(this)) {
                InitializationGuideDialog.show(this);
            }
        } else {
            State.log("未知权限请求代码: " + requestCode);
        }
    }

    private void onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: "
                    + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
            if (InitializationGuideDialog.needsSetup(this)) {
                InitializationGuideDialog.show(this);
            }
        } else {
            State.log("未知 Shizuku 请求代码: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener =
            this::onRequestShizukuPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i(TAG, "已启用 HiddenApiBypass");
            } catch (Exception e) {
                android.util.Log.e(TAG, "HiddenApiBypass 初始化失败: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean darkMode = getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(Pref.KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(darkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        State.setCurrentActivity(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        boolean doNotAutoStartMoonlight = getIntent().getBooleanExtra("DoNotAutoStartMoonlight", false);
        if (doNotAutoStartMoonlight) {
            Pref.doNotAutoStartMoonlight = true;
        }

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);
        setContentView(R.layout.activity_main);

        TextView versionTitle = findViewById(R.id.versionTitle);
        versionTitle.setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        versionTitle.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));

        mainPager = findViewById(R.id.mainPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        mainPager.setAdapter(new MainPagerAdapter(this));
        mainPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int itemId = position == 1 ? R.id.nav_tnt : position == 2 ? R.id.nav_streaming : R.id.nav_home;
                if (bottomNavigation.getSelectedItemId() != itemId) {
                    bottomNavigation.setSelectedItemId(itemId);
                }
                captureHomeFragment();
            }
        });
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_tnt) {
                mainPager.setCurrentItem(1, true);
                return true;
            }
            if (item.getItemId() == R.id.nav_streaming) {
                mainPager.setCurrentItem(2, true);
                return true;
            }
            mainPager.setCurrentItem(0, true);
            return true;
        });

        State.uiState.observe(this, this::updateUI);
        State.streamingDebugInfo.observe(this, info -> {
            captureHomeFragment();
            if (homePageFragment != null) {
                homePageFragment.updateDebugInfo(info);
            }
        });

        State.log(SunshineService.getLifecycleState() == SunshineService.LifecycleState.STOPPED
                ? "SunshineService 未启动，请点击启动服务"
                : "SunshineService 正在运行");
        refresh();
        if (InitializationGuideDialog.needsSetup(this)) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> InitializationGuideDialog.show(this), 400);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.setCurrentActivity(this);
        ensureAccessibilityServiceStarted();
        forceRefreshUi();
        if (InitializationGuideDialog.needsSetup(this)) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> InitializationGuideDialog.show(this), 200);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
        State.clearCurrentActivity(this);
    }

    private void ensureAccessibilityServiceStarted() {
        if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            startService(serviceIntent);
        }
    }

    public void startSunshineServiceWithPreflight() {
        State.setCurrentActivity(this);
        SunshineService.LifecycleState lifecycleState = SunshineService.getLifecycleState();
        if (lifecycleState == SunshineService.LifecycleState.STOPPED) {
            State.startNewJob(new StartSunshineService());
            refresh();
            return;
        }
        if (lifecycleState == SunshineService.LifecycleState.STARTING
                || lifecycleState == SunshineService.LifecycleState.STOPPING) {
            State.log("SunshineService 正在切换状态，请稍候");
            refresh();
            return;
        }
        State.log("手动停止 SunshineService");
        SunshineService.markStopping();
        refresh();
        if (AutoRotateAndScaleForDisplaylink.instance != null) {
            AutoRotateAndScaleForDisplaylink.instance.release();
        }
        ExitAll.stopServices(this);
        refresh();
    }

    public void toggleTntDesktop() {
        if (Pref.getUseTntOverlayBackend()) {
            if (TntOverlayHelper.isOverlayOwnedByApp()) {
                TntOverlayHelper.clearOverlayDisplay();
                State.log("已关闭 TNT overlay 调试显示");
            } else if (TntOverlayHelper.ensureHeadlessOverlayDisplayFromPreferences()) {
                State.log("已启动 TNT overlay 调试显示");
            }
            refresh();
            return;
        }
        if (TntDebugVirtualDisplayHelper.isActive()) {
            TntDebugVirtualDisplayHelper.clearVirtualDisplay();
            State.log("已关闭 TNT 原生虚拟显示");
            refresh();
            return;
        }
        if (TntDebugVirtualDisplayHelper.ensureVirtualDisplayFromPreferences()) {
            State.log("已创建 TNT 原生虚拟显示，请稍候等待系统激活 TNT");
            refresh();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                if (SunshineService.instance == null) {
                    Intent sunshineServiceIntent = new Intent(this, SunshineService.class);
                    sunshineServiceIntent.putExtra("data", data);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(sunshineServiceIntent);
                    } else {
                        startService(sunshineServiceIntent);
                    }
                    State.log("启动 SunshineService 服务");
                    refresh();
                } else {
                    MediaProjectionManager mediaProjectionManager =
                            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop 回调");
                        }
                    }, null);
                    State.resumeJob();
                }
            } else {
                State.log("用户拒绝了投屏权限");
                SunshineService.markStopped();
                refresh();
                State.resumeJob();
            }
        }
    }

    @Override
    public void updateLogs() {
        captureHomeFragment();
        if (homePageFragment != null) {
            homePageFragment.updateLogs();
        }
    }

    public void startMediaProjectionService() {
        if (SunshineService.getLifecycleState() == SunshineService.LifecycleState.STOPPING) {
            State.log("SunshineService 正在停止，请稍候再启动");
            refresh();
            return;
        }
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager == null) {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }

        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            captureIntent = mediaProjectionManager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        }
        startActivityForResult(captureIntent, REQUEST_CODE_MEDIA_PROJECTION);
    }

    public void refresh() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread(this::refresh);
            return;
        }

        MirrorUiState newUiState = new MirrorUiState();
        newUiState.tntDesktopButtonVisibility = true;
        newUiState.tntDesktopButtonText = getTntDesktopButtonText();
        newUiState.screenOffBtnText = "息屏";

        SunshineService.LifecycleState lifecycleState = SunshineService.getLifecycleState();
        boolean connected = SunshineServer.isMoonlightSessionActive()
                || State.mirrorVirtualDisplay != null
                || State.displaylinkState.getVirtualDisplay() != null
                || State.lastSingleAppDisplay != 0;

        if (lifecycleState == SunshineService.LifecycleState.STOPPED) {
            newUiState.mirrorStatusText = "Sunshine 服务未启动";
            newUiState.screenOffBtnVisibility = false;
            newUiState.screenOffBtnEnabled = false;
        } else if (lifecycleState == SunshineService.LifecycleState.STARTING) {
            newUiState.mirrorStatusText = "Sunshine 服务启动中";
            newUiState.screenOffBtnVisibility = false;
            newUiState.screenOffBtnEnabled = false;
        } else if (lifecycleState == SunshineService.LifecycleState.STOPPING) {
            newUiState.mirrorStatusText = "Sunshine 服务关闭中";
            newUiState.screenOffBtnVisibility = false;
            newUiState.screenOffBtnEnabled = false;
        } else if (connected) {
            newUiState.mirrorStatusText = "已连接到客户端";
            newUiState.screenOffBtnVisibility = true;
            newUiState.screenOffBtnEnabled = ShizukuUtils.hasPermission();
        } else {
            newUiState.mirrorStatusText = "Sunshine 服务已启动，等待连接中";
            newUiState.screenOffBtnVisibility = false;
            newUiState.screenOffBtnEnabled = false;
        }
        State.uiState.setValue(newUiState);
    }

    public void forceRefreshUi() {
        refresh();
        captureHomeFragment();
        if (homePageFragment != null) {
            homePageFragment.updateUiState(State.uiState.getValue());
            homePageFragment.updateDebugInfo(State.streamingDebugInfo.getValue());
        }
    }

    private void updateUI(MirrorUiState state) {
        captureHomeFragment();
        if (homePageFragment != null) {
            homePageFragment.updateUiState(state);
        }
    }

    private String getTntDesktopButtonText() {
        if (Pref.getUseTntOverlayBackend()) {
            return TntOverlayHelper.isOverlayOwnedByApp() ? "关闭 TNT" : "开启 TNT";
        }
        return TntDebugVirtualDisplayHelper.isActive() ? "关闭 TNT" : "开启 TNT";
    }

    private void captureHomeFragment() {
        androidx.fragment.app.Fragment fragment =
                getSupportFragmentManager().findFragmentByTag("f" + 0);
        if (fragment instanceof HomePageFragment) {
            homePageFragment = (HomePageFragment) fragment;
        }
    }
}
