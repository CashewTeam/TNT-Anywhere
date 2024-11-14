package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UsbFragment extends Fragment {
    private RecyclerView usbDeviceList;
    private UsbDeviceAdapter adapter;
    private List<UsbDevice> devices = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usb, container, false);
        
        usbDeviceList = view.findViewById(R.id.usbDeviceList);
        usbDeviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new UsbDeviceAdapter(devices, device -> {
            // 更新面包屑
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.pushBreadcrumb(device.getDeviceName());
            }
            
            // 处理设备点击事件，显示详情
            UsbDeviceDetailFragment detailFragment = UsbDeviceDetailFragment.newInstance(device);
            getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null)
                .commit();
        });
        
        usbDeviceList.setAdapter(adapter);
        
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
    }
} 