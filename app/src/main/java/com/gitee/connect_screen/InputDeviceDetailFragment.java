package com.gitee.connect_screen;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class InputDeviceDetailFragment extends Fragment {
    private static final String ARG_DEVICE_ID = "device_id";
    private InputDevice device;

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
        
        return view;
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