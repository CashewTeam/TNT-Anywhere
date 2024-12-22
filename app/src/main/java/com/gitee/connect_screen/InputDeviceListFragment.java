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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private RecyclerView rvExternalDevices;
    private RecyclerView rvInternalDevices;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input_device_list, container, false);
        
        spinnerDisplays = view.findViewById(R.id.spinnerDisplays);
        btnBind = view.findViewById(R.id.btnBind);
        rvExternalDevices = view.findViewById(R.id.rvExternalDevices);
        rvInternalDevices = view.findViewById(R.id.rvInternalDevices);
        
        initializeDisplaySpinner();
        setupBindButton();
        setupDeviceLists();
        
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

    private void setupDeviceLists() {
        InputManager inputManager = (InputManager) requireContext().getSystemService(Context.INPUT_SERVICE);
        int[] deviceIds = inputManager.getInputDeviceIds();
        
        List<InputDevice> externalDevices = new ArrayList<>();
        List<InputDevice> internalDevices = new ArrayList<>();
        
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null) {
                if (device.isExternal()) {
                    externalDevices.add(device);
                } else {
                    internalDevices.add(device);
                }
            }
        }

        rvExternalDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvInternalDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        DeviceAdapter externalAdapter = new DeviceAdapter(externalDevices, this::showDeviceDetails);
        DeviceAdapter internalAdapter = new DeviceAdapter(internalDevices, this::showDeviceDetails);
        
        rvExternalDevices.setAdapter(externalAdapter);
        rvInternalDevices.setAdapter(internalAdapter);
    }

    private void showDeviceDetails(InputDevice device) {
        State.breadcrumbManager.pushBreadcrumb(device.getName(), () ->
        InputDeviceDetailFragment.newInstance(device.getId())
        );
    }
} 