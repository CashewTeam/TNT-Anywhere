package com.gitee.connect_screen;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import android.provider.Settings;
import android.view.Display;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.rikka.tools.refine.Refine;

public class InputDeviceListFragment extends Fragment {
    private List<Display> displayList;
    private Spinner spinnerDisplays;
    private Button btnBind;
    private RecyclerView rvExternalDevices;
    private RecyclerView rvInternalDevices;
    private CheckBox cbForceDesktop;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_input_device_list, container, false);
        
        cbForceDesktop = view.findViewById(R.id.cbForceDesktop);
        spinnerDisplays = view.findViewById(R.id.spinnerDisplays);
        btnBind = view.findViewById(R.id.btnBind);
        rvExternalDevices = view.findViewById(R.id.rvExternalDevices);
        rvInternalDevices = view.findViewById(R.id.rvInternalDevices);
        
        initializeDisplaySpinner();
        setupBindButton();
        setupDeviceLists();

        if (grantWriteSecureSettings()) {
            setupForceDesktopCheckbox();
//             Settings.Secure.putString(getActivity().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
//                     "com.gitee.connect_screen/.TouchpadAccessibilityService");
//             Settings.Secure.putString(getActivity().getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
        } else {
            cbForceDesktop.setVisibility(View.GONE);
        }
        
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

    private boolean grantWriteSecureSettings() {
        IPermissionManager permissionManager = ServiceUtils.getPermissionManager();
        UserHandle userHandle = Process.myUserHandle();
        UserHandleHidden userHandleHidden = Refine.unsafeCast(userHandle);
        String packageName = getActivity().getPackageName();
        try {
            permissionManager.grantRuntimePermission(
                    packageName,
                    "android.permission.WRITE_SECURE_SETTINGS",
                    "0", userHandleHidden.getIdentifier());
            State.log("成功授予 WRITE_SECURE_SETTINGS 权限");
            return true;
        } catch (Throwable e) {
            try {
                permissionManager.grantRuntimePermission(
                        packageName,
                        "android.permission.WRITE_SECURE_SETTINGS",
                        userHandleHidden.getIdentifier());
                State.log("成功授予 WRITE_SECURE_SETTINGS 权限");
                return true;
            } catch (Throwable e2) {
                State.log("授予权限失败: " + e2.getMessage());
            }
        }
        return false;
    }

    private void setupForceDesktopCheckbox() {
        // 读取当前设置
        boolean isForceDesktop = Settings.Global.getInt(requireContext().getContentResolver(),
                "force_desktop_mode_on_external_displays", 0) == 1;
        cbForceDesktop.setChecked(isForceDesktop);

        cbForceDesktop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Settings.Global.putInt(requireContext().getContentResolver(),
                        "force_desktop_mode_on_external_displays", isChecked ? 1 : 0);
            } catch (SecurityException e) {
                State.log("failed: " + e);
            }
        });
    }
}