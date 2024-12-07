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

public class UsbDeviceDetailFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;

    // 添加 USB 标准请求常量
    private static final byte USB_REQ_GET_DESCRIPTOR = 0x06;
    private static final byte USB_DT_HID = 0x21;

    private TextView detailContent;
    private Button btnGetHidDetails;
    private UsbManager usbManager;
    
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
        btnGetHidDetails = view.findViewById(R.id.btnGetHidDetails);
        usbManager = (UsbManager) requireContext().getSystemService(Context.USB_SERVICE);

        if (device == null) {
            detailContent.setText("USB 设备未找到");
            return view;
        }

        // 显示基本信息
        showBasicInfo();
        
        // 检查是否为HID设备
        boolean isHidDevice = false;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
                isHidDevice = true;
                break;
            }
        }
        
        // 如果是HID设备，显示按钮
        if (isHidDevice) {
            btnGetHidDetails.setVisibility(View.VISIBLE);
            btnGetHidDetails.setOnClickListener(v -> requestUsbPermissionAndGetDetails());
        }

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
        
        // 添加接口详细信息
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            sb.append("接口 #").append(i).append(":\n");
            sb.append("  类型: ").append(intf.getInterfaceClass());
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
                sb.append(" (HID设备)\n");
                sb.append("  协议: ").append(getHidProtocolDescription(intf.getInterfaceProtocol())).append("\n");
                sb.append("  子类: ").append(getHidSubclassDescription(intf.getInterfaceSubclass())).append("\n");
            } else {
                sb.append("\n");
            }

            // 添加 endpoint 信息
            sb.append("  端点数量: ").append(intf.getEndpointCount()).append("\n");
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint endpoint = intf.getEndpoint(j);
                sb.append("    端点 #").append(j).append(":\n");
                sb.append("      地址: 0x").append(String.format("%02X", endpoint.getAddress())).append("\n");
                sb.append("      方向: ").append(endpoint.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT").append("\n");
                sb.append("      类型: ").append(getEndpointType(endpoint.getType())).append("\n");
                sb.append("      最大包大小: ").append(endpoint.getMaxPacketSize()).append(" bytes\n");
                sb.append("      轮询间隔: ").append(endpoint.getInterval()).append(" ms\n");
            }
            sb.append("\n");
        }
        
        detailContent.setText(sb.toString());
    }
    
    private void requestUsbPermissionAndGetDetails() {
        if (!usbManager.hasPermission(device)) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                requireContext(), 0, 
                new Intent(ACTION_USB_PERMISSION), 
                PendingIntent.FLAG_IMMUTABLE);
                
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            requireContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            getHidDetails();
                        } else {
                            Toast.makeText(context, "USB权限被拒绝", Toast.LENGTH_SHORT).show();
                        }
                        requireContext().unregisterReceiver(this);
                    }
                }
            }, filter, Context.RECEIVER_NOT_EXPORTED);
            
            usbManager.requestPermission(device, permissionIntent);
        } else {
            getHidDetails();
        }
    }
    
    private void getHidDetails() {
        StringBuilder sb = new StringBuilder(detailContent.getText());
        UsbDeviceConnection connection = usbManager.openDevice(device);
        
        if (connection != null) {
            try {
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface intf = device.getInterface(i);
                    if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
                        sb.append("\nHID描述符信息 #").append(i).append(":\n");
                        
                        byte[] desc = new byte[256];
                        int result = connection.controlTransfer(
                            UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_STANDARD,
                            USB_REQ_GET_DESCRIPTOR,
                            (USB_DT_HID << 8) | 0,
                            intf.getId(),
                            desc,
                            desc.length,
                            1000);
                        
                        if (result > 0) {
                            sb.append("HID描述符长度: ").append(result).append(" bytes\n");
                            sb.append("原始描述符数据: ").append(bytesToHex(desc, result)).append("\n");
                        }
                    }
                }
                detailContent.setText(sb.toString());
            } finally {
                connection.close();
            }
        } else {
            Toast.makeText(requireContext(), "无法打开USB连接", Toast.LENGTH_SHORT).show();
        }
    }
    
    private static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";
    
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hex.append(String.format("%02X ", bytes[i]));
        }
        return hex.toString();
    }

    private String getHidProtocolDescription(int protocol) {
        switch (protocol) {
            case 0:
                return "无 (0)";
            case 1:
                return "键盘 (1)";
            case 2:
                return "鼠标 (2)";
            default:
                return "未知 (" + protocol + ")";
        }
    }

    private String getHidSubclassDescription(int subclass) {
        switch (subclass) {
            case 0:
                return "无子类 (0)";
            case 1:
                return "启动接口子类 (1)";
            default:
                return "未知子类 (" + subclass + ")";
        }
    }

    // 添加新的辅助方法来获取端点类型描述
    private String getEndpointType(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                return "控制传输";
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                return "批量传输";
            case UsbConstants.USB_ENDPOINT_XFER_INT:
                return "中断传输";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                return "同步传输";
            default:
                return "未知 (" + type + ")";
        }
    }
} 