package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.job.BindInputToDisplay;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UsbDeviceDetailFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;

    private TextView detailContent;
    private RecyclerView rvInterfaces;
    private Spinner spinnerDisplays;
    private Button btnBind;
    private List<Display> displayList;

    public static UsbDeviceDetailFragment newInstance(UsbDevice device) {
        UsbDeviceDetailFragment fragment = new UsbDeviceDetailFragment();
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
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usb_device_detail, container, false);
        detailContent = view.findViewById(R.id.detailContent);
        rvInterfaces = view.findViewById(R.id.rvInterfaces);
        spinnerDisplays = view.findViewById(R.id.spinnerDisplays);
        btnBind = view.findViewById(R.id.btnBind);
        
        if (device == null) {
            detailContent.setText("USB 设备未找到");
            return view;
        }

        showBasicInfo();
        setupInterfacesList();
        initializeDisplaySpinner();
        setupBindButton();
        return view;
    }
    
    private void showBasicInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("设备名称: ").append(device.getDeviceName()).append("\n");
        String manufacturerName = device.getManufacturerName();
        sb.append("制造商: ").append(manufacturerName != null ? manufacturerName : "未知").append("\n");
        String productName = device.getProductName();
        sb.append("产品名称: ").append(productName != null ? productName : "未知").append("\n");
        sb.append("厂商ID: ").append(device.getVendorId()).append("\n");
        sb.append("产品ID: ").append(device.getProductId()).append("\n");
        sb.append("设备类: ").append(device.getDeviceClass()).append("\n");
        sb.append("设备子类: ").append(device.getDeviceSubclass()).append("\n");
        sb.append("协议: ").append(device.getDeviceProtocol()).append("\n");
        sb.append("接口数量: ").append(device.getInterfaceCount()).append("\n\n");
        
        detailContent.setText(sb.toString());
        detailContent.setVisibility(View.VISIBLE);
    }
    
    private void setupInterfacesList() {
        List<UsbInterface> interfaceList = new ArrayList<>();
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            interfaceList.add(device.getInterface(i));
        }
        
        rvInterfaces.setLayoutManager(new LinearLayoutManager(getContext()));
        UsbInterfaceAdapter adapter = new UsbInterfaceAdapter(interfaceList, position -> {
            MainActivity activity = (MainActivity) getActivity();
            String interfaceText = "#" + position;
            activity.pushBreadcrumb(interfaceText,
                () -> UsbInterfaceDetailFragment.newInstance(device, position));
        });
        rvInterfaces.setAdapter(adapter);
    }

    private void initializeDisplaySpinner() {
        DisplayManager displayManager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        displayList = Arrays.asList(displays);

        // 创建显示器名称列表用于Spinner
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
            int selectedPosition = spinnerDisplays.getSelectedItemPosition();
            if (selectedPosition != -1 && selectedPosition < displayList.size()) {
                Display selectedDisplay = displayList.get(selectedPosition);
                bindDeviceToDisplay(device, selectedDisplay);
            }
        });
    }

    private void bindDeviceToDisplay(UsbDevice usbDevice, Display display) {
        if (!ShizukuUtils.hasShizukuStarted()) {
            State.log("需要安装 Shizuku 并授权才能绑定输入到屏幕");
            return;
        }
        InputDevice inputDevice = findInputDevice(usbDevice);
        if (inputDevice == null) {
            State.log("找不到对应的输入设备");
            return;
        }
        State.startNewJob(new BindInputToDisplay(inputDevice, display));
    }

    private InputDevice findInputDevice(UsbDevice usbDevice) {
        InputManager inputManager = (InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
        for(int inputDeviceId : inputManager.getInputDeviceIds()) {
            InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
            if (inputDevice.isExternal() && inputDevice.getVendorId() == usbDevice.getVendorId() && inputDevice.getProductId() == usbDevice.getProductId()) {
                return inputDevice;
            }
         }
        return null;
    }
} 