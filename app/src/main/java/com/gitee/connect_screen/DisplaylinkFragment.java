package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.MirrorArgs;
import com.gitee.connect_screen.job.MirrorViaDisplaylink;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.Arrays;

public class DisplaylinkFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;
    private UsbState usbState;
    private View sourceScreenSizeLayout;
    private View aspectRatioExplanation;
    private CheckBox rotatesWithContentCheckbox;
    private View frameRateLayout;

    public static DisplaylinkFragment newInstance(UsbDevice device) {
        DisplaylinkFragment fragment = new DisplaylinkFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            device = getArguments().getParcelable(ARG_DEVICE);
            usbState = State.getOrCreateUsbState(device);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_displaylink, container, false);

        rotatesWithContentCheckbox = view.findViewById(R.id.rotatesWithContentCheckbox);

        TextView detailContent = view.findViewById(R.id.detailContent);
        Button mirrorViaDisplaylinkButton = view.findViewById(R.id.mirrorViaDisplaylinkButton);
        EditText monitorWidthInput = view.findViewById(R.id.displayWidthInput);
        EditText monitorHeightInput = view.findViewById(R.id.monitorHeightInput);

        sourceScreenSizeLayout = view.findViewById(R.id.sourceScreenSizeLayout);
        EditText sourceWidthInput = view.findViewById(R.id.sourceWidthInput);
        EditText sourceHeightInput = view.findViewById(R.id.sourceHeightInput);
        aspectRatioExplanation = view.findViewById(R.id.aspectRatioExplanation);
        frameRateLayout = view.findViewById(R.id.frameRateLayout);

        Spinner projectionModeSpinner = view.findViewById(R.id.projectionModeSpinner);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            Arrays.stream(ProjectionMode.values())
                  .map(mode -> {
                      switch(mode) {
                          case MIRROR: return "普通镜像";
                          case MIRROR_AND_CROP_16_9: return "16:9裁剪";
                          case SINGLE_APP: return "投屏单个应用（需要 shizuku 授权）";
                          default: return mode.name();
                      }
                  })
                  .toArray(String[]::new)
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        projectionModeSpinner.setAdapter(spinnerAdapter);
        
        if (usbState.projectionMode != null) {
            projectionModeSpinner.setSelection(usbState.projectionMode.ordinal());
        }


        projectionModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                usbState.projectionMode = ProjectionMode.values()[position];
                updateView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // 获取主屏幕尺寸
        WindowManager windowManager = (WindowManager) requireActivity().getSystemService(Context.WINDOW_SERVICE);
        WindowMetrics windowMetrics = windowManager.getMaximumWindowMetrics();
        int pxWidth = windowMetrics.getBounds().width();
        int pxHeight = windowMetrics.getBounds().height();

        if (usbState.sourceWidth != 0) {
            sourceWidthInput.setText(String.valueOf(usbState.sourceWidth));
        } else {
            sourceWidthInput.setText(String.valueOf(pxWidth));
        }
        if (usbState.sourceHeight != 0) {
            sourceHeightInput.setText(String.valueOf(usbState.sourceHeight));
        } else {
            sourceHeightInput.setText(String.valueOf(pxHeight));
        }

        // 监听输入框变化并更新 usbState
        TextWatcher dimensionsWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    usbState.sourceWidth = Integer.parseInt(sourceWidthInput.getText().toString());
                    usbState.sourceHeight = Integer.parseInt(sourceHeightInput.getText().toString());
                    usbState.monitorWidth = Integer.parseInt(monitorWidthInput.getText().toString());
                    usbState.monitorHeight = Integer.parseInt(monitorHeightInput.getText().toString());
                    updateView();
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
        
        sourceWidthInput.addTextChangedListener(dimensionsWatcher);
        sourceHeightInput.addTextChangedListener(dimensionsWatcher);
        monitorWidthInput.addTextChangedListener(dimensionsWatcher);
        monitorHeightInput.addTextChangedListener(dimensionsWatcher);

        // 添加分辨率预设选项
        Spinner resolutionPresetSpinner = view.findViewById(R.id.resolutionPresetSpinner);
        String[] resolutionPresets = new String[]{"快捷设置", "1080p", "1440p", "2160p", "ipad4"};
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            resolutionPresets
        );
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionPresetSpinner.setAdapter(resolutionAdapter);
        
        resolutionPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 1: // 1080p
                        monitorWidthInput.setText("1920");
                        monitorHeightInput.setText("1080");
                        break;
                    case 2: // 1440p
                        monitorWidthInput.setText("2560");
                        monitorHeightInput.setText("1440");
                        break;
                    case 3: // 2160p
                        monitorWidthInput.setText("3840");
                        monitorHeightInput.setText("2160");
                        break;
                    case 4: // ipad4
                        monitorWidthInput.setText("2048");
                        monitorHeightInput.setText("1536");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("设备名称: ").append(device.getDeviceName()).append("\n");
        sb.append("制造商: ").append(device.getManufacturerName() != null ? device.getManufacturerName() : "未知").append("\n");
        sb.append("产品名称: ").append(device.getProductName() != null ? device.getProductName() : "未知").append("\n");
        sb.append("厂商ID: ").append(device.getVendorId()).append("\n");
        sb.append("产品ID: ").append(device.getProductId()).append("\n");
        sb.append("设备类: ").append(device.getDeviceClass()).append("\n");
        sb.append("设备子类: ").append(device.getDeviceSubclass()).append("\n");
        sb.append("协议: ").append(device.getDeviceProtocol()).append("\n");

        sb.append("Native Driver: ").append(usbState.nativeDriver != null ? "已连接" : "未连接").append("\n");
        if (usbState.monitorWidth != 0) {
            monitorWidthInput.setText(String.valueOf(usbState.monitorWidth));
        } else if (usbState.monitorInfo != null) {
            monitorWidthInput.setText(String.valueOf(usbState.monitorInfo.a[0].width));
        } else {
            monitorWidthInput.setText("未连接");
        }
        if (usbState.monitorHeight != 0) {
            monitorHeightInput.setText(String.valueOf(usbState.monitorHeight));
        } else if (usbState.monitorInfo != null) {
            monitorHeightInput.setText(String.valueOf(usbState.monitorInfo.a[0].height));
        } else {
            monitorHeightInput.setText("未连接");
        }
        if (usbState.getVirtualDisplay() != null) {
            Display display = usbState.getVirtualDisplay().getDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            sb.append("虚拟显示器: ").append(display.getDisplayId()).append(", 宽: ").append(metrics.widthPixels).append(", 高: ").append(metrics.heightPixels).append("\n");
        } else {
            sb.append("虚拟显示器: ").append("未连接").append("\n");
        }
        sb.append("总发送帧数: ").append(usbState.frameCounter).append("\n");
        sb.append("最近发送帧状态码: ").append(Arrays.toString(usbState.recentPostFrameResultCodes)).append("\n");

        detailContent.setText(sb.toString());

        mirrorViaDisplaylinkButton.setOnClickListener(v -> {
            State.startNewJob(new MirrorViaDisplaylink(device, usbState.mirrorArgs));
        });

        rotatesWithContentCheckbox.setChecked(usbState.rotatesWithContent);
        rotatesWithContentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            usbState.rotatesWithContent = isChecked;
        });

        EditText frameRateInput = view.findViewById(R.id.frameRateInput);
        frameRateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int frameRate = Integer.parseInt(s.toString());
                    usbState.refreshRate = frameRate;
                    updateView();
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        
        // 如果 usbState 中已有帧率值，则显示在输入框中
        if (usbState.refreshRate > 0) {
            frameRateInput.setText(String.valueOf(usbState.refreshRate));
        }

        updateView();

        return view;
    }

    private void updateView() {
        if (usbState == null) return;
        
        // 更新16:9模式相关视图
        boolean is16_9Mode = usbState.projectionMode == ProjectionMode.MIRROR_AND_CROP_16_9;
        sourceScreenSizeLayout.setVisibility(is16_9Mode ? View.VISIBLE : View.GONE);
        
        // 更新单应用模式相关视图
        boolean isSingleAppMode = usbState.projectionMode == ProjectionMode.SINGLE_APP;
        rotatesWithContentCheckbox.setVisibility(isSingleAppMode ? View.VISIBLE : View.GONE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ShizukuUtils.hasShizukuStarted()) {
            frameRateLayout.setVisibility(View.VISIBLE);
        }

            // 如果在16:9模式下，更新裁剪说明
        if (is16_9Mode) {
            try {
                int sourceWidth = usbState.sourceWidth;
                int sourceHeight = usbState.sourceHeight;
                int monitorWidth = usbState.monitorWidth;
                int monitorHeight = usbState.monitorHeight;
                int maxSourceDim = Math.max(sourceWidth, sourceHeight);
                int minSourceDim = Math.min(sourceWidth, sourceHeight);
                if (minSourceDim == 0) {
                    throw new NumberFormatException();
                }
                int virtualDisplayWidth = monitorHeight * maxSourceDim / minSourceDim;

                StringBuilder explanation = new StringBuilder();
                explanation.append("16:9裁剪计算过程：\n");
                explanation.append("1. 主屏尺寸：").append(sourceWidth).append("x").append(sourceHeight).append("\n");
                explanation.append("2. 显示器尺寸：").append(monitorWidth).append("x").append(monitorHeight).append("\n");
                explanation.append("3. 虚拟屏的高度固定为显示器的高度：").append(monitorHeight).append("\n");
                explanation.append("4. 根据高度计算虚拟屏的宽度：").append(virtualDisplayWidth).append("\n");
                explanation.append("5. 左右会裁切的画面宽度：").append((virtualDisplayWidth - monitorWidth) / 2).append("\n");

                usbState.mirrorArgs = new MirrorArgs(monitorWidth, monitorHeight, virtualDisplayWidth, usbState.refreshRate);
                ((TextView) aspectRatioExplanation).setText(explanation.toString());
            } catch (NumberFormatException e) {
                // 忽略无效输入
            }
        } else {
            usbState.mirrorArgs = new MirrorArgs(usbState.monitorWidth, usbState.monitorHeight, usbState.monitorWidth, usbState.refreshRate);
        }
        aspectRatioExplanation.setVisibility(is16_9Mode ? View.VISIBLE : View.GONE);
    }

}