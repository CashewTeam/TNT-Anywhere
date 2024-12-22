package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.job.UsbMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UsbListFragment extends Fragment {
    private RecyclerView usbDeviceList;
    private UsbDeviceAdapter adapter;
    private List<UsbDevice> devices = new ArrayList<>();
    private TextView emptyView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usb_list, container, false);
        
        UsbManager usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        usbDeviceList = view.findViewById(R.id.usbDeviceList);
        usbDeviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new UsbDeviceAdapter(devices, device -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                UsbMonitor.handleDisplaylink(device);
                if (device.getDeviceName().equals(State.displaylinkDeviceName)) {
                    activity.pushBreadcrumb(device.getDeviceName(), () -> DisplaylinkFragment.newInstance(device));
                } else {
                    activity.pushBreadcrumb(device.getDeviceName(), () -> UsbDeviceDetailFragment.newInstance(device));
                }
            }
        }, usbManager);
        
        usbDeviceList.setAdapter(adapter);
        
        emptyView = view.findViewById(R.id.emptyView);
        
        // 查询USB设备
        refreshUsbDevices();
        
        return view;
    }
    
    private void refreshUsbDevices() {
        UsbManager usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        devices.clear();
        devices.addAll(deviceList.values());
        adapter.notifyDataSetChanged();
        
        // 根据列表是否为空来显示或隐藏提示文本
        emptyView.setVisibility(devices.isEmpty() ? View.VISIBLE : View.GONE);
    }
} 