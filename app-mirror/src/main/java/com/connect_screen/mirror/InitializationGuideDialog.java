package com.connect_screen.mirror;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public final class InitializationGuideDialog {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Activity activity;
    private AlertDialog dialog;
    private TextView shizukuStatus;
    private TextView recordAudioStatus;
    private Button shizukuButton;
    private Button recordAudioButton;
    private Button doneButton;

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
        warning.setText("TNT Anywhere 涉及高级权限、SmartisanOS 私有 API 调用，且仍处于开发阶段，可能存在未知风险。请确认你理解这些权限用途后继续。");
        warning.setTextColor(0xFF5F6368);
        warning.setTextSize(14);
        warning.setLineSpacing(dp(2), 1.0f);
        content.addView(warning, matchWrapParams());

        content.addView(createStatusRow(
                "Shizuku 权限",
                "用于获取画面和注入控制事件。",
                "授权",
                true));
        content.addView(createStatusRow(
                "录音权限",
                "用于采集系统播放音频；未授权时仍可串流画面，但不会传输声音。",
                "授权",
                false));

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
        });
        dialog.show();
    }

    private View createStatusRow(String title, String note, String actionText, boolean shizukuRow) {
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
        actionButton.setText(actionText);
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
            recordAudioStatus = statusView;
            recordAudioButton = actionButton;
            recordAudioButton.setOnClickListener(v -> requestRecordAudioPermission());
        }
        return row;
    }

    private void refreshStatus() {
        boolean shizukuGranted = ShizukuUtils.hasPermission();
        boolean recordAudioGranted = isRecordAudioGranted();

        updateStatus(shizukuStatus, shizukuGranted ? "已授权" : "未授权", shizukuGranted);
        if (shizukuButton != null) {
            shizukuButton.setEnabled(!shizukuGranted);
        }

        updateStatus(recordAudioStatus, recordAudioGranted ? "已授权" : "未授权", recordAudioGranted);
        if (recordAudioButton != null) {
            recordAudioButton.setEnabled(!recordAudioGranted);
        }

        boolean allReady = shizukuGranted && recordAudioGranted;
        if (doneButton != null) {
            doneButton.setEnabled(allReady);
        }
    }

    private void requestRecordAudioPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if (isRecordAudioGranted()) {
            return;
        }
        activity.requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                MirrorMainActivity.REQUEST_RECORD_AUDIO_PERMISSION);
        MAIN_HANDLER.postDelayed(this::refreshStatus, 600);
        MAIN_HANDLER.postDelayed(this::refreshStatus, 1800);
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

    private boolean isRecordAudioGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
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
