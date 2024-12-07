package com.gitee.connect_screen;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class TouchscreenFragment extends Fragment {
    private static final String ARG_HID_DESC = "hid_descriptor";
    private static final String ARG_REPORT_DESC = "report_descriptor";
    private static final String ARG_DEVICE = "device";
    private static final String ARG_INTERFACE_INDEX = "interface_index";
    private static final String TAG = "TouchscreenFragment";
    
    private byte[] hidDescriptor;
    private byte[] reportDescriptor;
    private UsbDevice device;
    private int interfaceIndex;
    private UsbInterface usbInterface;
    private UsbDeviceConnection connection;
    private UsbEndpoint inputEndpoint;
    private UsbManager usbManager;
    private boolean isReading = false;
    private Thread readThread;
    private byte[] inputBuffer;
    
    private void parseInputFormat() {
        if (reportDescriptor == null) return;
        
        // 解析报告描述符以获取输入格式
        TouchInputFormat format = new TouchInputFormat();
        int i = 0;
        int currentUsagePage = 0;
        int currentReportSize = 0;
        int currentReportCount = 0;
        int currentUsage = 0;
        
        TouchInputFormat.FieldInfo currentField = null;
        
        int logicalMinimum = 0;
        int logicalMaximum = 0;
        
        while (i < reportDescriptor.length) {
            int prefix = reportDescriptor[i] & 0xFF;
            int tag = (prefix >> 4) & 0x0F;
            int type = (prefix >> 2) & 0x03;
            int size = prefix & 0x03;
            
            long data = 0;
            if (size > 0 && i + size < reportDescriptor.length) {
                for (int j = 0; j < size; j++) {
                    data |= (reportDescriptor[i + 1 + j] & 0xFF) << (j * 8);
                }
            }

            switch (type) {
                case 0: // 主项
                    if (tag == 8) { // Input
                        currentField = new TouchInputFormat.FieldInfo(currentReportSize, currentReportCount);
                        currentField.usagePage = currentUsagePage;
                        currentField.usage = currentUsage;
                        format.addField(currentField);
                    }
                    break;
                    
                case 1: // 全局项
                    switch (tag) {
                        case 0: // Usage Page
                            currentUsagePage = (int)data;
                            break;
                        case 7: // Report Size
                            currentReportSize = (int)data;
                            break;
                        case 9: // Report Count
                            currentReportCount = (int)data;
                            break;
                        case 1: // Logical Minimum
                            logicalMinimum = (int)data;
                            break;
                        case 2: // Logical Maximum
                            logicalMaximum = (int)data;
                            break;
                    }
                    break;
                    
                case 2: // 局部项
                    if (tag == 0) { // Usage
                        currentUsage = (int)data;
                        if (currentUsagePage == 0x01) { // GenericDesktop
                            switch ((int)data) {
                                case 0x30: // X
                                    format.xMin = logicalMinimum;
                                    format.xMax = logicalMaximum;
                                    break;
                                case 0x31: // Y
                                    format.yMin = logicalMinimum;
                                    format.yMax = logicalMaximum;
                                    break;
                            }
                        } else if (currentUsagePage == 0x0D) {// Digitizer
                            switch ((int)data) {
                                case 0x51: // Contact ID
                                    // 手指ID字段
                                    break;
                                case 0x42: // Tip Switch
                                    format.hasTipSwitch = true;
                                    break;
                                case 0x30: // Tip Pressure
                                    format.hasPressure = true;
                                    break;
                            }
                        }
                    }
                    break;
            }
            
            i += 1 + size;
        }
        
        // 保存解析结果
        inputFormat = format;
    }
    
    // 用于存储触摸输入格式的内部类
    private static class TouchInputFormat {
        List<FieldInfo> fields = new ArrayList<>();
        boolean hasPressure = false;
        boolean hasInRange = false;
        boolean hasTipSwitch = false;
        int totalBits = 0;
        
        // 添加坐标范围信息
        int xMin = 0, xMax = 0;
        int yMin = 0, yMax = 0;
        
        void addField(FieldInfo field) {
            fields.add(field);
            totalBits += field.size * field.count;
        }
        
        static class FieldInfo {
            final int size;
            final int count;
            int usage = 0;
            int usagePage = 0;
            
            FieldInfo(int size, int count) {
                this.size = size;
                this.count = count;
            }
        }
    }
    
    private TouchInputFormat inputFormat;
    
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
            
            // 解析输入格式
            parseInputFormat();
        }
        usbManager = (UsbManager) requireContext().getSystemService(Context.USB_SERVICE);
        
        if (inputEndpoint != null) {
            inputBuffer = new byte[inputEndpoint.getMaxPacketSize()];
            startReading();
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_touchscreen, container, false);
        
        TextView formatInfo = view.findViewById(R.id.formatInfo);
        displayFormatInfo(formatInfo);
        
        return view;
    }
    
    private void displayFormatInfo(TextView textView) {
        if (inputFormat == null) {
            textView.setText("未能解析输入格式");
            return;
        }
        
        StringBuilder info = new StringBuilder();
        info.append("触摸输入格式信息：\n\n");
        
        info.append("支持的功能：\n");
        info.append("- 压力感应: ").append(inputFormat.hasPressure ? "是" : "否").append("\n");
        info.append("- 悬空检测: ").append(inputFormat.hasInRange ? "是" : "否").append("\n");
        info.append("- 接触检测: ").append(inputFormat.hasTipSwitch ? "是" : "否").append("\n\n");
        
        info.append("坐标范围：\n");
        if (inputFormat.xMax > inputFormat.xMin) {
            info.append("- X轴: ").append(inputFormat.xMin).append(" - ").append(inputFormat.xMax).append("\n");
        }
        if (inputFormat.yMax > inputFormat.yMin) {
            info.append("- Y轴: ").append(inputFormat.yMin).append(" - ").append(inputFormat.yMax).append("\n");
        }
        info.append("\n");
        
        info.append("数据字段：\n");
        for (int i = 0; i < inputFormat.fields.size(); i++) {
            TouchInputFormat.FieldInfo field = inputFormat.fields.get(i);
            String usage = getUsageDescription(field.usagePage, field.usage);
            info.append(String.format("字段 %d: %d位 x %d个 -%s\n", 
                i + 1, field.size, field.count, usage));
        }
        info.append("\n总位数：").append(inputFormat.totalBits).append("位");
        
        android.util.Log.d(TAG, info.toString());
        textView.setText(info.toString());
    }
    
    private String getUsageDescription(int usagePage, int usage) {
        if (usagePage == 0x01) {
            switch (usage) {
                case 0x30: return "X坐标";
                case 0x31: return "Y坐标";
                default: return "未知通用字段";
            }
        } else if (usagePage == 0x0D) {
            switch (usage) {
                case 0x51: return "手指ID";
                case 0x42: return "触摸状态";
                case 0x30: return "压力值";
                default: return "未知数字化仪字段";
            }
        }
        return "未知字段";
    }
    
    private void startReading() {
        if (connection == null) {
            connection = usbManager.openDevice(device);
            if (connection != null) {
                connection.claimInterface(usbInterface, true);
            }
        }
        
        if (connection == null) return;
        
        isReading = true;
        readThread = new Thread(() -> {
            while (isReading) {
                int bytesRead = connection.bulkTransfer(inputEndpoint, inputBuffer, 
                    inputBuffer.length, 100);
                if (bytesRead > 0) {
                    parseInputData(inputBuffer, bytesRead);
                }
            }
        });
        readThread.start();
    }

    private void parseInputData(byte[] data, int length) {
        TouchData touchData = parseWithBasicFormat(data, length);
        for (TouchPoint point : touchData.points) {
            if (point.isValid) {
                Log.d(TAG, String.format("触摸点 ID:%d, X:%d, Y:%d, 按下:%b",
                        point.contactId, point.x, point.y, point.isTouched));
            }
        }
    }

    private long extractBits(byte[] data, int startBit, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            int byteIndex = (startBit + i) / 8;
            int bitIndex = (startBit + i) % 8;
            if (byteIndex >= data.length) break;
            
            if ((data[byteIndex] & (1 << bitIndex)) != 0) {
                result |= (1L << i);
            }
        }
        return result;
    }

    @Override
    public void onDestroy() {
        isReading = false;
        if (readThread != null) {
            try {
                readThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (connection != null) {
            connection.releaseInterface(usbInterface);
            connection.close();
        }
        super.onDestroy();
    }

    private static class TouchPoint {
        boolean isValid;
        int contactId;
        int x;
        int y;
        boolean isTouched;
    }

    private static class TouchData {
        List<TouchPoint> points;
        int scanTime; // 扫描时间 (最后16位)
        int reportId; // 报告ID (最后8位)
        
        TouchData() {
            points = new ArrayList<>();
        }
    }

    private TouchData parseWithBasicFormat(byte[] data, int length) {
        TouchData result = new TouchData();
        if (length < 5) return result;

        int touchCount = data[0] & 0xFF;
        touchCount = Math.min(touchCount, 10);
        
        int offset = 1;
        for (int i = 0; i < touchCount && offset + 5 <= length; i++) {
            TouchPoint point = new TouchPoint();
            
            int touchInfo = data[offset] & 0xFF;
            
            point.contactId = (touchInfo >> 4) & 0x0F;
            point.isTouched = (touchInfo & 0x0F) == 1;
            
            // 解析X坐标 (小端)
            point.x = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            
            // 解析Y坐标 (小端)
            point.y = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            
            // 判断该点是否有效
            point.isValid = (point.x != 0 || point.y != 0);
            
            result.points.add(point);
            offset += 5; // 移动到下一个触摸点数据
        }
        return result;
    }
} 