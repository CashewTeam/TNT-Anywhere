package com.gitee.connect_screen;

import android.content.Context;
import android.os.Bundle;
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

import android.hardware.input.InputManager;
import android.hardware.display.DisplayManager;

import com.gitee.connect_screen.job.BindAllExternalInputToDisplay;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InputDeviceListFragment extends Fragment {
    private List<Display> displayList;
    private Spinner spinnerDisplays;
    private Button btnBind;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input_device_list, container, false);
        
        TextView placeholderText = view.findViewById(R.id.placeholderText);
        spinnerDisplays = view.findViewById(R.id.spinnerDisplays);
        btnBind = view.findViewById(R.id.btnBind);
        
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
                State.startNewJob(new BindAllExternalInputToDisplay(selectedDisplay.getDisplayId()));
            }
        });
    }
} 