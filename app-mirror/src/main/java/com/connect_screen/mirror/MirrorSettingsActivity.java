package com.connect_screen.mirror;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.ConnectToClient;
import com.connect_screen.mirror.job.SunshineServer;
import com.connect_screen.mirror.job.TntOverlayHelper;
import com.connect_screen.mirror.shizuku.PermissionManager;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MirrorSettingsActivity extends AppCompatActivity {
    public static final String PREF_NAME = "mirror_settings";
    private static final String MANUAL_INPUT_LABEL = "Manual input";
    private static final int[] ENCODER_CODEC_VALUES = new int[]{Pref.ENCODER_CODEC_H264, Pref.ENCODER_CODEC_H265};
    private static final int[] ENCODER_BITRATE_MODE_VALUES = new int[]{2, 1, 0};
    private static final TntOverlayPreset[] TNT_OVERLAY_PRESETS = new TntOverlayPreset[]{
            new TntOverlayPreset("Default 1080P (1920 x 1080 / 216dpi)", 1920, 1080, 216, false),
            new TntOverlayPreset("4K (3840 x 2160 / 320dpi)", 3840, 2160, 320, false),
            new TntOverlayPreset("iPad mini 6 (2266 x 1488 / 320dpi)", 2266, 1488, 320, false),
            new TntOverlayPreset("iPad Pro 11 (2420 x 1668 / 320dpi)", 2420, 1668, 320, false),
            new TntOverlayPreset("Xiaomi Pad 6 Pro (2880 x 1800 / 320dpi)", 2880, 1800, 320, false),
            new TntOverlayPreset("Custom", 0, 0, 0, true)
    };

    private static final class TntOverlayPreset {
        final String label;
        final int width;
        final int height;
        final int dpi;
        final boolean custom;

        TntOverlayPreset(String label, int width, int height, int dpi, boolean custom) {
            this.label = label;
            this.width = width;
            this.height = height;
            this.dpi = dpi;
            this.custom = custom;
        }

        boolean matches(int targetWidth, int targetHeight, int targetDpi) {
            return width == targetWidth && height == targetHeight && dpi == targetDpi;
        }
    }

    private SharedPreferences preferences;
    private TextView currentTntOverlaySettingsText;
    private TextView backupTntRootStatus;
    private TextView backupTntDebugStatus;
    private Button checkBackupTntRootButton;
    private Button backupTntOverlayButton;
    private SwitchCompat backupTntDebugSwitch;
    private EditText encoderBitratePercentEditText;
    private EditText encoderComplexityEditText;
    private EditText encoderIFrameIntervalEditText;
    private EditText encoderMaxFpsEditText;
    private EditText streamFecPercentEditText;
    private Spinner encoderCodecSpinner;
    private Spinner encoderBitrateModeSpinner;
    private SwitchCompat encoderLowLatencyCheckbox;
    private SwitchCompat encoderDisableBFramesCheckbox;
    private SwitchCompat encoderRealtimePriorityCheckbox;
    private SwitchCompat encoderDynamicFrameRateCheckbox;
    private TextView currentEncoderSettingsText;
    private boolean backupRootGranted;
    private boolean backupRootChecked;
    private boolean backupRootChecking;
    private boolean suppressBackupTntDebugSwitchCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror_settings);
        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings take effect next time the feature starts");
        }

        SwitchCompat autoRotateCheckbox = findViewById(R.id.autoRotateCheckbox);
        SwitchCompat autoScaleCheckbox = findViewById(R.id.autoScaleCheckbox);
        SwitchCompat autoHideFloatingCheckbox = findViewById(R.id.autoHideFloatingCheckbox);
        SwitchCompat autoScreenOffCheckbox = findViewById(R.id.autoScreenOffCheckbox);
        SwitchCompat disableUsbAudioCheckbox = findViewById(R.id.disableUsbAudioCheckbox);
        SwitchCompat autoMatchAspectRatioCheckbox = findViewById(R.id.autoMatchAspectRatioCheckbox);
        SwitchCompat showFloatingInMirrorModeCheckbox = findViewById(R.id.showFloatingInMirrorModeCheckbox);
        SwitchCompat autoConnectClientCheckbox = findViewById(R.id.autoConnectClientCheckbox);
        SwitchCompat useBlackImageCheckbox = findViewById(R.id.useBlackImageCheckbox);
        SwitchCompat preventAutoLockCheckbox = findViewById(R.id.preventAutoLockCheckbox);
        SwitchCompat disableRemoteSubmixCheckbox = findViewById(R.id.disableRemoteSubmixCheckbox);
        SwitchCompat smartisanCpusetBoostCheckbox = findViewById(R.id.smartisanCpusetBoostCheckbox);
        SwitchCompat useAndroidCursorOverlayCheckbox = findViewById(R.id.useAndroidCursorOverlayCheckbox);
        SwitchCompat mapMouseToTouchCheckbox = findViewById(R.id.mapMouseToTouchCheckbox);
        SwitchCompat disableAccessibilityCheckbox = findViewById(R.id.disableAccessibilityCheckbox);
        SwitchCompat adaptTntResolutionToClientCheckbox = findViewById(R.id.adaptTntResolutionToClientCheckbox);
        SwitchCompat autoCloseTntOnClientDisconnectCheckbox = findViewById(R.id.autoCloseTntOnClientDisconnectCheckbox);

        styleSwitch(autoRotateCheckbox);
        styleSwitch(autoScaleCheckbox);
        styleSwitch(autoHideFloatingCheckbox);
        styleSwitch(autoScreenOffCheckbox);
        styleSwitch(disableUsbAudioCheckbox);
        styleSwitch(autoMatchAspectRatioCheckbox);
        styleSwitch(showFloatingInMirrorModeCheckbox);
        styleSwitch(autoConnectClientCheckbox);
        styleSwitch(useBlackImageCheckbox);
        styleSwitch(preventAutoLockCheckbox);
        styleSwitch(disableRemoteSubmixCheckbox);
        styleSwitch(smartisanCpusetBoostCheckbox);
        styleSwitch(useAndroidCursorOverlayCheckbox);
        styleSwitch(mapMouseToTouchCheckbox);
        styleSwitch(disableAccessibilityCheckbox);
        styleSwitch(adaptTntResolutionToClientCheckbox);
        styleSwitch(autoCloseTntOnClientDisconnectCheckbox);

        LinearLayout clientConnectionContainer = findViewById(R.id.clientConnectionContainer);
        Spinner clientSpinner = findViewById(R.id.clientSpinner);
        Button connectClientButton = findViewById(R.id.connectClientButton);
        TextView shizukuStatus = findViewById(R.id.shizukuStatus);
        TextView accessibilityStatus = findViewById(R.id.accessibilityStatus);
        TextView overlayStatus = findViewById(R.id.overlayStatus);
        Button shizukuPermissionBtn = findViewById(R.id.shizukuPermissionBtn);
        currentTntOverlaySettingsText = findViewById(R.id.currentTntOverlaySettingsText);
        backupTntRootStatus = findViewById(R.id.backupTntRootStatus);
        backupTntDebugStatus = findViewById(R.id.backupTntDebugStatus);
        checkBackupTntRootButton = findViewById(R.id.checkBackupTntRootButton);
        backupTntOverlayButton = findViewById(R.id.backupTntOverlayButton);
        backupTntDebugSwitch = findViewById(R.id.backupTntDebugSwitch);

        autoRotateCheckbox.setChecked(Pref.getAutoRotate());
        autoScaleCheckbox.setChecked(Pref.getAutoScale());
        autoHideFloatingCheckbox.setChecked(Pref.getAutoHideFloatingBackButton());
        autoScreenOffCheckbox.setChecked(Pref.getAutoScreenOff());
        disableUsbAudioCheckbox.setChecked(Pref.getDisableUsbAudio());
        autoMatchAspectRatioCheckbox.setChecked(Pref.getAutoMatchAspectRatio());
        showFloatingInMirrorModeCheckbox.setChecked(Pref.getShowFloatingInMirrorMode());
        autoConnectClientCheckbox.setChecked(Pref.getAutoConnectClient());
        useBlackImageCheckbox.setChecked(Pref.getUseBlackImage());
        preventAutoLockCheckbox.setChecked(Pref.getPreventAutoLock());
        disableRemoteSubmixCheckbox.setChecked(Pref.getDisableRemoteSubmix());
        smartisanCpusetBoostCheckbox.setChecked(Pref.getSmartisanCpusetBoost());
        useAndroidCursorOverlayCheckbox.setChecked(Pref.getUseAndroidCursorOverlay());
        mapMouseToTouchCheckbox.setChecked(Pref.getMapMouseToTouch());
        disableAccessibilityCheckbox.setChecked(!Pref.getDisableAccessibility());
        adaptTntResolutionToClientCheckbox.setChecked(Pref.getAdaptTntResolutionToClient());
        autoCloseTntOnClientDisconnectCheckbox.setChecked(Pref.getAutoCloseTntOnClientDisconnect());
        disableAccessibilityCheckbox.setText("Accessibility compatibility mode");

        if (ShizukuUtils.hasPermission()) {
            autoScreenOffCheckbox.setText("Auto screen off");
        }

        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);
        updateAccessibilityStatus(accessibilityStatus);
        updateOverlayStatus(overlayStatus);
        updateTntOverlaySettingsText();
        styleSwitch(backupTntDebugSwitch);
        updateBackupTntSection();

        shizukuPermissionBtn.setOnClickListener(v -> State.startNewJob(new AcquireShizuku()));

        Button initializationGuideButton = findViewById(R.id.initializationGuideButton);
        initializationGuideButton.setOnClickListener(v -> InitializationGuideDialog.show(this));

        Button aboutButton = findViewById(R.id.aboutButton);
        aboutButton.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));

        Button screenSettingsButton = findViewById(R.id.screenSettingsButton);
        screenSettingsButton.setOnClickListener(v -> startActivity(new Intent(this, ScreenSettingsActivity.class)));

        Button viewRecentHandshakeButton = findViewById(R.id.viewRecentHandshakeButton);
        viewRecentHandshakeButton.setOnClickListener(v -> showLastMoonlightHandshakeDialog());

        Button viewRecentControlInputButton = findViewById(R.id.viewRecentControlInputButton);
        viewRecentControlInputButton.setOnClickListener(v -> showLastMoonlightControlInputDialog());

        Button editTntOverlaySettingsButton = findViewById(R.id.editTntOverlaySettingsButton);
        editTntOverlaySettingsButton.setOnClickListener(v -> showTntOverlaySettingsDialog());
        checkBackupTntRootButton.setOnClickListener(v -> runBackupTntRootCheck());
        backupTntOverlayButton.setOnClickListener(v -> toggleBackupTntOverlay());
        backupTntDebugSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressBackupTntDebugSwitchCallback) {
                return;
            }
            setBackupTntDebugEnabled(isChecked);
        });

        autoRotateCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_AUTO_ROTATE, isChecked).apply());
        autoScaleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_AUTO_SCALE, isChecked).apply());
        autoHideFloatingCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_AUTO_HIDE_FLOATING_BACK_BUTTON, isChecked).apply());
        autoScreenOffCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_AUTO_SCREEN_OFF, isChecked).apply());
        autoMatchAspectRatioCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_AUTO_MATCH_ASPECT_RATIO, isChecked).apply());
        showFloatingInMirrorModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_SHOW_FLOATING_IN_MIRROR_MODE, isChecked).apply());
        useBlackImageCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_USE_BLACK_IMAGE, isChecked).apply());
        preventAutoLockCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_PREVENT_AUTO_LOCK, isChecked).apply());
        disableRemoteSubmixCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_DISABLE_REMOTE_SUBMIX, isChecked).apply());
        smartisanCpusetBoostCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_SMARTISAN_CPUSET_BOOST, isChecked).apply();
            if (SunshineService.getLifecycleState() == SunshineService.LifecycleState.RUNNING) {
                SmartisanPerformanceHelper.updateStreamingBoost(
                        isChecked,
                        "settings toggle while Sunshine is running");
            }
        });
        useAndroidCursorOverlayCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_USE_ANDROID_CURSOR_OVERLAY, isChecked).apply());
        mapMouseToTouchCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_MAP_MOUSE_TO_TOUCH, isChecked).apply());
        adaptTntResolutionToClientCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_ADAPT_TNT_RESOLUTION_TO_CLIENT, isChecked).apply());
        autoCloseTntOnClientDisconnectCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_AUTO_CLOSE_TNT_ON_CLIENT_DISCONNECT, isChecked).apply());

        disableUsbAudioCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_USB_AUDIO, isChecked).apply();
            if (ShizukuUtils.hasPermission() && PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
                try {
                    Settings.Secure.putInt(
                            getContentResolver(),
                            "usb_audio_automatic_routing_disabled",
                            isChecked ? 1 : 0);
                } catch (SecurityException e) {
                    State.log("failed to set usb_audio_automatic_routing_disabled: " + e);
                }
            }
        });

        disableAccessibilityCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_ACCESSIBILITY, !isChecked).apply();
            updateAccessibilityStatus(accessibilityStatus);
            if (!isChecked && ShizukuUtils.hasPermission()) {
                TouchpadAccessibilityService.disableAll(this);
            }
        });

        boolean autoConnectClient = Pref.getAutoConnectClient();
        clientConnectionContainer.setVisibility(autoConnectClient ? View.VISIBLE : View.GONE);
        autoConnectClientCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_CONNECT_CLIENT, isChecked).apply();
            clientConnectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                loadClientList(clientSpinner);
            }
        });
        if (autoConnectClient) {
            loadClientList(clientSpinner);
        }

        connectClientButton.setOnClickListener(v -> {
            String selectedClient = (String) clientSpinner.getSelectedItem();
            if (selectedClient == null || selectedClient.isEmpty()) {
                return;
            }
            if (MANUAL_INPUT_LABEL.equals(selectedClient)) {
                showManualInputDialog();
                return;
            }
            preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, selectedClient).apply();
            int pin = (int) (Math.random() * 9000) + 1000;
            SunshineServer.suppressPin = String.valueOf(pin);
            ConnectToClient.connect(pin);
        });

        if (!ShizukuUtils.hasPermission()) {
            disableUsbAudioCheckbox.setEnabled(false);
            autoMatchAspectRatioCheckbox.setEnabled(false);
            preventAutoLockCheckbox.setEnabled(false);
        }

        setupEncoderSettings();
    }

    private void showLastMoonlightHandshakeDialog() {
        String handshakeInfo = State.lastMoonlightHandshakeInfo;
        if (handshakeInfo == null || handshakeInfo.trim().isEmpty()) {
            handshakeInfo = "尚无最近一次 Moonlight 连接握手信息";
        }
        showReadonlyDebugDialog("最近握手信息", handshakeInfo, "Moonlight 握手信息", "已复制握手信息");
    }

    private void showLastMoonlightControlInputDialog() {
        String controlInputInfo = State.lastMoonlightControlInputInfo;
        if (controlInputInfo == null || controlInputInfo.trim().isEmpty()) {
            controlInputInfo = "尚无最近一次 Moonlight 控制输入统计";
        }
        showReadonlyDebugDialog("最近控制输入统计", controlInputInfo, "Moonlight 控制输入统计", "已复制控制输入统计");
    }

    private void showReadonlyDebugDialog(String title,
                                         String content,
                                         String clipLabel,
                                         String copiedToastText) {
        final String textToCopy = content;
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制", (dialog, which) -> {
                    ClipboardManager clipboardManager =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(
                                ClipData.newPlainText(clipLabel, textToCopy));
                        Toast.makeText(this, copiedToastText, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateShizukuStatus(findViewById(R.id.shizukuStatus), findViewById(R.id.shizukuPermissionBtn));
        updateAccessibilityStatus(findViewById(R.id.accessibilityStatus));
        updateOverlayStatus(findViewById(R.id.overlayStatus));
        updateTntOverlaySettingsText();
        updateBackupTntSection();

        SwitchCompat disableAccessibilityCheckbox = findViewById(R.id.disableAccessibilityCheckbox);
        disableAccessibilityCheckbox.setChecked(!Pref.getDisableAccessibility());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SunshineServer.suppressPin = null;
    }

    private void updateShizukuStatus(TextView statusView, Button permissionBtn) {
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();
        if (!started) {
            statusView.setText("Not started");
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            statusView.setText("Started, permission required");
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            statusView.setText("Authorized");
            permissionBtn.setVisibility(View.GONE);
        }
    }

    private void updateAccessibilityStatus(TextView statusView) {
        boolean enabled = TouchpadAccessibilityService.isAccessibilityServiceEnabled(this);
        boolean disabled = Pref.getDisableAccessibility();
        if (disabled) {
            statusView.setText("Disabled");
        } else {
            statusView.setText(enabled ? "Authorized" : "Permission required");
        }

        View parent = (View) statusView.getParent();
        Button accessibilityPermissionBtn = parent.findViewById(R.id.accessibilityPermissionBtn);
        if (accessibilityPermissionBtn != null) {
            accessibilityPermissionBtn.setText("Grant");
            accessibilityPermissionBtn.setVisibility((enabled || disabled) ? View.GONE : View.VISIBLE);
            accessibilityPermissionBtn.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
    }

    private void updateOverlayStatus(TextView statusView) {
        boolean hasPermission = Settings.canDrawOverlays(this);
        statusView.setText(hasPermission ? "Authorized" : "Permission required");

        View parent = (View) statusView.getParent();
        Button overlayPermissionBtn = parent.findViewById(R.id.overlayPermissionBtn);
        if (overlayPermissionBtn != null) {
            overlayPermissionBtn.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
            overlayPermissionBtn.setOnClickListener(v -> {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        }
    }

    private void updateTntOverlaySettingsText() {
        if (currentTntOverlaySettingsText == null) {
            return;
        }
        int width = Pref.getTntOverlayWidth();
        int height = Pref.getTntOverlayHeight();
        int dpi = Pref.getTntOverlayDpi();
        int presetIndex = findMatchingTntOverlayPresetIndex(width, height, dpi);
        String presetName = TNT_OVERLAY_PRESETS[presetIndex].custom
                ? "Custom"
                : TNT_OVERLAY_PRESETS[presetIndex].label;
        currentTntOverlaySettingsText.setText(String.format(
                Locale.US,
                "Current: %d x %d / %ddpi | Preset: %s",
                width,
                height,
                dpi,
                presetName));
    }

    private void updateBackupTntSection() {
        boolean overlayActive = TntOverlayHelper.isOverlayOwnedByApp();
        boolean tntDebugEnabled = TntOverlayHelper.isOverlayDebugPropertyEnabled();

        if (backupTntRootStatus != null) {
            if (backupRootChecking) {
                backupTntRootStatus.setText("检查中");
                backupTntRootStatus.setTextColor(0xFF80868B);
            } else if (!backupRootChecked) {
                backupTntRootStatus.setText("未检查");
                backupTntRootStatus.setTextColor(0xFF80868B);
            } else if (backupRootGranted) {
                backupTntRootStatus.setText("已授权");
                backupTntRootStatus.setTextColor(0xFF2E7D32);
            } else {
                backupTntRootStatus.setText("未授权");
                backupTntRootStatus.setTextColor(0xFF80868B);
            }
        }

        if (checkBackupTntRootButton != null) {
            checkBackupTntRootButton.setEnabled(!backupRootChecking);
            checkBackupTntRootButton.setText(backupRootChecking ? "检查中" : "检查");
        }

        if (backupTntDebugStatus != null) {
            backupTntDebugStatus.setText(tntDebugEnabled ? "已开启" : "未开启");
            backupTntDebugStatus.setTextColor(tntDebugEnabled ? 0xFF2E7D32 : 0xFF80868B);
        }

        if (backupTntDebugSwitch != null) {
            suppressBackupTntDebugSwitchCallback = true;
            backupTntDebugSwitch.setChecked(tntDebugEnabled);
            backupTntDebugSwitch.setEnabled(backupRootGranted && !backupRootChecking);
            suppressBackupTntDebugSwitchCallback = false;
        }

        if (backupTntOverlayButton != null) {
            backupTntOverlayButton.setEnabled(!backupRootChecking);
            backupTntOverlayButton.setText(overlayActive ? "关闭备用 TNT overlay" : "启动备用 TNT overlay");
        }
    }

    private void runBackupTntRootCheck() {
        if (backupRootChecking) {
            return;
        }
        backupRootChecking = true;
        updateBackupTntSection();
        new Thread(() -> {
            boolean hasRoot = TntOverlayHelper.hasRootAccess();
            runOnUiThread(() -> {
                backupRootGranted = hasRoot;
                backupRootChecked = true;
                backupRootChecking = false;
                updateBackupTntSection();
            });
        }, "TNTShakerBackupRootCheck").start();
    }

    private void setBackupTntDebugEnabled(boolean enabled) {
        if (!backupRootGranted) {
            Toast.makeText(this, "备用 overlay 方式需要先授予 Root 权限", Toast.LENGTH_SHORT).show();
            updateBackupTntSection();
            return;
        }
        if (backupTntDebugSwitch != null) {
            backupTntDebugSwitch.setEnabled(false);
        }
        if (backupTntDebugStatus != null) {
            backupTntDebugStatus.setText("设置中");
            backupTntDebugStatus.setTextColor(0xFF80868B);
        }
        new Thread(() -> {
            boolean success = TntOverlayHelper.setOverlayDebugPropertyEnabled(enabled);
            runOnUiThread(() -> {
                if (success) {
                    backupRootChecked = true;
                    backupRootGranted = true;
                }
                Toast.makeText(
                        this,
                        success ? "已写入 TNT 调试选项，重启系统后完全生效" : "TNT 调试选项写入失败",
                        Toast.LENGTH_LONG).show();
                updateBackupTntSection();
            });
        }, "TNTShakerBackupOverlayProp").start();
    }

    private void toggleBackupTntOverlay() {
        if (backupRootChecking) {
            return;
        }
        boolean overlayActive = TntOverlayHelper.isOverlayOwnedByApp();
        if (backupTntOverlayButton != null) {
            backupTntOverlayButton.setEnabled(false);
        }
        new Thread(() -> {
            boolean success = overlayActive
                    ? TntOverlayHelper.clearOverlayDisplay()
                    : TntOverlayHelper.ensureHeadlessOverlayDisplayFromPreferences();
            boolean hasRoot = success || TntOverlayHelper.hasRootAccess();
            runOnUiThread(() -> {
                backupRootChecked = true;
                backupRootGranted = hasRoot;
                Toast.makeText(
                        this,
                        success
                                ? (overlayActive ? "已关闭备用 TNT overlay" : "已启动备用 TNT overlay")
                                : "备用 TNT overlay 操作失败",
                        Toast.LENGTH_SHORT).show();
                updateBackupTntSection();
            });
        }, "TNTShakerBackupOverlayToggle").start();
    }

    private void showTntOverlaySettingsDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_tnt_overlay_settings, null);

        Spinner presetSpinner = dialogView.findViewById(R.id.tntOverlayPresetSpinner);
        EditText widthEditText = dialogView.findViewById(R.id.tntOverlayWidthEditText);
        EditText heightEditText = dialogView.findViewById(R.id.tntOverlayHeightEditText);
        EditText dpiEditText = dialogView.findViewById(R.id.tntOverlayDpiEditText);

        List<String> presetLabels = new ArrayList<>();
        for (TntOverlayPreset preset : TNT_OVERLAY_PRESETS) {
            presetLabels.add(preset.label);
        }
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                presetLabels);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(presetAdapter);

        int currentWidth = Pref.getTntOverlayWidth();
        int currentHeight = Pref.getTntOverlayHeight();
        int currentDpi = Pref.getTntOverlayDpi();
        widthEditText.setText(String.valueOf(currentWidth));
        heightEditText.setText(String.valueOf(currentHeight));
        dpiEditText.setText(String.valueOf(currentDpi));

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TntOverlayPreset preset = TNT_OVERLAY_PRESETS[position];
                if (preset.custom) {
                    return;
                }
                widthEditText.setText(String.valueOf(preset.width));
                heightEditText.setText(String.valueOf(preset.height));
                dpiEditText.setText(String.valueOf(preset.dpi));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        presetSpinner.setSelection(findMatchingTntOverlayPresetIndex(currentWidth, currentHeight, currentDpi));

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setTitle("TNT overlay resolution")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    int width = parseClampedInt(widthEditText, 1920, 100, 8192);
                    int height = parseClampedInt(heightEditText, 1080, 100, 8192);
                    int dpi = parseClampedInt(dpiEditText, 216, 72, 640);
                    preferences.edit()
                            .putInt(Pref.KEY_TNT_OVERLAY_WIDTH, width)
                            .putInt(Pref.KEY_TNT_OVERLAY_HEIGHT, height)
                            .putInt(Pref.KEY_TNT_OVERLAY_DPI, dpi)
                            .apply();
                    updateTntOverlaySettingsText();
                    Toast.makeText(
                            this,
                            "TNT overlay resolution saved. It will apply next time TNT starts.",
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Reset", (dialog, which) -> {
                    preferences.edit()
                            .putInt(Pref.KEY_TNT_OVERLAY_WIDTH, 1920)
                            .putInt(Pref.KEY_TNT_OVERLAY_HEIGHT, 1080)
                            .putInt(Pref.KEY_TNT_OVERLAY_DPI, 216)
                            .apply();
                    updateTntOverlaySettingsText();
                    Toast.makeText(this, "TNT overlay resolution reset to default.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int findMatchingTntOverlayPresetIndex(int width, int height, int dpi) {
        for (int i = 0; i < TNT_OVERLAY_PRESETS.length; i++) {
            TntOverlayPreset preset = TNT_OVERLAY_PRESETS[i];
            if (!preset.custom && preset.matches(width, height, dpi)) {
                return i;
            }
        }
        return TNT_OVERLAY_PRESETS.length - 1;
    }

    private void setupEncoderSettings() {
        encoderBitratePercentEditText = findViewById(R.id.encoderBitratePercentEditText);
        encoderComplexityEditText = findViewById(R.id.encoderComplexityEditText);
        encoderIFrameIntervalEditText = findViewById(R.id.encoderIFrameIntervalEditText);
        encoderMaxFpsEditText = findViewById(R.id.encoderMaxFpsEditText);
        streamFecPercentEditText = findViewById(R.id.streamFecPercentEditText);
        encoderCodecSpinner = findViewById(R.id.encoderCodecSpinner);
        encoderBitrateModeSpinner = findViewById(R.id.encoderBitrateModeSpinner);
        encoderLowLatencyCheckbox = findViewById(R.id.encoderLowLatencyCheckbox);
        encoderDisableBFramesCheckbox = findViewById(R.id.encoderDisableBFramesCheckbox);
        encoderRealtimePriorityCheckbox = findViewById(R.id.encoderRealtimePriorityCheckbox);
        encoderDynamicFrameRateCheckbox = findViewById(R.id.encoderDynamicFrameRateCheckbox);
        currentEncoderSettingsText = findViewById(R.id.currentEncoderSettingsText);

        styleSwitch(encoderLowLatencyCheckbox);
        styleSwitch(encoderDisableBFramesCheckbox);
        styleSwitch(encoderRealtimePriorityCheckbox);
        styleSwitch(encoderDynamicFrameRateCheckbox);

        String[] codecs = new String[]{
                "H.264 / AVC - default",
                "H.265 / HEVC - higher compression"
        };
        ArrayAdapter<String> codecAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                codecs);
        codecAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderCodecSpinner.setAdapter(codecAdapter);

        String[] bitrateModes = new String[]{
                "CBR - Stable bandwidth",
                "VBR - Variable bitrate",
                "CQ - Constant quality (experimental)"
        };
        ArrayAdapter<String> bitrateModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                bitrateModes);
        bitrateModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderBitrateModeSpinner.setAdapter(bitrateModeAdapter);

        loadEncoderSettingsIntoViews();

        Button saveEncoderSettingsButton = findViewById(R.id.saveEncoderSettingsButton);
        saveEncoderSettingsButton.setOnClickListener(v -> {
            saveEncoderSettingsFromViews();
            SunshineServer.setEncoderSettingsFromPreferences();
            SunshineServer.setVideoCodec(Pref.getEncoderCodec());
            Toast.makeText(this, "Encoder settings saved.", Toast.LENGTH_SHORT).show();
        });

        Button resetEncoderSettingsButton = findViewById(R.id.resetEncoderSettingsButton);
        resetEncoderSettingsButton.setOnClickListener(v -> {
            preferences.edit()
                    .putInt(Pref.KEY_ENCODER_BITRATE_PERCENT, 100)
                    .putInt(Pref.KEY_ENCODER_CODEC, Pref.ENCODER_CODEC_H264)
                    .putInt(Pref.KEY_ENCODER_BITRATE_MODE, 2)
                    .putInt(Pref.KEY_ENCODER_COMPLEXITY, 5)
                    .putInt(Pref.KEY_ENCODER_I_FRAME_INTERVAL, 3)
                    .putInt(Pref.KEY_ENCODER_MAX_FPS, 60)
                    .putBoolean(Pref.KEY_ENCODER_LOW_LATENCY, true)
                    .putBoolean(Pref.KEY_ENCODER_DISABLE_B_FRAMES, true)
                    .putBoolean(Pref.KEY_ENCODER_REALTIME_PRIORITY, true)
                    .putBoolean(Pref.KEY_ENCODER_DYNAMIC_FRAME_RATE, false)
                    .putInt(Pref.KEY_STREAM_FEC_PERCENT, 0)
                    .apply();
            loadEncoderSettingsIntoViews();
            SunshineServer.setEncoderSettingsFromPreferences();
            SunshineServer.setVideoCodec(Pref.getEncoderCodec());
            Toast.makeText(this, "Encoder settings reset.", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadEncoderSettingsIntoViews() {
        encoderBitratePercentEditText.setText(String.valueOf(Pref.getEncoderBitratePercent()));
        encoderComplexityEditText.setText(String.valueOf(Pref.getEncoderComplexity()));
        encoderIFrameIntervalEditText.setText(String.valueOf(Pref.getEncoderIFrameInterval()));
        encoderMaxFpsEditText.setText(String.valueOf(Pref.getEncoderMaxFps()));
        streamFecPercentEditText.setText(String.valueOf(Pref.getStreamFecPercent()));
        encoderLowLatencyCheckbox.setChecked(Pref.getEncoderLowLatency());
        encoderDisableBFramesCheckbox.setChecked(Pref.getEncoderDisableBFrames());
        encoderRealtimePriorityCheckbox.setChecked(Pref.getEncoderRealtimePriority());
        encoderDynamicFrameRateCheckbox.setChecked(Pref.getEncoderDynamicFrameRate());

        int codec = Pref.getEncoderCodec();
        for (int i = 0; i < ENCODER_CODEC_VALUES.length; i++) {
            if (ENCODER_CODEC_VALUES[i] == codec) {
                encoderCodecSpinner.setSelection(i);
                break;
            }
        }

        int bitrateMode = Pref.getEncoderBitrateMode();
        for (int i = 0; i < ENCODER_BITRATE_MODE_VALUES.length; i++) {
            if (ENCODER_BITRATE_MODE_VALUES[i] == bitrateMode) {
                encoderBitrateModeSpinner.setSelection(i);
                break;
            }
        }
        updateEncoderSettingsText();
    }

    private void saveEncoderSettingsFromViews() {
        int codecIndex = encoderCodecSpinner.getSelectedItemPosition();
        int codec = ENCODER_CODEC_VALUES[Math.max(
                0,
                Math.min(ENCODER_CODEC_VALUES.length - 1, codecIndex))];
        int bitrateModeIndex = encoderBitrateModeSpinner.getSelectedItemPosition();
        int bitrateMode = ENCODER_BITRATE_MODE_VALUES[Math.max(
                0,
                Math.min(ENCODER_BITRATE_MODE_VALUES.length - 1, bitrateModeIndex))];
        preferences.edit()
                .putInt(Pref.KEY_ENCODER_CODEC, codec)
                .putInt(Pref.KEY_ENCODER_BITRATE_PERCENT, parseClampedInt(encoderBitratePercentEditText, 100, 25, 200))
                .putInt(Pref.KEY_ENCODER_BITRATE_MODE, bitrateMode)
                .putInt(Pref.KEY_ENCODER_COMPLEXITY, parseClampedInt(encoderComplexityEditText, 5, 0, 10))
                .putInt(Pref.KEY_ENCODER_I_FRAME_INTERVAL, parseClampedInt(encoderIFrameIntervalEditText, 3, 1, 10))
                .putInt(Pref.KEY_ENCODER_MAX_FPS, parseClampedInt(encoderMaxFpsEditText, 60, 1, 240))
                .putBoolean(Pref.KEY_ENCODER_LOW_LATENCY, encoderLowLatencyCheckbox.isChecked())
                .putBoolean(Pref.KEY_ENCODER_DISABLE_B_FRAMES, encoderDisableBFramesCheckbox.isChecked())
                .putBoolean(Pref.KEY_ENCODER_REALTIME_PRIORITY, encoderRealtimePriorityCheckbox.isChecked())
                .putBoolean(Pref.KEY_ENCODER_DYNAMIC_FRAME_RATE, encoderDynamicFrameRateCheckbox.isChecked())
                .putInt(Pref.KEY_STREAM_FEC_PERCENT, parseClampedInt(streamFecPercentEditText, 0, 0, 50))
                .apply();
        loadEncoderSettingsIntoViews();
    }

    private int parseClampedInt(EditText editText, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void updateEncoderSettingsText() {
        String codecName = Pref.getEncoderCodec() == Pref.ENCODER_CODEC_H264 ? "H.264" : "H.265";
        String modeName;
        switch (Pref.getEncoderBitrateMode()) {
            case 0:
                modeName = "CQ";
                break;
            case 1:
                modeName = "VBR";
                break;
            case 2:
            default:
                modeName = "CBR";
                break;
        }
        String text = String.format(
                Locale.US,
                "Codec %s | Bitrate %d%% | Mode %s | Complexity %d | I-frame %ds | Max FPS %d | Frame %s | FEC %d%%",
                codecName,
                Pref.getEncoderBitratePercent(),
                modeName,
                Pref.getEncoderComplexity(),
                Pref.getEncoderIFrameInterval(),
                Pref.getEncoderMaxFps(),
                Pref.getEncoderDynamicFrameRate() ? "dynamic" : "fixed",
                Pref.getStreamFecPercent());
        currentEncoderSettingsText.setText(text);
    }

    private void loadClientList(Spinner spinner) {
        String selectedClient = Pref.getSelectedClient();
        List<String> clients = new ArrayList<>();
        clients.add(MANUAL_INPUT_LABEL);
        if (!selectedClient.isEmpty() && !clients.contains(selectedClient)) {
            clients.add(selectedClient);
        }
        for (String discovered : State.discoveredConnectScreenClients) {
            if (!clients.contains(discovered)) {
                clients.add(discovered);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                clients);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (!selectedClient.isEmpty()) {
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).equals(selectedClient)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void showManualInputDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_client_input, null);
        EditText ipEditText = dialogView.findViewById(R.id.ipEditText);
        EditText portEditText = dialogView.findViewById(R.id.portEditText);
        portEditText.setText("42515");

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setTitle("Manual client address")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    String ip = ipEditText.getText().toString().trim();
                    String port = portEditText.getText().toString().trim();
                    if (ip.isEmpty()) {
                        return;
                    }
                    String clientAddress = port.isEmpty() ? ip : ip + ":" + port;
                    preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, clientAddress).apply();
                    Spinner clientSpinner = findViewById(R.id.clientSpinner);
                    loadClientList(clientSpinner);
                    int pin = (int) (Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                })
                .setNegativeButton("Cancel", null)
                .show();
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
    }
}
