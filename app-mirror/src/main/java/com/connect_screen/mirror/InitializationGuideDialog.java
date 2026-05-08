package com.connect_screen.mirror;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.TntOverlayHelper;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public final class InitializationGuideDialog {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Activity activity;
    private AlertDialog dialog;
    private TextView shizukuStatus;
    private TextView rootStatus;
    private TextView tntDebugStatus;
    private Button shizukuButton;
    private Button rootButton;
    private Button doneButton;
    private SwitchCompat tntDebugSwitch;
    private boolean rootGranted;
    private boolean rootChecking;
    private boolean suppressSwitchCallback;

    private InitializationGuideDialog(Activity activity) {
        this.activity = activity;
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new InitializationGuideDialog(activity).showInternal();
    }

    private void showInternal() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, dp(10), padding, 0);

        TextView warning = new TextView(activity);
        warning.setText("TNT Shaker 涉及高级权限、SmartisanOS 私有 API 调用，且仍处于开发阶段，可能存在未知风险。请确认你理解这些权限用途后继续。");
        warning.setTextColor(0xFF5F6368);
        warning.setTextSize(14);
        warning.setLineSpacing(dp(2), 1.0f);
        content.addView(warning, matchWrapParams());

        content.addView(createStatusRow(
                "Shizuku 权限",
                "用于获取画面和注入控制事件。",
                true));
        content.addView(createStatusRow(
                "Root 权限",
                "用于无头 TNT 启动和开启 TNT 调试选项。",
                false));
        content.addView(createTntDebugRow());

        dialog = new AlertDialog.Builder(activity)
                .setTitle("初始化配置")
                .setView(content)
                .setNegativeButton("跳过", null)
                .setPositiveButton("完成", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button skipButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            doneButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (skipButton != null) {
                skipButton.setTextColor(0xFF80868B);
                skipButton.setOnClickListener(v -> finishSetup());
            }
            if (doneButton != null) {
                doneButton.setOnClickListener(v -> finishSetup());
            }
            refreshStatus();
            runRootCheck();
        });
        dialog.show();
    }

    private View createStatusRow(String title, String note, boolean shizukuRow) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(16), 0, 0);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(0xFF202124);
        titleView.setTextSize(16);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView statusView = new TextView(activity);
        statusView.setText("待检查");
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(statusView, wrapParams());

        Button actionButton = new Button(activity);
        actionButton.setText(shizukuRow ? "授权" : "检查");
        actionButton.setMinHeight(0);
        actionButton.setMinimumHeight(0);
        actionButton.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams buttonParams = wrapParams();
        buttonParams.setMarginStart(dp(8));
        header.addView(actionButton, buttonParams);

        TextView noteView = new TextView(activity);
        noteView.setText(note);
        noteView.setTextColor(0xFF6B7075);
        noteView.setTextSize(13);
        noteView.setPadding(0, dp(4), 0, 0);

        row.addView(header, matchWrapParams());
        row.addView(noteView, matchWrapParams());

        if (shizukuRow) {
            shizukuStatus = statusView;
            shizukuButton = actionButton;
            shizukuButton.setOnClickListener(v -> {
                State.startNewJob(new AcquireShizuku());
                MAIN_HANDLER.postDelayed(this::refreshStatus, 800);
                MAIN_HANDLER.postDelayed(this::refreshStatus, 2500);
            });
        } else {
            rootStatus = statusView;
            rootButton = actionButton;
            rootButton.setOnClickListener(v -> runRootCheck());
        }
        return row;
    }

    private View createTntDebugRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(16), 0, 0);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(activity);
        titleView.setText("TNT 调试选项");
        titleView.setTextColor(0xFF202124);
        titleView.setTextSize(16);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        tntDebugStatus = new TextView(activity);
        tntDebugStatus.setText("待检查");
        tntDebugStatus.setTextSize(14);
        header.addView(tntDebugStatus, wrapParams());

        tntDebugSwitch = new SwitchCompat(activity);
        tntDebugSwitch.setShowText(false);
        tntDebugSwitch.setMinWidth(dp(48));
        styleSwitch(tntDebugSwitch);
        LinearLayout.LayoutParams switchParams = wrapParams();
        switchParams.setMarginStart(dp(8));
        header.addView(tntDebugSwitch, switchParams);

        TextView noteView = new TextView(activity);
        noteView.setText("开关执行 setprop persist.easycast.show_overlay_display 1/0。修改后通常需要重启手机才会完全生效。");
        noteView.setTextColor(0xFF6B7075);
        noteView.setTextSize(13);
        noteView.setPadding(0, dp(4), 0, 0);

        row.addView(header, matchWrapParams());
        row.addView(noteView, matchWrapParams());

        tntDebugSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallback) {
                return;
            }
            setTntDebugEnabled(isChecked);
        });
        return row;
    }

    private void refreshStatus() {
        boolean shizukuGranted = ShizukuUtils.hasPermission();
        boolean tntDebugEnabled = TntOverlayHelper.isOverlayDebugPropertyEnabled();

        updateStatus(shizukuStatus, shizukuGranted ? "已授权" : "未授权", shizukuGranted);
        if (shizukuButton != null) {
            shizukuButton.setEnabled(!shizukuGranted);
        }

        if (rootChecking) {
            updateStatus(rootStatus, "检查中", false);
        } else {
            updateStatus(rootStatus, rootGranted ? "已授权" : "未授权", rootGranted);
        }

        updateStatus(tntDebugStatus, tntDebugEnabled ? "已开启" : "未开启", tntDebugEnabled);
        if (tntDebugSwitch != null) {
            suppressSwitchCallback = true;
            tntDebugSwitch.setChecked(tntDebugEnabled);
            tntDebugSwitch.setEnabled(rootGranted && !rootChecking);
            suppressSwitchCallback = false;
        }

        boolean allReady = shizukuGranted && rootGranted && tntDebugEnabled;
        if (doneButton != null) {
            doneButton.setEnabled(allReady);
        }
    }

    private void runRootCheck() {
        if (rootChecking) {
            return;
        }
        rootChecking = true;
        refreshStatus();
        new Thread(() -> {
            boolean hasRoot = TntOverlayHelper.hasRootAccess();
            MAIN_HANDLER.post(() -> {
                rootGranted = hasRoot;
                rootChecking = false;
                refreshStatus();
            });
        }, "TNTShakerRootCheck").start();
    }

    private void setTntDebugEnabled(boolean enabled) {
        if (!rootGranted) {
            Toast.makeText(activity, "需要先授予 Root 权限", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        tntDebugSwitch.setEnabled(false);
        tntDebugStatus.setText("设置中");
        new Thread(() -> {
            boolean success = TntOverlayHelper.setOverlayDebugPropertyEnabled(enabled);
            MAIN_HANDLER.post(() -> {
                Toast.makeText(activity,
                        success ? "已写入 TNT 调试选项，重启手机后完全生效" : "TNT 调试选项写入失败",
                        Toast.LENGTH_LONG).show();
                refreshStatus();
            });
        }, "TNTShakerOverlayProp").start();
    }

    private void finishSetup() {
        activity.getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Activity.MODE_PRIVATE)
                .edit()
                .putBoolean(Pref.KEY_INITIAL_SETUP_COMPLETE, true)
                .apply();
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private void updateStatus(TextView view, String text, boolean ok) {
        if (view == null) {
            return;
        }
        view.setText(text);
        view.setTextColor(ok ? 0xFF2E7D32 : 0xFF80868B);
    }

    private void styleSwitch(SwitchCompat switchCompat) {
        int[][] states = new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        switchCompat.setThumbTintList(new ColorStateList(states, new int[]{
                0xFFD0D4D8,
                0xFF4CAF50,
                0xFF9EA4AA
        }));
        switchCompat.setTrackTintList(new ColorStateList(states, new int[]{
                0x223F454A,
                0x664CAF50,
                0x553F454A
        }));
        CompoundButtonCompat.setButtonTintList(switchCompat, null);
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
