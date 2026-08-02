package com.connect_screen.mirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.connect_screen.mirror.job.ConnectToClient;
import com.connect_screen.mirror.job.SunshineServer;
import com.connect_screen.mirror.shizuku.PermissionManager;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StreamingSettingsFragment extends Fragment {
    private static final String MANUAL_INPUT_LABEL = "手动输入";
    private static final int[] ENCODER_CODEC_VALUES = new int[]{Pref.ENCODER_CODEC_H264, Pref.ENCODER_CODEC_H265};
    private static final int[] ENCODER_BITRATE_MODE_VALUES = new int[]{2, 1, 0};
    private static final int[] ENCODER_AVC_PROFILE_VALUES = new int[]{
            Pref.ENCODER_AVC_PROFILE_BASELINE,
            Pref.ENCODER_AVC_PROFILE_HIGH
    };
    private static final int[] ENCODER_AVC_LEVEL_VALUES = new int[]{
            Pref.ENCODER_AVC_LEVEL_42,
            Pref.ENCODER_AVC_LEVEL_51,
            Pref.ENCODER_AVC_LEVEL_52
    };

    private SharedPreferences preferences;
    private EditText encoderBitratePercentEditText;
    private EditText encoderComplexityEditText;
    private EditText encoderIFrameIntervalEditText;
    private EditText encoderMaxFpsEditText;
    private EditText streamFecPercentEditText;
    private Spinner encoderCodecSpinner;
    private Spinner encoderBitrateModeSpinner;
    private Spinner encoderAvcProfileSpinner;
    private Spinner encoderAvcLevelSpinner;
    private SwitchCompat encoderLowLatencyCheckbox;
    private SwitchCompat encoderDisableBFramesCheckbox;
    private SwitchCompat encoderRealtimePriorityCheckbox;
    private SwitchCompat encoderDynamicFrameRateCheckbox;
    private Spinner clientSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_streaming_settings, container, false);
        preferences = requireContext().getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE);

        bindSmartisanBoost(view);
        bindAutoConnect(view);
        bindAudioSwitches(view);
        setupEncoderSettings(view);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SunshineServer.suppressPin = null;
    }

    private void bindSmartisanBoost(View root) {
        SwitchCompat smartisanCpusetBoostCheckbox = root.findViewById(R.id.smartisanCpusetBoostCheckbox);
        UiCompat.tintSwitch(requireContext(), smartisanCpusetBoostCheckbox);
        smartisanCpusetBoostCheckbox.setChecked(Pref.getSmartisanCpusetBoost());
        smartisanCpusetBoostCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_SMARTISAN_CPUSET_BOOST, isChecked).apply();
            if (SunshineService.getLifecycleState() == SunshineService.LifecycleState.RUNNING) {
                SmartisanPerformanceHelper.updateStreamingBoost(
                        isChecked,
                        "settings toggle while Sunshine is running");
            }
        });
    }

    private void bindAutoConnect(View root) {
        SwitchCompat autoConnectClientCheckbox = root.findViewById(R.id.autoConnectClientCheckbox);
        View clientConnectionContainer = root.findViewById(R.id.clientConnectionContainer);
        clientSpinner = root.findViewById(R.id.clientSpinner);
        Button connectClientButton = root.findViewById(R.id.connectClientButton);
        UiCompat.tintSwitch(requireContext(), autoConnectClientCheckbox);
        boolean autoConnect = Pref.getAutoConnectClient();
        autoConnectClientCheckbox.setChecked(autoConnect);
        clientConnectionContainer.setVisibility(autoConnect ? View.VISIBLE : View.GONE);
        if (autoConnect) {
            loadClientList();
        }
        autoConnectClientCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_AUTO_CONNECT_CLIENT, isChecked).apply();
            clientConnectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                loadClientList();
            }
        });
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
    }

    private void bindAudioSwitches(View root) {
        SwitchCompat disableUsbAudioCheckbox = root.findViewById(R.id.disableUsbAudioCheckbox);
        UiCompat.tintSwitch(requireContext(), disableUsbAudioCheckbox);
        disableUsbAudioCheckbox.setChecked(Pref.getDisableUsbAudio());
        disableUsbAudioCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(Pref.KEY_DISABLE_USB_AUDIO, isChecked).apply();
            if (ShizukuUtils.hasPermission() && PermissionManager.grant("android.permission.WRITE_SECURE_SETTINGS")) {
                try {
                    Settings.Secure.putInt(
                            requireContext().getContentResolver(),
                            "usb_audio_automatic_routing_disabled",
                            isChecked ? 1 : 0);
                } catch (SecurityException e) {
                    State.log("failed to set usb_audio_automatic_routing_disabled: " + e);
                }
            }
        });
        disableUsbAudioCheckbox.setEnabled(ShizukuUtils.hasPermission());
    }

    private void setupEncoderSettings(View root) {
        encoderBitratePercentEditText = root.findViewById(R.id.encoderBitratePercentEditText);
        encoderComplexityEditText = root.findViewById(R.id.encoderComplexityEditText);
        encoderIFrameIntervalEditText = root.findViewById(R.id.encoderIFrameIntervalEditText);
        encoderMaxFpsEditText = root.findViewById(R.id.encoderMaxFpsEditText);
        streamFecPercentEditText = root.findViewById(R.id.streamFecPercentEditText);
        encoderCodecSpinner = root.findViewById(R.id.encoderCodecSpinner);
        encoderBitrateModeSpinner = root.findViewById(R.id.encoderBitrateModeSpinner);
        encoderAvcProfileSpinner = root.findViewById(R.id.encoderAvcProfileSpinner);
        encoderAvcLevelSpinner = root.findViewById(R.id.encoderAvcLevelSpinner);
        encoderLowLatencyCheckbox = root.findViewById(R.id.encoderLowLatencyCheckbox);
        encoderDisableBFramesCheckbox = root.findViewById(R.id.encoderDisableBFramesCheckbox);
        encoderRealtimePriorityCheckbox = root.findViewById(R.id.encoderRealtimePriorityCheckbox);
        encoderDynamicFrameRateCheckbox = root.findViewById(R.id.encoderDynamicFrameRateCheckbox);
        UiCompat.tintSwitch(requireContext(), encoderLowLatencyCheckbox);
        UiCompat.tintSwitch(requireContext(), encoderDisableBFramesCheckbox);
        UiCompat.tintSwitch(requireContext(), encoderRealtimePriorityCheckbox);
        UiCompat.tintSwitch(requireContext(), encoderDynamicFrameRateCheckbox);

        ArrayAdapter<String> codecAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"H.264 / AVC", "H.265 / HEVC"});
        codecAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderCodecSpinner.setAdapter(codecAdapter);

        ArrayAdapter<String> bitrateModeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"CBR - 稳定带宽", "VBR - 动态码率", "CQ - 恒定质量"});
        bitrateModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderBitrateModeSpinner.setAdapter(bitrateModeAdapter);

        ArrayAdapter<String> avcProfileAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Baseline（兼容性）", "High（更高质量）"});
        avcProfileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderAvcProfileSpinner.setAdapter(avcProfileAdapter);

        ArrayAdapter<String> avcLevelAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Level 4.2", "Level 5.1", "Level 5.2"});
        avcLevelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        encoderAvcLevelSpinner.setAdapter(avcLevelAdapter);

        loadEncoderSettingsIntoViews();

        root.findViewById(R.id.saveEncoderSettingsButton).setOnClickListener(v -> {
            saveEncoderSettingsFromViews();
            SunshineServer.setEncoderSettingsFromPreferences();
            SunshineServer.setVideoCodec(Pref.getEncoderCodec());
            Toast.makeText(requireContext(), "编码设置已保存", Toast.LENGTH_SHORT).show();
        });
        root.findViewById(R.id.resetEncoderSettingsButton).setOnClickListener(v -> {
            preferences.edit()
                    .putInt(Pref.KEY_ENCODER_BITRATE_PERCENT, 100)
                    .putInt(Pref.KEY_ENCODER_CODEC, Pref.ENCODER_CODEC_H264)
                    .putInt(Pref.KEY_ENCODER_BITRATE_MODE, 2)
                    .putBoolean(Pref.KEY_ENCODER_AVC_BASELINE, true)
                    .putInt(Pref.KEY_ENCODER_AVC_LEVEL, Pref.ENCODER_AVC_LEVEL_42)
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
            Toast.makeText(requireContext(), "编码设置已恢复默认", Toast.LENGTH_SHORT).show();
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
        selectValue(encoderCodecSpinner, ENCODER_CODEC_VALUES, Pref.getEncoderCodec());
        selectValue(encoderBitrateModeSpinner, ENCODER_BITRATE_MODE_VALUES, Pref.getEncoderBitrateMode());
        selectValue(encoderAvcProfileSpinner, ENCODER_AVC_PROFILE_VALUES, Pref.getEncoderAvcProfile());
        selectValue(encoderAvcLevelSpinner, ENCODER_AVC_LEVEL_VALUES, Pref.getEncoderAvcLevel());
    }

    private void saveEncoderSettingsFromViews() {
        int codecIndex = encoderCodecSpinner.getSelectedItemPosition();
        int bitrateModeIndex = encoderBitrateModeSpinner.getSelectedItemPosition();
        int avcProfileIndex = encoderAvcProfileSpinner.getSelectedItemPosition();
        int avcLevelIndex = encoderAvcLevelSpinner.getSelectedItemPosition();
        int avcProfile = ENCODER_AVC_PROFILE_VALUES[Math.max(0, Math.min(
                ENCODER_AVC_PROFILE_VALUES.length - 1, avcProfileIndex))];
        int avcLevel = ENCODER_AVC_LEVEL_VALUES[Math.max(0, Math.min(
                ENCODER_AVC_LEVEL_VALUES.length - 1, avcLevelIndex))];
        preferences.edit()
                .putInt(Pref.KEY_ENCODER_CODEC, ENCODER_CODEC_VALUES[Math.max(0, Math.min(ENCODER_CODEC_VALUES.length - 1, codecIndex))])
                .putInt(Pref.KEY_ENCODER_BITRATE_PERCENT, parseClampedInt(encoderBitratePercentEditText, 100, 25, 200))
                .putInt(Pref.KEY_ENCODER_BITRATE_MODE, ENCODER_BITRATE_MODE_VALUES[Math.max(0, Math.min(ENCODER_BITRATE_MODE_VALUES.length - 1, bitrateModeIndex))])
                .putBoolean(Pref.KEY_ENCODER_AVC_BASELINE, avcProfile == Pref.ENCODER_AVC_PROFILE_BASELINE)
                .putInt(Pref.KEY_ENCODER_AVC_LEVEL, avcLevel)
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

    private void selectValue(Spinner spinner, int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void loadClientList() {
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
                requireContext(),
                android.R.layout.simple_spinner_item,
                clients);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clientSpinner.setAdapter(adapter);
    }

    private void showManualInputDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manual_client_input, null);
        EditText ipEditText = dialogView.findViewById(R.id.ipEditText);
        EditText portEditText = dialogView.findViewById(R.id.portEditText);
        portEditText.setText("42515");
        new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setTitle("手动输入客户端地址")
                .setView(dialogView)
                .setPositiveButton("连接", (dialog, which) -> {
                    String ip = ipEditText.getText().toString().trim();
                    String port = portEditText.getText().toString().trim();
                    if (ip.isEmpty()) {
                        return;
                    }
                    String clientAddress = port.isEmpty() ? ip : ip + ":" + port;
                    preferences.edit().putString(Pref.KEY_SELECTED_CLIENT, clientAddress).apply();
                    loadClientList();
                    int pin = (int) (Math.random() * 9000) + 1000;
                    SunshineServer.suppressPin = String.valueOf(pin);
                    ConnectToClient.connect(pin);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int parseClampedInt(EditText editText, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
