package com.connect_screen.mirror;

import android.app.AlertDialog;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.ConnectToClient;
import com.connect_screen.mirror.job.SunshineServer;
import com.connect_screen.mirror.shizuku.PermissionManager;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MirrorSettingsActivity extends AppCompatActivity {
    public static final String PREF_NAME = "mirror_settings";
    private static final String MANUAL_INPUT_LABEL = "Manual input";
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
    private EditText encoderBitratePercentEditText;
    private EditText encoderComplexityEditText;
    private EditText encoderIFrameIntervalEditText;
    private EditText encoderMaxFpsEditText;
    private EditText streamFecPercentEditText;
    private Spinner encoderBitrateModeSpinner;
    private SwitchCompat encoderLowLatencyCheckbox;
    private SwitchCompat encoderDisableBFramesCheckbox;
    private SwitchCompat encoderRealtimePriorityCheckbox;
    private TextView currentEncoderSettingsText;

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
        SwitchCompat useAndroidCursorOverlayCheckbox = findViewById(R.id.useAndroidCursorOverlayCheckbox);
        SwitchCompat disableAccessibilityCheckbox = findViewById(R.id.disableAccessibilityCheckbox);

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
        styleSwitch(useAndroidCursorOverlayCheckbox);
        styleSwitch(disableAccessibilityCheckbox);

        LinearLayout clientConnectionContainer = findViewById(R.id.clientConnectionContainer);
        Spinner clientSpinner = findViewById(R.id.clientSpinner);
        Button connectClientButton = findViewById(R.id.connectClientButton);
        TextView shizukuStatus = findViewById(R.id.shizukuStatus);
        TextView accessibilityStatus = findViewById(R.id.accessibilityStatus);
        TextView overlayStatus = findViewById(R.id.overlayStatus);
        Button shizukuPermissionBtn = findViewById(R.id.shizukuPermissionBtn);
        currentTntOverlaySettingsText = findViewById(R.id.currentTntOverlaySettingsText);

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
        useAndroidCursorOverlayCheckbox.setChecked(Pref.getUseAndroidCursorOverlay());
        disableAccessibilityCheckbox.setChecked(!Pref.getDisableAccessibility());
        disableAccessibilityCheckbox.setText("Accessibility compatibility mode");

        if (ShizukuUtils.hasPermission()) {
            autoScreenOffCheckbox.setText("Auto screen off");
        }

        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);
        updateAccessibilityStatus(accessibilityStatus);
        updateOverlayStatus(overlayStatus);
        updateTntOverlaySettingsText();

        shizukuPermissionBtn.setOnClickListener(v -> State.startNewJob(new AcquireShizuku()));

        Button initializationGuideButton = findViewById(R.id.initializationGuideButton);
        initializationGuideButton.setOnClickListener(v -> InitializationGuideDialog.show(this));

        Button aboutButton = findViewById(R.id.aboutButton);
        aboutButton.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));

        Button screenSettingsButton = findViewById(R.id.screenSettingsButton);
        screenSettingsButton.setOnClickListener(v -> startActivity(new Intent(this, ScreenSettingsActivity.class)));

        Button editTntOverlaySettingsButton = findViewById(R.id.editTntOverlaySettingsButton);
        editTntOverlaySettingsButton.setOnClickListener(v -> showTntOverlaySettingsDialog());

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
        useAndroidCursorOverlayCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_USE_ANDROID_CURSOR_OVERLAY, isChecked).apply());

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

    @Override
    protected void onResume() {
        super.onResume();
        updateShizukuStatus(findViewById(R.id.shizukuStatus), findViewById(R.id.shizukuPermissionBtn));
        updateAccessibilityStatus(findViewById(R.id.accessibilityStatus));
        updateOverlayStatus(findViewById(R.id.overlayStatus));
        updateTntOverlaySettingsText();

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

        new AlertDialog.Builder(this)
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
        encoderBitrateModeSpinner = findViewById(R.id.encoderBitrateModeSpinner);
        encoderLowLatencyCheckbox = findViewById(R.id.encoderLowLatencyCheckbox);
        encoderDisableBFramesCheckbox = findViewById(R.id.encoderDisableBFramesCheckbox);
        encoderRealtimePriorityCheckbox = findViewById(R.id.encoderRealtimePriorityCheckbox);
        currentEncoderSettingsText = findViewById(R.id.currentEncoderSettingsText);

        styleSwitch(encoderLowLatencyCheckbox);
        styleSwitch(encoderDisableBFramesCheckbox);
        styleSwitch(encoderRealtimePriorityCheckbox);

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
            Toast.makeText(this, "Encoder settings saved.", Toast.LENGTH_SHORT).show();
        });

        Button resetEncoderSettingsButton = findViewById(R.id.resetEncoderSettingsButton);
        resetEncoderSettingsButton.setOnClickListener(v -> {
            preferences.edit()
                    .putInt(Pref.KEY_ENCODER_BITRATE_PERCENT, 100)
                    .putInt(Pref.KEY_ENCODER_BITRATE_MODE, 2)
                    .putInt(Pref.KEY_ENCODER_COMPLEXITY, 5)
                    .putInt(Pref.KEY_ENCODER_I_FRAME_INTERVAL, 3)
                    .putInt(Pref.KEY_ENCODER_MAX_FPS, 120)
                    .putBoolean(Pref.KEY_ENCODER_LOW_LATENCY, true)
                    .putBoolean(Pref.KEY_ENCODER_DISABLE_B_FRAMES, true)
                    .putBoolean(Pref.KEY_ENCODER_REALTIME_PRIORITY, true)
                    .putInt(Pref.KEY_STREAM_FEC_PERCENT, 20)
                    .apply();
            loadEncoderSettingsIntoViews();
            SunshineServer.setEncoderSettingsFromPreferences();
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
        int bitrateModeIndex = encoderBitrateModeSpinner.getSelectedItemPosition();
        int bitrateMode = ENCODER_BITRATE_MODE_VALUES[Math.max(
                0,
                Math.min(ENCODER_BITRATE_MODE_VALUES.length - 1, bitrateModeIndex))];
        preferences.edit()
                .putInt(Pref.KEY_ENCODER_BITRATE_PERCENT, parseClampedInt(encoderBitratePercentEditText, 100, 25, 200))
                .putInt(Pref.KEY_ENCODER_BITRATE_MODE, bitrateMode)
                .putInt(Pref.KEY_ENCODER_COMPLEXITY, parseClampedInt(encoderComplexityEditText, 5, 0, 10))
                .putInt(Pref.KEY_ENCODER_I_FRAME_INTERVAL, parseClampedInt(encoderIFrameIntervalEditText, 3, 1, 10))
                .putInt(Pref.KEY_ENCODER_MAX_FPS, parseClampedInt(encoderMaxFpsEditText, 120, 1, 240))
                .putBoolean(Pref.KEY_ENCODER_LOW_LATENCY, encoderLowLatencyCheckbox.isChecked())
                .putBoolean(Pref.KEY_ENCODER_DISABLE_B_FRAMES, encoderDisableBFramesCheckbox.isChecked())
                .putBoolean(Pref.KEY_ENCODER_REALTIME_PRIORITY, encoderRealtimePriorityCheckbox.isChecked())
                .putInt(Pref.KEY_STREAM_FEC_PERCENT, parseClampedInt(streamFecPercentEditText, 20, 0, 50))
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
                "Bitrate %d%% | Mode %s | Complexity %d | I-frame %ds | Max FPS %d | FEC %d%%",
                Pref.getEncoderBitratePercent(),
                modeName,
                Pref.getEncoderComplexity(),
                Pref.getEncoderIFrameInterval(),
                Pref.getEncoderMaxFps(),
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

        new AlertDialog.Builder(this)
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
