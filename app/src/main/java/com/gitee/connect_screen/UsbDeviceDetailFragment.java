package com.gitee.connect_screen;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.MirrorViaDisplaylink;

public class UsbDeviceDetailFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;

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
        
        TextView detailContent = view.findViewById(R.id.detailContent);
        Button mirrorViaDisplaylinkButton = view.findViewById(R.id.mirrorViaDisplaylinkButton);
        
        StringBuilder sb = new StringBuilder();
        sb.append("设备名称: ").append(device.getDeviceName()).append("\n");
        sb.append("厂商ID: ").append(device.getVendorId()).append("\n");
        sb.append("产品ID: ").append(device.getProductId()).append("\n");
        sb.append("设备类: ").append(device.getDeviceClass()).append("\n");
        sb.append("设备子类: ").append(device.getDeviceSubclass()).append("\n");
        sb.append("协议: ").append(device.getDeviceProtocol());
        
        detailContent.setText(sb.toString());
        
        if (device.getVendorId() == 6121) {
            mirrorViaDisplaylinkButton.setVisibility(View.VISIBLE);
        } else {
            mirrorViaDisplaylinkButton.setVisibility(View.GONE);
        }
        
        mirrorViaDisplaylinkButton.setOnClickListener(v -> {
            State.startNewJob(new MirrorViaDisplaylink(device));
        });
        
        return view;
    }
} 