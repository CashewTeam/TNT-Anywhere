package com.connect_screen.mirror;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.AutoRotateAndScaleForDisplaylink;
import com.connect_screen.mirror.job.CreateVirtualDisplay;
import com.connect_screen.mirror.job.ExitAll;
import com.connect_screen.mirror.job.StartSunshineService;
import com.connect_screen.mirror.job.TntOverlayHelper;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
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

    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;
    private long lastCheckTime;

    Button settingsBtn;
    Button screenOffBtn;
    Button touchScreenBtn;
    Button exitBtn;
    Button tntDesktopBtn;
    SwitchCompat tntModeCheckbox;
    TextView mirrorStatus;
    TextView streamingDebugPanel;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
        } else {
            State.log("未知权限请求代码: " + requestCode);
        }
    }

    private void onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: "
                    + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
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
        super.onCreate(savedInstanceState);
        State.setCurrentActivity(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        boolean doNotAutoStartMoonlight = getIntent().getBooleanExtra("DoNotAutoStartMoonlight", false);
        if (doNotAutoStartMoonlight) {
            Pref.doNotAutoStartMoonlight = true;
        }

        if (SunshineService.getLifecycleState() == SunshineService.LifecycleState.STOPPED) {
            State.log("SunshineService 未启动，请点击启动服务");
        } else {
            State.log("SunshineService 正在运行");
        }

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);

        State.uiState.observe(this, this::updateUI);
        initHomeControls();
        if (!Pref.isInitialSetupComplete()) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> InitializationGuideDialog.show(this), 400);
        }
    }

    private void ensureAccessibilityServiceStarted() {
        if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            startService(serviceIntent);
        }
    }

    private void initHomeControls() {
        settingsBtn = findViewById(R.id.settingsBtn);
        screenOffBtn = findViewById(R.id.screenOffBtn);
        touchScreenBtn = findViewById(R.id.touchScreenBtn);
        exitBtn = findViewById(R.id.exitBtn);
        tntDesktopBtn = findViewById(R.id.tntDesktopBtn);
        tntModeCheckbox = findViewById(R.id.tntModeCheckbox);
        mirrorStatus = findViewById(R.id.mirrorStatus);
        streamingDebugPanel = findViewById(R.id.streamingDebugPanel);
        styleSwitch(tntModeCheckbox);

        State.streamingDebugInfo.observe(this, info -> {
            if (streamingDebugPanel != null) {
                streamingDebugPanel.setText(info);
            }
        });

        refresh();

        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, MirrorSettingsActivity.class)));

        tntModeCheckbox.setChecked(Pref.getSkipExternalActivity());
        tntModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (Pref.getPreferences() != null) {
                Pref.getPreferences().edit().putBoolean(Pref.KEY_SKIP_EXTERNAL_ACTIVITY, isChecked).apply();
            }
            State.log("TNT模式" + (isChecked ? "已开启" : "已关闭"));
            refresh();
        });

        screenOffBtn.setOnClickListener(v -> CreateVirtualDisplay.doPowerOffScreen(this));

        touchScreenBtn.setOnClickListener(v -> {
            boolean useTouchscreen = Pref.getUseTouchscreen();
            if (ShizukuUtils.hasPermission() && useTouchscreen) {
                VirtualDisplay virtualDisplay = State.displaylinkState.getVirtualDisplay();
                if (virtualDisplay == null) {
                    virtualDisplay = State.mirrorVirtualDisplay;
                }
                if (virtualDisplay == null) {
                    return;
                }
                int displayId = virtualDisplay.getDisplay().getDisplayId();
                Intent intent = new Intent(this, TouchscreenActivity.class);
                intent.putExtra("surface", virtualDisplay.getSurface());
                intent.putExtra("display", displayId);
                startActivity(intent);
            } else {
                TouchpadActivity.startTouchpad(this, State.lastSingleAppDisplay, false);
            }
        });

        exitBtn.setOnClickListener(v -> startSunshineServiceWithPreflight());
        tntDesktopBtn.setOnClickListener(v -> toggleTntDesktop());
    }

    private void startSunshineServiceWithPreflight() {
        SunshineService.LifecycleState lifecycleState = SunshineService.getLifecycleState();
        if (lifecycleState == SunshineService.LifecycleState.STOPPED) {
            State.startNewJob(new StartSunshineService());
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

    private void toggleSunshineService() {
        SunshineService.LifecycleState lifecycleState = SunshineService.getLifecycleState();
        if (lifecycleState == SunshineService.LifecycleState.STOPPED) {
            State.log("手动启动 SunshineService");
            SunshineService.markStarting();
            refresh();
            startMediaProjectionService();
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

    private void toggleTntDesktop() {
        if (TntOverlayHelper.isOverlayOwnedByApp()) {
            TntOverlayHelper.clearOverlayDisplay();
            State.log("已关闭 TNT overlay 调试显示器");
            refresh();
            return;
        }
        if (TntOverlayHelper.ensureHeadlessOverlayDisplayFromPreferences()) {
            State.log("已请求开启 TNT 桌面，请稍候让系统拉起桌面");
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.setCurrentActivity(this);
        ensureAccessibilityServiceStarted();
        if (tntModeCheckbox != null) {
            tntModeCheckbox.setChecked(Pref.getSkipExternalActivity());
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
        State.setCurrentActivity(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                lastCheckTime = System.currentTimeMillis();
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
        try {
            if (logAdapter != null) {
                logAdapter.notifyDataSetChanged();
                logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
            }
        } catch (Exception e) {
            // ignore
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

        MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
        if (mirrorMainActivity != null) {
            mirrorMainActivity.startActivityForResult(captureIntent, REQUEST_CODE_MEDIA_PROJECTION);
        }
    }

    private void updateUI(MirrorUiState state) {
        if (state.errorStatusText != null) {
            mirrorStatus.setText(state.errorStatusText);
            mirrorStatus.setVisibility(View.VISIBLE);
            settingsBtn.setVisibility(View.VISIBLE);
            exitBtn.setVisibility(View.VISIBLE);
            exitBtn.setText(getSunshineServiceButtonText());
            exitBtn.setEnabled(isSunshineServiceButtonEnabled());
            tntDesktopBtn.setVisibility(View.VISIBLE);
            tntDesktopBtn.setText(getTntDesktopButtonText());
            tntModeCheckbox.setVisibility(View.VISIBLE);
            screenOffBtn.setVisibility(View.GONE);
            touchScreenBtn.setVisibility(View.GONE);
            return;
        }

        mirrorStatus.setText(state.mirrorStatusText);
        mirrorStatus.setVisibility(View.VISIBLE);
        settingsBtn.setVisibility(state.settingsBtnVisibility ? View.VISIBLE : View.GONE);
        exitBtn.setVisibility(View.VISIBLE);
        exitBtn.setText(getSunshineServiceButtonText());
        exitBtn.setEnabled(isSunshineServiceButtonEnabled());
        tntDesktopBtn.setVisibility(state.tntDesktopButtonVisibility ? View.VISIBLE : View.GONE);
        tntDesktopBtn.setText(state.tntDesktopButtonText);
        tntModeCheckbox.setVisibility(View.VISIBLE);
        screenOffBtn.setText("熄屏");
        screenOffBtn.setVisibility(state.screenOffBtnVisibility ? View.VISIBLE : View.GONE);
        touchScreenBtn.setVisibility(state.touchScreenBtnVisibility ? View.VISIBLE : View.GONE);
        if (state.touchScreenBtnVisibility && state.touchScreenBtnText != null) {
            touchScreenBtn.setText(state.touchScreenBtnText);
        }
    }

    public void refresh() {
        MirrorUiState currentState = State.uiState.getValue();
        if (currentState != null && currentState.errorStatusText != null) {
            return;
        }

        boolean singleAppMode = Pref.getSingleAppMode();
        boolean useTouchscreen = Pref.getUseTouchscreen();
        boolean isScreenMirroring = State.mirrorVirtualDisplay != null
                || State.displaylinkState.getVirtualDisplay() != null
                || State.lastSingleAppDisplay != 0;

        MirrorUiState newUiState = new MirrorUiState();
        newUiState.settingsBtnVisibility = true;
        newUiState.tntDesktopButtonVisibility = true;
        newUiState.tntDesktopButtonText = getTntDesktopButtonText();

        SunshineService.LifecycleState lifecycleState = SunshineService.getLifecycleState();
        if (lifecycleState == SunshineService.LifecycleState.STOPPED) {
            newUiState.mirrorStatusText = "Sunshine 服务未启动，请点击启动服务";
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        } else if (lifecycleState == SunshineService.LifecycleState.STARTING) {
            newUiState.mirrorStatusText = "Sunshine 服务正在启动，请稍候";
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        } else if (lifecycleState == SunshineService.LifecycleState.STOPPING) {
            newUiState.mirrorStatusText = "Sunshine 服务正在停止，请稍候";
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        } else if (isScreenMirroring) {
            newUiState.mirrorStatusText = "Sunshine Host 运行中。建议在系统设置中为 TNT Shaker 关闭省电限制，并在任务列表中锁定任务防止被杀。控制链路推荐使用 Shizuku。";
            newUiState.screenOffBtnVisibility = ShizukuUtils.hasPermission();
            newUiState.touchScreenBtnVisibility = singleAppMode;
            if (singleAppMode) {
                newUiState.touchScreenBtnText = useTouchscreen ? "触摸屏" : "触控板";
            }
        } else {
            StringBuilder status = new StringBuilder(
                    "请连接屏幕。如果手机接口是 USB 2.0，可搭配 DisplayLink 扩展坞，或使用 Moonlight 无线串流。");
            try {
                for (String ip : SunshineService.getAllWifiIpAddresses(this)) {
                    status.append("\n").append(ip);
                }
            } catch (Throwable e) {
                // ignore
            }
            newUiState.mirrorStatusText = status.toString();
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        }

        State.uiState.setValue(newUiState);
    }

    private String getTntDesktopButtonText() {
        return TntOverlayHelper.isOverlayOwnedByApp() ? "关闭 TNT" : "开启 TNT";
    }

    private String getSunshineServiceButtonText() {
        switch (SunshineService.getLifecycleState()) {
            case STARTING:
                return "启动中...";
            case RUNNING:
                return "停止服务";
            case STOPPING:
                return "停止中...";
            case STOPPED:
            default:
                return "启动服务";
        }
    }

    private boolean isSunshineServiceButtonEnabled() {
        SunshineService.LifecycleState state = SunshineService.getLifecycleState();
        return state == SunshineService.LifecycleState.STOPPED
                || state == SunshineService.LifecycleState.RUNNING;
    }

    private void styleSwitch(SwitchCompat switchCompat) {
        if (switchCompat == null) {
            return;
        }
        int textColor = 0xFF202020;
        int[][] states = new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        ColorStateList thumbColors = new ColorStateList(states, new int[]{
                0xFFD0D4D8,
                0xFF4CAF50,
                0xFF9EA4AA
        });
        ColorStateList trackColors = new ColorStateList(states, new int[]{
                0x223F454A,
                0x664CAF50,
                0x553F454A
        });
        switchCompat.setTextColor(textColor);
        switchCompat.setThumbTintList(thumbColors);
        switchCompat.setTrackTintList(trackColors);
        CompoundButtonCompat.setButtonTintList(switchCompat, null);
    }
}
