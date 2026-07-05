package com.easycast.source;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.easycast.source.job.TntOverlayHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class TntSettingsFragment extends Fragment {
    private static final TntOverlayPreset[] TNT_OVERLAY_PRESETS = new TntOverlayPreset[]{
            new TntOverlayPreset("默认 1080P（1920 x 1080 / 216dpi）", 1920, 1080, 216, false),
            new TntOverlayPreset("4K（3840 x 2160 / 320dpi）", 3840, 2160, 320, false),
            new TntOverlayPreset("iPad mini 6（2266 x 1488 / 320dpi）", 2266, 1488, 320, false),
            new TntOverlayPreset("iPad Pro 11（2420 x 1668 / 320dpi）", 2420, 1668, 320, false),
            new TntOverlayPreset("Xiaomi Pad 6 Pro（2880 x 1800 / 320dpi）", 2880, 1800, 320, false),
            new TntOverlayPreset("自定义", 0, 0, 0, true)
    };

    private SharedPreferences preferences;
    private TextView backupTntRootStatus;
    private TextView backupTntDebugStatus;
    private Button checkBackupTntRootButton;
    private SwitchCompat backupTntDebugSwitch;
    private boolean backupRootGranted;
    private boolean backupRootChecked;
    private boolean backupRootChecking;
    private boolean suppressBackupTntDebugSwitchCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tnt_settings, container, false);
        preferences = requireContext().getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE);

        bindSwitch(view, R.id.adaptTntResolutionToClientCheckbox, Pref.getAdaptTntResolutionToClient(),
                Pref.KEY_ADAPT_TNT_RESOLUTION_TO_CLIENT);
        bindSwitch(view, R.id.autoCloseTntOnClientDisconnectCheckbox, Pref.getAutoCloseTntOnClientDisconnect(),
                Pref.KEY_AUTO_CLOSE_TNT_ON_CLIENT_DISCONNECT);
        bindSwitch(view, R.id.useAndroidCursorOverlayCheckbox, Pref.getUseAndroidCursorOverlay(),
                Pref.KEY_USE_ANDROID_CURSOR_OVERLAY);
        bindSwitch(view, R.id.mapMouseToTouchCheckbox, Pref.getMapMouseToTouch(),
                Pref.KEY_MAP_MOUSE_TO_TOUCH);
        bindSwitch(view, R.id.useTntOverlayBackendCheckbox, Pref.getUseTntOverlayBackend(),
                Pref.KEY_USE_TNT_OVERLAY_BACKEND);
        bindSwitch(view, R.id.autoRotateCheckbox, Pref.getAutoRotate(),
                Pref.KEY_AUTO_ROTATE);
        bindSwitch(view, R.id.autoScaleCheckbox, Pref.getAutoScale(),
                Pref.KEY_AUTO_SCALE);
        bindSwitch(view, R.id.autoScreenOffCheckbox, Pref.getAutoScreenOff(),
                Pref.KEY_AUTO_SCREEN_OFF);
        bindSwitch(view, R.id.useBlackImageCheckbox, Pref.getUseBlackImage(),
                Pref.KEY_USE_BLACK_IMAGE);
        bindSwitch(view, R.id.preventAutoLockCheckbox, Pref.getPreventAutoLock(),
                Pref.KEY_PREVENT_AUTO_LOCK);
        bindSwitch(view, R.id.showFloatingInMirrorModeCheckbox, Pref.getShowFloatingInMirrorMode(),
                Pref.KEY_SHOW_FLOATING_IN_MIRROR_MODE);
        bindSwitch(view, R.id.autoHideFloatingCheckbox, Pref.getAutoHideFloatingBackButton(),
                Pref.KEY_AUTO_HIDE_FLOATING_BACK_BUTTON);

        SwitchCompat tntModeCheckbox = view.findViewById(R.id.tntModeCheckbox);
        UiCompat.tintSwitch(requireContext(), tntModeCheckbox);
        tntModeCheckbox.setChecked(!Pref.getSkipExternalActivity());
        tntModeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(Pref.KEY_SKIP_EXTERNAL_ACTIVITY, !isChecked).apply());

        backupTntRootStatus = view.findViewById(R.id.backupTntRootStatus);
        backupTntDebugStatus = view.findViewById(R.id.backupTntDebugStatus);
        checkBackupTntRootButton = view.findViewById(R.id.checkBackupTntRootButton);
        backupTntDebugSwitch = view.findViewById(R.id.backupTntDebugSwitch);
        UiCompat.tintSwitch(requireContext(), backupTntDebugSwitch);
        checkBackupTntRootButton.setOnClickListener(v -> runBackupTntRootCheck());
        backupTntDebugSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressBackupTntDebugSwitchCallback) {
                setBackupTntDebugEnabled(isChecked);
            }
        });
        updateBackupTntSection();

        view.findViewById(R.id.editTntOverlaySettingsButton).setOnClickListener(v -> showTntOverlaySettingsDialog());
        view.findViewById(R.id.screenSettingsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ScreenSettingsActivity.class)));
        view.findViewById(R.id.aboutButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class)));
        return view;
    }

    private void bindSwitch(View root, int id, boolean checked, String key) {
        SwitchCompat switchCompat = root.findViewById(id);
        UiCompat.tintSwitch(requireContext(), switchCompat);
        switchCompat.setChecked(checked);
        switchCompat.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean(key, isChecked).apply());
    }

    private void runBackupTntRootCheck() {
        if (backupRootChecking) {
            return;
        }
        backupRootChecking = true;
        updateBackupTntSection();
        new Thread(() -> {
            boolean hasRoot = TntOverlayHelper.hasRootAccess();
            requireActivity().runOnUiThread(() -> {
                backupRootGranted = hasRoot;
                backupRootChecked = true;
                backupRootChecking = false;
                updateBackupTntSection();
            });
        }, "TNTAnywhereRootCheck").start();
    }

    private void updateBackupTntSection() {
        boolean debugEnabled = TntOverlayHelper.isOverlayDebugPropertyEnabled();
        if (backupTntRootStatus != null) {
            if (backupRootChecking) {
                backupTntRootStatus.setText("检查中");
            } else if (!backupRootChecked) {
                backupTntRootStatus.setText("未检查");
            } else {
                backupTntRootStatus.setText(backupRootGranted ? "已授权" : "未授权");
            }
        }
        if (checkBackupTntRootButton != null) {
            checkBackupTntRootButton.setEnabled(!backupRootChecking);
            checkBackupTntRootButton.setText(backupRootChecking ? "检查中" : "检查");
        }
        if (backupTntDebugStatus != null) {
            backupTntDebugStatus.setText(debugEnabled ? "已开启" : "未开启");
        }
        if (backupTntDebugSwitch != null) {
            suppressBackupTntDebugSwitchCallback = true;
            backupTntDebugSwitch.setChecked(debugEnabled);
            backupTntDebugSwitch.setEnabled(backupRootGranted && !backupRootChecking);
            suppressBackupTntDebugSwitchCallback = false;
        }
    }

    private void setBackupTntDebugEnabled(boolean enabled) {
        if (!backupRootGranted) {
            Toast.makeText(requireContext(), "需要先完成 Root 检查并授权", Toast.LENGTH_SHORT).show();
            updateBackupTntSection();
            return;
        }
        backupTntDebugSwitch.setEnabled(false);
        new Thread(() -> {
            boolean success = TntOverlayHelper.setOverlayDebugPropertyEnabled(enabled);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(
                        requireContext(),
                        success ? "已写入 TNT 调试选项" : "TNT 调试选项写入失败",
                        Toast.LENGTH_LONG).show();
                updateBackupTntSection();
            });
        }, "TNTAnywhereOverlayDebug").start();
    }

    private void showTntOverlaySettingsDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_tnt_overlay_settings, null);
        Spinner presetSpinner = dialogView.findViewById(R.id.tntOverlayPresetSpinner);
        EditText widthEditText = dialogView.findViewById(R.id.tntOverlayWidthEditText);
        EditText heightEditText = dialogView.findViewById(R.id.tntOverlayHeightEditText);
        EditText dpiEditText = dialogView.findViewById(R.id.tntOverlayDpiEditText);

        List<String> presetLabels = new ArrayList<>();
        for (TntOverlayPreset preset : TNT_OVERLAY_PRESETS) {
            presetLabels.add(preset.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                presetLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);

        int currentWidth = Pref.getTntOverlayWidth();
        int currentHeight = Pref.getTntOverlayHeight();
        int currentDpi = Pref.getTntOverlayDpi();
        widthEditText.setText(String.valueOf(currentWidth));
        heightEditText.setText(String.valueOf(currentHeight));
        dpiEditText.setText(String.valueOf(currentDpi));
        presetSpinner.setSelection(findMatchingTntOverlayPresetIndex(currentWidth, currentHeight, currentDpi));
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TntOverlayPreset preset = TNT_OVERLAY_PRESETS[position];
                if (!preset.custom) {
                    widthEditText.setText(String.valueOf(preset.width));
                    heightEditText.setText(String.valueOf(preset.height));
                    dpiEditText.setText(String.valueOf(preset.dpi));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setTitle("TNT 显示分辨率")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    preferences.edit()
                            .putInt(Pref.KEY_TNT_OVERLAY_WIDTH, parseClampedInt(widthEditText, 1920, 100, 8192))
                            .putInt(Pref.KEY_TNT_OVERLAY_HEIGHT, parseClampedInt(heightEditText, 1080, 100, 8192))
                            .putInt(Pref.KEY_TNT_OVERLAY_DPI, parseClampedInt(dpiEditText, 216, 72, 640))
                            .apply();
                    Toast.makeText(requireContext(), "TNT 显示参数已保存", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("重置", (dialog, which) -> {
                    preferences.edit()
                            .putInt(Pref.KEY_TNT_OVERLAY_WIDTH, 1920)
                            .putInt(Pref.KEY_TNT_OVERLAY_HEIGHT, 1080)
                            .putInt(Pref.KEY_TNT_OVERLAY_DPI, 216)
                            .apply();
                    Toast.makeText(requireContext(), "已恢复默认显示参数", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int findMatchingTntOverlayPresetIndex(int width, int height, int dpi) {
        for (int i = 0; i < TNT_OVERLAY_PRESETS.length; i++) {
            TntOverlayPreset preset = TNT_OVERLAY_PRESETS[i];
            if (!preset.custom && preset.width == width && preset.height == height && preset.dpi == dpi) {
                return i;
            }
        }
        return TNT_OVERLAY_PRESETS.length - 1;
    }

    private int parseClampedInt(EditText editText, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

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
    }
}
