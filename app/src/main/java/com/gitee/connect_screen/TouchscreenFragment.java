package com.gitee.connect_screen;

import android.content.Context;
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

import androidx.fragment.app.Fragment;

public class TouchscreenFragment extends Fragment {
    private static final String ARG_HID_DESC = "hid_descriptor";
    private static final String ARG_REPORT_DESC = "report_descriptor";
    private static final String ARG_DEVICE = "device";
    private static final String ARG_INTERFACE_INDEX = "interface_index";
    
    private byte[] hidDescriptor;
    private byte[] reportDescriptor;
    private UsbDevice device;
    private int interfaceIndex;
    private UsbInterface usbInterface;
    private UsbDeviceConnection connection;
    private UsbEndpoint inputEndpoint;
    private UsbManager usbManager;
    
    public static TouchscreenFragment newInstance(byte[] hidDesc, byte[] reportDesc, 
            UsbDevice device, int interfaceIndex) {
        TouchscreenFragment fragment = new TouchscreenFragment();
        Bundle args = new Bundle();
        args.putByteArray(ARG_HID_DESC, hidDesc);
        args.putByteArray(ARG_REPORT_DESC, reportDesc);
        args.putParcelable(ARG_DEVICE, device);
        args.putInt(ARG_INTERFACE_INDEX, interfaceIndex);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            hidDescriptor = getArguments().getByteArray(ARG_HID_DESC);
            reportDescriptor = getArguments().getByteArray(ARG_REPORT_DESC);
            device = getArguments().getParcelable(ARG_DEVICE);
            interfaceIndex = getArguments().getInt(ARG_INTERFACE_INDEX);
            
            if (device != null) {
                usbInterface = device.getInterface(interfaceIndex);
                // 查找输入端点
                for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN &&
                        endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                        inputEndpoint = endpoint;
                        break;
                    }
                }
            }
        }
        usbManager = (UsbManager) requireContext().getSystemService(Context.USB_SERVICE);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_touchscreen, container, false);
        // TODO: 实现触摸屏界面
        return view;
    }
} 