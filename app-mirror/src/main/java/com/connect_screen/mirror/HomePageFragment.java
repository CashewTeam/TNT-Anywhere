package com.connect_screen.mirror;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.connect_screen.mirror.job.CreateVirtualDisplay;
import com.connect_screen.mirror.job.SunshineServer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Set;

public class HomePageFragment extends Fragment {
    private TextView screenOffButton;
    private TextView tntDesktopButton;
    private TextView ipAddressText;
    private TextView serviceStatusPrimary;
    private TextView serviceStatusSecondary;
    private View serviceButton;
    private View simpleDebugPanel;
    private TextView debugCodecValue;
    private TextView debugResolutionValue;
    private TextView debugBitrateValue;
    private TextView debugInputFpsValue;
    private TextView debugOutputFpsValue;
    private TextView debugPingValue;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_page, container, false);
        screenOffButton = view.findViewById(R.id.screenOffButton);
        tntDesktopButton = view.findViewById(R.id.tntDesktopButton);
        ipAddressText = view.findViewById(R.id.ipAddressText);
        serviceStatusPrimary = view.findViewById(R.id.serviceStatusPrimary);
        serviceStatusSecondary = view.findViewById(R.id.serviceStatusSecondary);
        simpleDebugPanel = view.findViewById(R.id.simpleDebugPanel);
        debugCodecValue = view.findViewById(R.id.debugCodecValue);
        debugResolutionValue = view.findViewById(R.id.debugResolutionValue);
        debugBitrateValue = view.findViewById(R.id.debugBitrateValue);
        debugInputFpsValue = view.findViewById(R.id.debugInputFpsValue);
        debugOutputFpsValue = view.findViewById(R.id.debugOutputFpsValue);
        debugPingValue = view.findViewById(R.id.debugPingValue);

        view.findViewById(R.id.guideButton).setOnClickListener(v ->
                InitializationGuideDialog.show(requireActivity()));
        view.findViewById(R.id.darkModeButton).setOnClickListener(v -> toggleDarkMode());
        screenOffButton.setOnClickListener(v -> CreateVirtualDisplay.doPowerOffScreen(requireContext()));
        tntDesktopButton.setOnClickListener(v -> {
            MirrorMainActivity activity = getMainActivity();
            if (activity != null) {
                activity.toggleTntDesktop();
            }
        });
        view.findViewById(R.id.serviceButton).setOnClickListener(v -> {
            MirrorMainActivity activity = getMainActivity();
            if (activity != null) {
                activity.startSunshineServiceWithPreflight();
            }
        });
        serviceButton = view.findViewById(R.id.serviceButton);
        serviceButton.post(() -> applyServiceButtonSize(serviceButton));
        setServiceButtonStoppedStyle();
        updateScreenOffButton(false);
        simpleDebugPanel.setOnClickListener(v -> showDebugLogPanel());

        refreshIpAddress();
        updateDebugInfo(State.streamingDebugInfo.getValue());
        return view;
    }

    private void applyServiceButtonSize(View serviceButton) {
        if (serviceButton == null || getContext() == null) {
            return;
        }
        int diameter = getResources().getDisplayMetrics().widthPixels - dp(78);
        ViewGroup.LayoutParams params = serviceButton.getLayoutParams();
        params.width = diameter;
        params.height = diameter;
        serviceButton.setLayoutParams(params);
    }

    public void updateUiState(MirrorUiState state) {
        if (state == null || serviceStatusPrimary == null) {
            return;
        }
        SunshineService.LifecycleState lifecycleState = SunshineService.getLifecycleState();
        boolean connected = SunshineServer.isMoonlightSessionActive();
        switch (lifecycleState) {
            case STARTING:
                serviceStatusPrimary.setText("启动中");
                serviceStatusSecondary.setText("Sunshine 服务启动中");
                setServiceButtonRunningStyle();
                break;
            case STOPPING:
                serviceStatusPrimary.setText("关闭中");
                serviceStatusSecondary.setText("Sunshine 服务关闭中");
                setServiceButtonRunningStyle();
                break;
            case RUNNING:
                if (connected) {
                    serviceStatusPrimary.setText("已连接");
                    serviceStatusSecondary.setText("已连接到客户端");
                } else {
                    serviceStatusPrimary.setText("等待连接");
                    serviceStatusSecondary.setText("Sunshine 服务已启动，等待连接中");
                }
                setServiceButtonRunningStyle();
                break;
            case STOPPED:
            default:
                serviceStatusPrimary.setText("启动服务");
                serviceStatusSecondary.setText("Sunshine 服务未启动");
                setServiceButtonStoppedStyle();
                break;
        }
        updateScreenOffButton(state.screenOffBtnEnabled);
        tntDesktopButton.setText(state.tntDesktopButtonText == null ? "TNT" : state.tntDesktopButtonText.replace("开启 ", "").replace("关闭 ", ""));
        refreshIpAddress();
    }

    private void setServiceButtonRunningStyle() {
        if (serviceButton != null) {
            serviceButton.setBackgroundResource(R.drawable.bg_ui_service_button);
        }
    }

    private void setServiceButtonStoppedStyle() {
        if (serviceButton != null) {
            serviceButton.setBackgroundResource(R.drawable.bg_ui_service_button_stopped);
        }
    }

    private void updateScreenOffButton(boolean enabled) {
        if (screenOffButton == null) {
            return;
        }
        screenOffButton.setVisibility(View.VISIBLE);
        screenOffButton.setEnabled(enabled);
        screenOffButton.setAlpha(enabled ? 1.0f : 0.38f);
    }

    public void updateDebugInfo(String info) {
        if (simpleDebugPanel == null) {
            return;
        }
        updateSimpleDebugPanel(info);
    }

    public void updateLogs() {
        // The compact panel is fed by streamingDebugInfo; the full log dialog reads State.logs on demand.
    }

    private void refreshIpAddress() {
        if (ipAddressText == null || getContext() == null) {
            return;
        }
        try {
            Set<String> ips = SunshineService.getAllWifiIpAddresses(requireContext());
            ipAddressText.setText(ips.isEmpty() ? "IP：--" : "IP：" + TextUtils.join(" / ", ips));
        } catch (Throwable e) {
            ipAddressText.setText("IP：--");
        }
    }

    private void updateSimpleDebugPanel(String info) {
        if (info == null || info.trim().isEmpty() || "串流未启动".equals(info.trim())) {
            setSimpleDebugValues("--", "--", "--", "--", "--", "--");
            return;
        }
        String codec = valueAfterPrefix(info, "Codec:");
        String resolution = valueAfterPrefix(info, "Size:");
        String bitrate = valueAfterPrefix(info, "Encoded bitrate:");
        if (bitrate.isEmpty()) {
            bitrate = valueAfterPrefix(info, "Target bitrate:");
        }
        String inputFps = extractInputFps(info);
        String outputFps = valueAfterPrefix(info, "Output FPS:");
        String ping = valueAfterPrefix(info, "Ping:");
        setSimpleDebugValues(
                emptyAsDash(codec),
                emptyAsDash(resolution),
                emptyAsDash(bitrate),
                emptyAsDash(inputFps),
                emptyAsDash(outputFps),
                emptyAsDash(ping));
    }

    private void setSimpleDebugValues(String codec,
                                      String resolution,
                                      String bitrate,
                                      String inputFps,
                                      String outputFps,
                                      String ping) {
        setTextIfReady(debugCodecValue, codec);
        setTextIfReady(debugResolutionValue, resolution);
        setTextIfReady(debugBitrateValue, bitrate);
        setTextIfReady(debugInputFpsValue, inputFps);
        setTextIfReady(debugOutputFpsValue, outputFps);
        setTextIfReady(debugPingValue, ping);
    }

    private void setTextIfReady(TextView textView, String value) {
        if (textView != null) {
            textView.setText(value);
        }
    }

    private String buildDebugPanelSummaryText(String info) {
        if (info == null || info.trim().isEmpty() || "串流未启动".equals(info.trim())) {
            return "编码状态：未启动\n编码器：--\n分辨率：--\n帧率：输入 -- / 输出 --\n码率：--\nPing：--";
        }

        String codec = valueAfterPrefix(info, "Codec:");
        String level = valueAfterPrefix(info, "H.264 level:");
        String size = valueAfterPrefix(info, "Size:");
        String status = valueAfterPrefix(info, "Status:");
        String clientAndEncoderFps = valueAfterPrefix(info, "Client FPS:");
        String inputFps = extractInputFps(info);
        String outputFps = valueAfterPrefix(info, "Output FPS:");
        String targetBitrate = valueAfterPrefix(info, "Target bitrate:");
        String encodedBitrate = valueAfterPrefix(info, "Encoded bitrate:");
        String ping = valueAfterPrefix(info, "Ping:");
        String priority = valueAfterPrefix(info, "Priority hint:");
        String audio = valueAfterPrefix(info, "Audio:");
        String color = valueAfterPrefix(info, "Color:");
        String outputGap = valueAfterPrefix(info, "Output gap max:");
        String queue = valueAfterPrefix(info, "Queue:");
        String nativeCost = valueAfterPrefix(info, "Avg native cost:");
        String framePacer = findLineStartingWith(info, "Frame pacer:");

        StringBuilder builder = new StringBuilder();
        builder.append("编码状态：").append(emptyAsDash(status)).append('\n');
        builder.append("编码器：").append(emptyAsDash(codec));
        if (!level.isEmpty() && !"-".equals(level)) {
            builder.append(" / Level ").append(level);
        }
        builder.append('\n');
        builder.append("分辨率：").append(emptyAsDash(size)).append('\n');
        builder.append("请求/编码帧率：").append(emptyAsDash(clientAndEncoderFps)).append('\n');
        builder.append("输入/输出帧率：").append(emptyAsDash(inputFps))
                .append(" / ").append(emptyAsDash(outputFps)).append('\n');
        builder.append("码率：").append(emptyAsDash(encodedBitrate))
                .append(" / 目标 ").append(emptyAsDash(targetBitrate)).append('\n');
        builder.append("Ping：").append(emptyAsDash(ping));
        appendIfPresent(builder, "优先级", priority);
        appendIfPresent(builder, "音频", audio);
        appendIfPresent(builder, "色彩", color);
        appendIfPresent(builder, "输出间隔", outputGap);
        appendIfPresent(builder, "队列", queue);
        appendIfPresent(builder, "Native", nativeCost);
        appendIfPresent(builder, "Frame pacer", stripPrefix(framePacer, "Frame pacer:"));
        return builder.toString();
    }

    private String valueAfterPrefix(String info, String prefix) {
        for (String line : info.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private String extractInputFps(String info) {
        for (String line : info.split("\\n")) {
            String trimmed = line.trim();
            int sourceIndex = trimmed.indexOf("source=");
            if (sourceIndex >= 0) {
                int end = trimmed.indexOf(' ', sourceIndex);
                return end > sourceIndex
                        ? trimmed.substring(sourceIndex + 7, end)
                        : trimmed.substring(sourceIndex + 7);
            }
            if (trimmed.startsWith("Client FPS:")) {
                continue;
            }
            String inputFps = valueAfterKnownPrefix(trimmed, "Input FPS:");
            if (!inputFps.isEmpty()) {
                return inputFps;
            }
            String sourceFps = valueAfterKnownPrefix(trimmed, "Source FPS:");
            if (!sourceFps.isEmpty()) {
                return sourceFps;
            }
        }
        return "";
    }

    private String valueAfterKnownPrefix(String line, String prefix) {
        return line.startsWith(prefix) ? line.substring(prefix.length()).trim() : "";
    }

    private String findLineStartingWith(String info, String prefix) {
        for (String line : info.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed;
            }
        }
        return "";
    }

    private String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()).trim() : value;
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        builder.append('\n').append(label).append("：").append(value.trim());
    }

    private String emptyAsDash(String value) {
        return value == null || value.isEmpty() ? "--" : value;
    }

    private void showDebugLogPanel() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_debug_log_panel, null);
        TextView summary = content.findViewById(R.id.debugSummaryText);
        summary.setText(buildDebugPanelSummaryText(State.streamingDebugInfo.getValue()));
        RecyclerView recyclerView = content.findViewById(R.id.debugLogRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        LogAdapter adapter = new LogAdapter(State.logs);
        recyclerView.setAdapter(adapter);
        content.findViewById(R.id.viewRecentHandshakeButton).setOnClickListener(v ->
                DebugDialogs.showLastMoonlightHandshakeDialog(requireContext()));
        content.findViewById(R.id.viewRecentControlInputButton).setOnClickListener(v ->
                DebugDialogs.showLastMoonlightControlInputDialog(requireContext()));
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_TntAnywhere_MaterialAlertDialog)
                .setView(content)
                .setPositiveButton("完成", null)
                .create();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable refreshRunnable = new Runnable() {
            private int lastLogCount = -1;

            @Override
            public void run() {
                if (!isAdded() || !dialog.isShowing()) {
                    return;
                }
                summary.setText(buildDebugPanelSummaryText(State.streamingDebugInfo.getValue()));
                if (lastLogCount != State.logs.size()) {
                    lastLogCount = State.logs.size();
                    adapter.notifyDataSetChanged();
                    recyclerView.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
                }
                handler.postDelayed(this, 500);
            }
        };
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            handler.post(refreshRunnable);
        });
        dialog.setOnDismissListener(d -> handler.removeCallbacks(refreshRunnable));
        dialog.show();
    }

    private void toggleDarkMode() {
        boolean next = !Pref.getDarkMode();
        if (Pref.getPreferences() != null) {
            Pref.getPreferences().edit().putBoolean(Pref.KEY_DARK_MODE, next).apply();
        }
        AppCompatDelegate.setDefaultNightMode(next
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private MirrorMainActivity getMainActivity() {
        return getActivity() instanceof MirrorMainActivity ? (MirrorMainActivity) getActivity() : null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
