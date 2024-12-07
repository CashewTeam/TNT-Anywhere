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
        
        // 添加描述符类型的含义说明
        int descType = desc[1] & 0xFF;
        String descTypeStr = descType == 0x21 ? "HID描述符" : 
                            descType == 0x22 ? "报告描述符" :
                            descType == 0x23 ? "物理描述符" : "未知类型";
        sb.append("  描述符类型: 0x").append(String.format("%02X", descType))
          .append(" (").append(descTypeStr).append(")\n");
        
        sb.append("  HID版本: ").append((desc[3] & 0xFF)).append(".").append((desc[2] & 0xFF)).append("\n");
        
        // 添加国家代码的含义说明
        int countryCode = desc[4] & 0xFF;
        String countryStr = getCountryCodeDescription(countryCode);
        sb.append("  国家代码: ").append(countryCode).append(" (").append(countryStr).append(")\n");
        
        sb.append("  描述符数量: ").append(desc[5] & 0xFF).append("\n");
        
        // 解析类描述符信息
        int numDesc = desc[5] & 0xFF;
        int pos = 6;
        for (int i = 0; i < numDesc && pos + 3 <= length; i++) {
            int classDescType = desc[pos + 1] & 0xFF;
            String classDescTypeStr = getClassDescriptorType(classDescType);
            int descLength = (desc[pos + 2] & 0xFF) | ((desc[pos + 3] & 0xFF) << 8);
            sb.append("  类描述符 #").append(i + 1).append(":\n");
            sb.append("    类型: 0x").append(String.format("%02X", classDescType))
              .append(" (").append(classDescTypeStr).append(")\n");
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
            
            // 解析项类型和数据
            String itemTypeStr = "";
            String tagMeaning = "";
            
            switch (type) {
                case 0: // 主项
                    itemTypeStr = "主项";
                    tagMeaning = getMainTagMeaning(tag);
                    break;
                case 1: // 全局项
                    itemTypeStr = "全局项";
                    tagMeaning = getGlobalTagMeaning(tag);
                    break;
                case 2: // 局部项
                    itemTypeStr = "局部项";
                    tagMeaning = getLocalTagMeaning(tag);
                    break;
            }
            
            sb.append(itemTypeStr).append(" - ").append(tagMeaning);
            
            // 读取数据并解析含义
            if (size > 0 && i + size < length) {
                long data = 0;
                for (int j = 0; j < size; j++) {
                    data |= (desc[i + 1 + j] & 0xFF) << (j * 8);
                }
                sb.append(" 数据: 0x").append(String.format("%X", data));
                
                // 根据不同类型的项添加数据含义
                String dataMeaning = getDataMeaning(type, tag, data);
                if (!dataMeaning.isEmpty()) {
                    sb.append(" (").append(dataMeaning).append(")");
                }
            }
            
            sb.append("\n");
            i += 1 + size;
        }
    }

    // 新增辅助方法
    private String getCountryCodeDescription(int code) {
        switch (code) {
            case 0: return "不支持本地化";
            case 1: return "阿拉伯";
            case 2: return "比利时";
            case 3: return "加拿大-双语";
            case 4: return "加拿大-法语";
            case 5: return "捷克共和国";
            case 6: return "丹麦";
            case 7: return "芬兰";
            case 8: return "法国";
            case 9: return "德国";
            case 10: return "希腊";
            case 11: return "希伯来语";
            case 12: return "匈牙利";
            case 13: return "国际通用";
            case 14: return "意大利";
            case 15: return "日本";
            case 16: return "韩国";
            case 17: return "拉丁美洲";
            case 18: return "荷兰";
            case 19: return "挪威";
            case 20: return "波斯（法尔西）";
            case 21: return "波兰";
            case 22: return "葡萄牙";
            case 23: return "俄罗斯";
            case 24: return "斯洛伐克";
            case 25: return "西班牙";
            case 26: return "瑞典";
            case 27: return "瑞士-法语";
            case 28: return "瑞士-德语";
            case 29: return "瑞士";
            case 30: return "台湾";
            case 31: return "土耳其-Q";
            case 32: return "英国";
            case 33: return "美国";
            case 34: return "南斯拉夫";
            case 35: return "土耳其-F";
            default: return "未知";
        }
    }

    private String getClassDescriptorType(int type) {
        switch (type) {
            case 0x21: return "HID描述符";
            case 0x22: return "报告描述符";
            case 0x23: return "物理描述符";
            default: return "未知描述符";
        }
    }

    private String getMainTagMeaning(int tag) {
        switch (tag) {
            case 8: return "输入(Input)";
            case 9: return "输出(Output)";
            case 10: return "集合(Collection)";
            case 11: return "特性(Feature)";
            case 12: return "结束集合(End Collection)";
            default: return "未知主项(" + tag + ")";
        }
    }

    private String getGlobalTagMeaning(int tag) {
        switch (tag) {
            case 0: return "用途页(Usage Page)";
            case 1: return "逻辑最小值";
            case 2: return "逻辑最大值";
            case 3: return "物理最小值";
            case 4: return "物理最大值";
            case 5: return "单位指数";
            case 6: return "单位";
            case 7: return "报告大小(bits)";
            case 8: return "报告ID";
            case 9: return "报告数量";
            default: return "未知全局项(" + tag + ")";
        }
    }

    private String getLocalTagMeaning(int tag) {
        switch (tag) {
            case 0: return "用途(Usage)";
            case 1: return "用途最小值";
            case 2: return "用途最大值";
            case 3: return "指示符索引";
            case 4: return "指示符数量";
            default: return "未知局部项(" + tag + ")";
        }
    }

    private String getDataMeaning(int type, int tag, long data) {
        if (type == 1 && tag == 0) { // Usage Page
            return getUsagePageMeaning((int)data);
        }
        return "";
    }

    private String getUsagePageMeaning(int page) {
        switch (page) {
            case 0x01: return "通用桌面控制";
            case 0x02: return "仿真控制";
            case 0x03: return "VR控制";
            case 0x04: return "运动控制";
            case 0x05: return "游戏控制";
            case 0x06: return "通用设备控制";
            case 0x07: return "键盘/小键盘";
            case 0x08: return "LED指示灯";
            case 0x09: return "按钮";
            case 0x0A: return "序数";
            case 0x0B: return "电话设备";
            case 0x0C: return "消费者设备";
            default: return "未知用途页";
        }
    }

    private void getHidDetails() {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Toast.makeText(getContext(), "无法打开USB连接", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 先获取HID描述符
            byte[] hidDesc = new byte[256];
            int hidLen = connection.controlTransfer(
                    UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_STANDARD,
                    USB_REQ_GET_DESCRIPTOR,
                    (USB_DT_HID << 8) | 0,
                    interfaceIndex,
                    hidDesc,
                    256,
                    1000);

            // 获取报告描述符
            byte[] reportDesc = null;
            int reportLen = 0;
            if (hidLen >= 9) {
                int reportDescLength = (hidDesc[7] & 0xFF) | ((hidDesc[8] & 0xFF) << 8);
                reportDesc = new byte[reportDescLength];
                reportLen = connection.controlTransfer(
                        UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_STANDARD,
                        USB_REQ_GET_DESCRIPTOR,
                        (0x22 << 8) | 0,
                        interfaceIndex,
                        reportDesc,
                        reportDescLength,
                        1000);
            }

            // 开始构建显示内容
            StringBuilder sb = new StringBuilder(detailContent.getText());
            
            // 如果成功获取到报告描述符，添加设备类型分析
            if (reportLen > 0) {
                sb.append("\n设备类型分析:\n");
                sb.append("━━━━━━━━━━━━━━━━━━\n");
                analyzeDeviceType(reportDesc, reportLen, sb);
            }

            sb.append("\n\nHID详情:\n");
            sb.append("━━━━━━━━━━━━━━━━━━\n");

            // 解析HID描述符
            if (hidLen > 0) {
                parseHidDescriptor(hidDesc, hidLen, sb);
                
                // 解析报告描述符
                if (reportLen > 0) {
                    parseReportDescriptor(reportDesc, reportLen, sb);
                } else {
                    sb.append("\n无法获取报告描述符\n");
                }
            } else {
                sb.append("无法获取HID描述符\n");
            }
            
            detailContent.setText(sb.toString());
            
        } finally {
            connection.close();
        }
    }

    // 新增设备类型分析方法
    private void analyzeDeviceType(byte[] desc, int length, StringBuilder sb) {
        boolean isTouchScreen = false;
        boolean isDigitizer = false;
        boolean isKeyboard = false;
        boolean isMouse = false;
        int currentUsagePage = 0;
        
        int i = 0;
        while (i < length) {
            int prefix = desc[i] & 0xFF;
            int tag = (prefix >> 4) & 0x0F;
            int type = (prefix >> 2) & 0x03;
            int size = prefix & 0x03;
            
            long data = 0;
            if (size > 0 && i + size < length) {
                for (int j = 0; j < size; j++) {
                    data |= (desc[i + 1 + j] & 0xFF) << (j * 8);
                }
            }

            if (type == 1 && tag == 0) { // Usage Page (全局项)
                currentUsagePage = (int)data;
                if (data == 0x0D) { // Digitizer
                    isDigitizer = true;
                } else if (data == 0x01) { // Generic Desktop
                    // 继续检查具体Usage
                } else if (data == 0x07) { // Keyboard
                    isKeyboard = true;
                }
            } else if (type == 2 && tag == 0) { // Usage (局部项)
                if (currentUsagePage == 0x0D && data == 0x04) { // Touch Screen
                    isTouchScreen = true;
                } else if (currentUsagePage == 0x01 && data == 0x02) { // Mouse
                    isMouse = true;
                }
            }

            i += 1 + size;
        }
        
        sb.append("检测到的设备类型:\n");
        if (isTouchScreen) sb.append("• 触摸屏\n");
        if (isDigitizer && !isTouchScreen) sb.append("• 数位板\n");
        if (isKeyboard) sb.append("• 键盘\n");
        if (isMouse) sb.append("• 鼠标\n");
        if (!isTouchScreen && !isDigitizer && !isKeyboard && !isMouse) {
            sb.append("• 其他HID设备\n");
        }
        sb.append("\n");
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

    // 新增方法：获取HID设备用途描述
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