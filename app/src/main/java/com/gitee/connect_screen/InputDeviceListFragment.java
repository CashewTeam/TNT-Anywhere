package com.gitee.connect_screen;

import android.content.Context;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.hardware.input.InputManager;

public class InputDeviceListFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input_device_list, container, false);
        
        TextView placeholderText = view.findViewById(R.id.placeholderText);
        
        // 获取输入设备管理器
        InputManager inputManager = (InputManager) requireContext().getSystemService(Context.INPUT_SERVICE);
        
        // 获取所有输入设备ID
        int[] deviceIds = inputManager.getInputDeviceIds();
        
        // 构建设备列表字符串
        StringBuilder deviceList = new StringBuilder();
        deviceList.append("外接输入设备列表:\n\n");
        
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && device.isExternal()) {
                deviceList.append(device.getName()).append("\n");
            }
        }
        
        // 如果没有外接设备，显示提示信息
        if (deviceList.toString().equals("外接输入设备列表:\n\n")) {
            deviceList.append("未检测到外接输入设备");
        }
        
        placeholderText.setText(deviceList.toString());
        
        return view;
    }
} 