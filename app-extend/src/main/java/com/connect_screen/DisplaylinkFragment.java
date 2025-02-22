package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.connect_screen.mirror.job.DisplaylinkMonitor;
import com.connect_screen.mirror.job.VirtualDisplayArgs;
import com.connect_screen.mirror.job.ProjectViaDisplaylink;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

public class DisplaylinkFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;
    private DisplaylinkState displaylinkState;
    private CheckBox rotatesWithContentCheckbox;
    private CheckBox skipMediaProjectionPermissionCheckbox;
    private CheckBox autoOpenLastAppCheckbox;
    private View frameRateLayout;
    private View launchAppButton;
    private EditText dpiInput;

    public static DisplaylinkFragment newInstance() {
        DisplaylinkFragment fragment = new DisplaylinkFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplaylinkPref.load(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            DisplaylinkMonitor.handleDisplaylink(usbDevice);
        }
        device = usbManager.getDeviceList().get(State.displaylinkDeviceName);
        if (device == null) {
            TextView textView = new TextView(getActivity());
            textView.setText("未找到 Displaylink 设备。USB 2.0 的手机需要插入 Displaylink 扩展坞才能接显示器。");
            return textView;
        }
        displaylinkState = State.displaylinkState;
        displaylinkState.device = device;
        View view = inflater.inflate(R.layout.fragment_displaylink, container, false);
        rotatesWithContentCheckbox = view.findViewById(R.id.rotatesWithContentCheckbox);
        skipMediaProjectionPermissionCheckbox = view.findViewById(R.id.skipMediaProjectionPermissionCheckbox);
        autoOpenLastAppCheckbox = view.findViewById(R.id.autoOpenLastAppCheckbox);
        launchAppButton = view.findViewById(R.id.launch_app_button);

        TextView detailContent = view.findViewById(R.id.detailContent);
        Button mirrorViaDisplaylinkButton = view.findViewById(R.id.mirrorViaDisplaylinkButton);
        EditText monitorWidthInput = view.findViewById(R.id.displayWidthInput);
        EditText monitorHeightInput = view.findViewById(R.id.monitorHeightInput);

        frameRateLayout = view.findViewById(R.id.frameRateLayout);

        // 获取主屏幕尺寸
        WindowManager windowManager = (WindowManager) requireActivity().getSystemService(Context.WINDOW_SERVICE);

        Display defaultDisplay = windowManager.getDefaultDisplay();
        DisplayMetrics defaultDisplayMetrics = new DisplayMetrics();
        defaultDisplay.getRealMetrics(defaultDisplayMetrics);

        // 监听输入框变化并更新 usbState
        TextWatcher dimensionsWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    DisplaylinkPref.monitorWidth = Integer.parseInt(monitorWidthInput.getText().toString());
                    DisplaylinkPref.monitorHeight = Integer.parseInt(monitorHeightInput.getText().toString());
                    updateView();
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
        
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

        sb.append("Native Driver: ").append(displaylinkState.nativeDriver != null ? "已连接" : "未连接").append("\n");
        if (DisplaylinkPref.monitorWidth != 0) {
            monitorWidthInput.setText(String.valueOf(DisplaylinkPref.monitorWidth));
        } else if (displaylinkState.monitorInfo != null) {
            monitorWidthInput.setText(String.valueOf(displaylinkState.monitorInfo.a[0].width));
        } else {
            monitorWidthInput.setText("未连接");
        }
        if (DisplaylinkPref.monitorHeight != 0) {
            monitorHeightInput.setText(String.valueOf(DisplaylinkPref.monitorHeight));
        } else if (displaylinkState.monitorInfo != null) {
            monitorHeightInput.setText(String.valueOf(displaylinkState.monitorInfo.a[0].height));
        } else {
            monitorHeightInput.setText("未连接");
        }
        if (displaylinkState.getVirtualDisplay() != null) {
            Display display = displaylinkState.getVirtualDisplay().getDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            sb.append("虚拟显示器: ").append(display.getDisplayId()).append(", 宽: ").append(metrics.widthPixels).append(", 高: ").append(metrics.heightPixels).append("\n");
        } else {
            sb.append("虚拟显示器: ").append("未连接").append("\n");
        }

        if (displaylinkState.frameDuration > 0) {
            sb.append("每帧耗时: ").append(String.format("%.2f", displaylinkState.frameDuration)).append("ms\n");
        }

        detailContent.setText(sb.toString());

        mirrorViaDisplaylinkButton.setOnClickListener(v -> {
            if (!ShizukuUtils.hasPermission()) {
                Toast.makeText(requireContext(), "Displaylink 单应用投屏需要 shizuku 权限", Toast.LENGTH_SHORT).show();
                return;
            }
            DisplaylinkPref.save(getContext());
            State.startNewJob(new ProjectViaDisplaylink(device, displaylinkState.virtualDisplayArgs, ProjectionMode.SINGLE_APP));
        });

        rotatesWithContentCheckbox.setChecked(DisplaylinkPref.rotatesWithContent);
        rotatesWithContentCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DisplaylinkPref.rotatesWithContent = isChecked;
        });

        skipMediaProjectionPermissionCheckbox.setChecked(DisplaylinkPref.skipMediaProjectionPermission);
        skipMediaProjectionPermissionCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DisplaylinkPref.skipMediaProjectionPermission = isChecked;
        });

        autoOpenLastAppCheckbox.setChecked(DisplaylinkPref.autoOpenLastApp);
        autoOpenLastAppCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DisplaylinkPref.autoOpenLastApp = isChecked;
        });

        EditText frameRateInput = view.findViewById(R.id.frameRateInput);
        frameRateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int frameRate = Integer.parseInt(s.toString());
                    DisplaylinkPref.refreshRate = frameRate;
                    updateView();
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        
        // 如果 usbState 中已有帧率值，则显示在输入框中
        if (DisplaylinkPref.refreshRate > 0) {
            frameRateInput.setText(String.valueOf(DisplaylinkPref.refreshRate));
        }

        // 添加查看虚拟显示器按钮
        Button viewVirtualDisplayButton = view.findViewById(R.id.view_virtual_display_button);
        if (displaylinkState.getVirtualDisplay() != null) {
            viewVirtualDisplayButton.setVisibility(View.VISIBLE);
            viewVirtualDisplayButton.setOnClickListener(v -> {
                State.breadcrumbManager.pushBreadcrumb("虚拟显示器", () ->
                    DisplayDetailFragment.newInstance(displaylinkState.getVirtualDisplay().getDisplay().getDisplayId())
                );
            });
            launchAppButton.setOnClickListener(v -> {
                LauncherActivity.start(getContext(), displaylinkState.getVirtualDisplay().getDisplay().getDisplayId());
            });
        } else {
            viewVirtualDisplayButton.setVisibility(View.GONE);
        }

        dpiInput = view.findViewById(R.id.dpiInput);
        dpiInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int dpi = Integer.parseInt(s.toString());
                    DisplaylinkPref.dpi = dpi;
                    updateView();
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        
        // 如果已有DPI值，则显示在输入框中
        if (DisplaylinkPref.dpi > 0) {
            dpiInput.setText(String.valueOf(DisplaylinkPref.dpi));
        }

        updateView();

        return view;
    }

    private void updateView() {
        if (displaylinkState == null) return;
        
        // 更新单应用模式相关视图
        rotatesWithContentCheckbox.setVisibility(View.VISIBLE);
        skipMediaProjectionPermissionCheckbox.setVisibility(View.VISIBLE);
        autoOpenLastAppCheckbox.setVisibility(View.VISIBLE);
        launchAppButton.setVisibility(View.VISIBLE);
        if (displaylinkState.getVirtualDisplay() == null) {
            launchAppButton.setVisibility(View.GONE);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ShizukuUtils.hasShizukuStarted()) {
            frameRateLayout.setVisibility(View.VISIBLE);
        }
        displaylinkState.virtualDisplayArgs = new VirtualDisplayArgs(
            "DisplayLink",
            DisplaylinkPref.monitorWidth,
            DisplaylinkPref.monitorHeight,
            DisplaylinkPref.refreshRate,
            DisplaylinkPref.dpi,
            DisplaylinkPref.rotatesWithContent
        );
    }

}