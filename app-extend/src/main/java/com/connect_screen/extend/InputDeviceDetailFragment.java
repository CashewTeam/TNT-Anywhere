package com.connect_screen.extend;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.connect_screen.extend.job.BindInputToDisplay;
import com.connect_screen.extend.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InputDeviceDetailFragment extends Fragment {
    private static final String ARG_DEVICE_ID = "device_id";
    private InputDevice device;
    private List<Display> displayList;
    private Spinner spinnerDisplays;
    private Button btnBind;

    public static InputDeviceDetailFragment newInstance(int deviceId) {
        InputDeviceDetailFragment fragment = new InputDeviceDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DEVICE_ID, deviceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            int deviceId = getArguments().getInt(ARG_DEVICE_ID);
            device = InputDevice.getDevice(deviceId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input_device_detail, container, false);
        
        TextView tvDeviceName = view.findViewById(R.id.tvDeviceName);
        TextView tvDeviceDetails = view.findViewById(R.id.tvDeviceDetails);
        
        if (device != null) {
            tvDeviceName.setText(device.getName());
            String details = String.format(
                "设备ID：%d\n" +
                "产品ID：%d\n" +
                "供应商ID：%d\n" +
                "描述符：%s\n" +
                "是否外部设备：%s\n" +
                "支持的按键：%s",
                device.getId(),
                device.getProductId(),
                device.getVendorId(),
                device.getDescriptor(),
                device.isExternal() ? "是" : "否",
                getDeviceSources(device)
            );
            tvDeviceDetails.setText(details);
        }
        
        // 初始化新增的控件
        spinnerDisplays = view.findViewById(R.id.spinnerDisplays);
        btnBind = view.findViewById(R.id.btnBind);
        
        initializeDisplaySpinner();
        setupBindButton();
        
        return view;
    }

    private void initializeDisplaySpinner() {
        DisplayManager displayManager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        displayList = Arrays.asList(displays);

        List<String> displayNames = new ArrayList<>();
        for (Display display : displays) {
            displayNames.add("显示器 " + display.getDisplayId() + " (" + display.getName() + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDisplays.setAdapter(adapter);
    }

    private void setupBindButton() {
        btnBind.setOnClickListener(v -> {
            if (!ShizukuUtils.hasShizukuStarted()) {
                Toast.makeText(requireContext(), "需要安装 shizuku", Toast.LENGTH_SHORT).show();
                return;
            }
            int selectedPosition = spinnerDisplays.getSelectedItemPosition();
            if (selectedPosition != -1 && selectedPosition < displayList.size()) {
                Display selectedDisplay = displayList.get(selectedPosition);
                // 这里只绑定当前设备
                State.startNewJob(new BindInputToDisplay(device,selectedDisplay));
            }
        });
    }

    private String getDeviceSources(InputDevice device) {
        List<String> sources = new ArrayList<>();

        if ((device.getSources() & InputDevice.SOURCE_KEYBOARD) != 0) sources.add("键盘");
        if ((device.getSources() & InputDevice.SOURCE_DPAD) != 0) sources.add("D-pad");
        if ((device.getSources() & InputDevice.SOURCE_GAMEPAD) != 0) sources.add("游戏手柄");
        if ((device.getSources() & InputDevice.SOURCE_TOUCHSCREEN) != 0) sources.add("触摸屏");
        if ((device.getSources() & InputDevice.SOURCE_MOUSE) != 0) sources.add("鼠标");
        
        return TextUtils.join(", ", sources);
    }
} 