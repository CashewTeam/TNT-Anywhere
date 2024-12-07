package com.gitee.connect_screen;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class UsbDeviceDetailFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;

    private TextView detailContent;
    private RecyclerView rvInterfaces;

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
        
        if (device == null) {
            detailContent.setText("USB 设备未找到");
            return view;
        }

        showBasicInfo();
        setupInterfacesList();
        return view;
    }
    
    private void showBasicInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("设备名称: ").append(device.getDeviceName()).append("\n");
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
} 