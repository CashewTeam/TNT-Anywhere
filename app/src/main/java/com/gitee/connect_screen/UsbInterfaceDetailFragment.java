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

public class UsbInterfaceDetailFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private static final String ARG_INTERFACE_INDEX = "interface_index";
    
    private UsbDevice device;
    private int interfaceIndex;
    private UsbInterface usbInterface;
    private TextView detailContent;
    private Button btnGetHidDetails;
    private UsbManager usbManager;

    private static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";
    private static final byte USB_REQ_GET_DESCRIPTOR = 0x06;
    private static final byte USB_DT_HID = 0x21;
    
    public static UsbInterfaceDetailFragment newInstance(UsbDevice device, int interfaceIndex) {
        UsbInterfaceDetailFragment fragment = new UsbInterfaceDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_DEVICE, device);
        args.putInt(ARG_INTERFACE_INDEX, interfaceIndex);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            device = getArguments().getParcelable(ARG_DEVICE);
            interfaceIndex = getArguments().getInt(ARG_INTERFACE_INDEX);
            if (device != null) {
                usbInterface = device.getInterface(interfaceIndex);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usb_interface_detail, container, false);
        detailContent = view.findViewById(R.id.detailContent);
        btnGetHidDetails = view.findViewById(R.id.btnGetHidDetails);
        usbManager = (UsbManager) requireContext().getSystemService(Context.USB_SERVICE);

        showInterfaceInfo();
        
        if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
            btnGetHidDetails.setVisibility(View.VISIBLE);
            btnGetHidDetails.setOnClickListener(v -> requestUsbPermissionAndGetDetails());
        }

        return view;
    }

    private void showInterfaceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("接口 #").append(interfaceIndex).append(" 详情:\n\n");
        
        // 基本信息部分
        sb.append("基本信息:\n");
        sb.append("━━━━━━━━━━━━━━━━━━\n");
        sb.append("接口ID: ").append(usbInterface.getId()).append("\n");
        sb.append("替代设置: ").append(usbInterface.getAlternateSetting()).append("\n");
        sb.append("类型: ").append(getInterfaceClassDescription(usbInterface.getInterfaceClass())).append("\n");
        
        // HID设备特殊信息
        if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
            sb.append("子类: ").append(getHidSubclassDescription(usbInterface.getInterfaceSubclass())).append("\n");
            sb.append("协议: ").append(getHidProtocolDescription(usbInterface.getInterfaceProtocol())).append("\n");
            sb.append("用途: ").append(getHidUsageDescription(usbInterface.getInterfaceSubclass(), usbInterface.getInterfaceProtocol())).append("\n");
        }
        
        // 端点信息部分
        sb.append("\n端点信息:\n");
        sb.append("━━━━━━━━━━━━━━━━━━\n");
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            sb.append("端点 ").append(i).append(":\n");
            sb.append("  地址: 0x").append(String.format("%02X", endpoint.getAddress())).append("\n");
            sb.append("  类型: ").append(getEndpointType(endpoint.getType())).append("\n");
            sb.append("  方向: ").append(getEndpointDirection(endpoint.getDirection())).append("\n");
            sb.append("  最大包大小: ").append(endpoint.getMaxPacketSize()).append(" bytes\n");
            sb.append("  间隔: ").append(endpoint.getInterval()).append(" ms\n");
        }
        
        detailContent.setText(sb.toString());
    }

    private void requestUsbPermissionAndGetDetails() {
        // 检查是否已经有权限
        if (usbManager.hasPermission(device)) {
            getHidDetails();
            return;
        }

        // 没有权限则请求权限
        PendingIntent permissionIntent = PendingIntent.getBroadcast(getContext(), 0,
                new Intent(ACTION_USB_PERMISSION), 
                PendingIntent.FLAG_IMMUTABLE);
                
        // 注册广播接收器
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        requireContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        
        // 请求权限前先确保设备和管理器都存在
        if (device != null && usbManager != null) {
            usbManager.requestPermission(device, permissionIntent);
        } else {
            Toast.makeText(getContext(), "USB设备或管理器不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null && usbDevice.equals(device)) {
                            getHidDetails();
                        }
                    } else {
                        Toast.makeText(context, "USB权限被拒绝", Toast.LENGTH_SHORT).show();
                    }
                    try {
                        context.unregisterReceiver(this);
                    } catch (IllegalArgumentException e) {
                        // 忽略重复解注册的异常
                    }
                }
            }
        }
    };

    // 从UsbDeviceDetailFragment复制的其他辅助方法
   
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
    
    // 添加新的解析方法
    private void parseHidDescriptor(byte[] desc, int length, StringBuilder sb) {
        if (length < 9) return; // HID描述符至少9字节
        
        sb.append("\nHID描述符解析:\n");
        sb.append("  描述符长度: ").append(desc[0] & 0xFF).append(" bytes\n");
        sb.append("  描述符类型: ").append(String.format("0x%02X", desc[1] & 0xFF)).append("\n");
        sb.append("  HID版本: ").append((desc[3] & 0xFF)).append(".").append((desc[2] & 0xFF)).append("\n");
        sb.append("  国家代码: ").append(desc[4] & 0xFF).append("\n");
        sb.append("  描述符数量: ").append(desc[5] & 0xFF).append("\n");
        
        // 解析类描述符信息
        int numDesc = desc[5] & 0xFF;
        int pos = 6;
        for (int i = 0; i < numDesc && pos + 3 <= length; i++) {
            int descType = desc[pos + 1] & 0xFF;
            int descLength = (desc[pos + 2] & 0xFF) | ((desc[pos + 3] & 0xFF) << 8);
            sb.append("  类描述符 #").append(i + 1).append(":\n");
            sb.append("    类型: ").append(String.format("0x%02X", descType)).append("\n");
            sb.append("    长度: ").append(descLength).append(" bytes\n");
            pos += 3;
        }
    }

    private void parseReportDescriptor(byte[] desc, int length, StringBuilder sb) {
        sb.append("\n报告描述符解析:\n");
        int i = 0;
        while (i < length) {
            int prefix = desc[i] & 0xFF;
            int tag = (prefix >> 4) & 0x0F;
            int type = (prefix >> 2) & 0x03;
            int size = prefix & 0x03;
            
            sb.append("  项 @").append(i).append(": ");
            
            // 解析项类型
            switch (type) {
                case 0: // 主项
                    sb.append("主项 - ");
                    switch (tag) {
                        case 8: sb.append("Input"); break;
                        case 9: sb.append("Output"); break;
                        case 11: sb.append("Feature"); break;
                        case 10: sb.append("Collection"); break;
                        case 12: sb.append("End Collection"); break;
                        default: sb.append("未知(").append(tag).append(")");
                    }
                    break;
                    
                case 1: // 全局项
                    sb.append("全局项 - ");
                    switch (tag) {
                        case 0: sb.append("Usage Page"); break;
                        case 1: sb.append("Logical Minimum"); break;
                        case 2: sb.append("Logical Maximum"); break;
                        case 3: sb.append("Physical Minimum"); break;
                        case 4: sb.append("Physical Maximum"); break;
                        case 5: sb.append("Unit Exponent"); break;
                        case 6: sb.append("Unit"); break;
                        case 7: sb.append("Report Size"); break;
                        case 8: sb.append("Report ID"); break;
                        case 9: sb.append("Report Count"); break;
                        default: sb.append("未知(").append(tag).append(")");
                    }
                    break;
                    
                case 2: // 局部项
                    sb.append("局部项 - ");
                    switch (tag) {
                        case 0: sb.append("Usage"); break;
                        case 1: sb.append("Usage Minimum"); break;
                        case 2: sb.append("Usage Maximum"); break;
                        default: sb.append("未知(").append(tag).append(")");
                    }
                    break;
            }
            
            // 读取数据
            if (size > 0 && i + size < length) {
                sb.append(" 数据: ");
                long data = 0;
                for (int j = 0; j < size; j++) {
                    data |= (desc[i + 1 + j] & 0xFF) << (j * 8);
                }
                sb.append(String.format("0x%X", data));
            }
            
            sb.append("\n");
            i += 1 + size; // 移动到下一项
        }
    }
    private void getHidDetails() {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Toast.makeText(getContext(), "无法打开USB连接", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            StringBuilder sb = new StringBuilder(detailContent.getText());
            sb.append("\n\nHID详情:\n");
            sb.append("━━━━━━━━━━━━━━━━━━\n");
            
            // 获取HID描述符
            byte[] hidDesc = new byte[256];
            int hidLen = connection.controlTransfer(
                    UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_STANDARD,
                    USB_REQ_GET_DESCRIPTOR,
                    (USB_DT_HID << 8) | 0,
                    interfaceIndex,
                    hidDesc,
                    256,
                    1000);

            if (hidLen > 0) {
                parseHidDescriptor(hidDesc, hidLen, sb);
                
                // 从HID描述符中获取报告描述符的长度
                if (hidLen >= 9) {
                    int reportDescLength = (hidDesc[7] & 0xFF) | ((hidDesc[8] & 0xFF) << 8);
                    
                    // 获取报告描述符
                    byte[] reportDesc = new byte[reportDescLength];
                    int reportLen = connection.controlTransfer(
                            UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_STANDARD,
                            USB_REQ_GET_DESCRIPTOR,
                            (0x22 << 8) | 0, // 0x22 是报告描述符的类型
                            interfaceIndex,
                            reportDesc,
                            reportDescLength,
                            1000);

                    if (reportLen > 0) {
                        parseReportDescriptor(reportDesc, reportLen, sb);
                    } else {
                        sb.append("\n无法获取报告描述符\n");
                    }
                }
            } else {
                sb.append("无法获取HID描述符\n");
            }
            
            detailContent.setText(sb.toString());
            
        } finally {
            connection.close();
        }
    }

    // 新增方法：获取接口类描述
    private String getInterfaceClassDescription(int interfaceClass) {
        switch (interfaceClass) {
            case UsbConstants.USB_CLASS_APP_SPEC:
                return "应用程序特定 (0x00)";
            case UsbConstants.USB_CLASS_AUDIO:
                return "音频设备 (0x01)";
            case UsbConstants.USB_CLASS_CDC_DATA:
                return "CDC数据 (0x0A)";
            case UsbConstants.USB_CLASS_HID:
                return "人机接口设备(HID) (0x03)";
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return "大容量存储 (0x08)";
            case UsbConstants.USB_CLASS_HUB:
                return "USB集线器 (0x09)";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:
                return "厂商特定 (0xFF)";
            default:
                return "未知类型 (0x" + String.format("%02X", interfaceClass) + ")";
        }
    }

    // 新增方法：获取端点方向描述
    private String getEndpointDirection(int direction) {
        switch (direction) {
            case UsbConstants.USB_DIR_IN:
                return "设备到主机 (IN)";
            case UsbConstants.USB_DIR_OUT:
                return "主机到设备 (OUT)";
            default:
                return "未知方向";
        }
    }

    // 新增方���：获取HID设备用途描述
    private String getHidUsageDescription(int subClass, int protocol) {
        if (subClass == 1) { // Boot Interface Subclass
            switch (protocol) {
                case 1:
                    return "启动键盘";
                case 2:
                    return "启动鼠标";
                default:
                    return "启动设备";
            }
        } else {
            switch (protocol) {
                case 0:
                    return "通用HID设备";
                case 1:
                    return "键盘";
                case 2:
                    return "鼠标";
                default:
                    return "特殊HID设备";
            }
        }
    }
}