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
                    }
                    break;
                    
                case 2: // 局部项
                    if (tag == 0) { // Usage
                        currentUsage = (int)data;
                        if (currentUsagePage == 0x01) { // GenericDesktop
                            switch ((int)data) {
                                case 0x30: // X
                                case 0x31: // Y
                                    // 坐标相关字段
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
        
        info.append("数据字段：\n");
        for (int i = 0; i < inputFormat.fields.size(); i++) {
            TouchInputFormat.FieldInfo field = inputFormat.fields.get(i);
            String usage = getUsageDescription(field.usagePage, field.usage);
            info.append(String.format("字段 %d: %d位 x %d个 -%s\n", 
                i + 1, field.size, field.count, usage));
        }
        info.append("\n总位数：").append(inputFormat.totalBits).append("位");
        
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
        if (inputFormat == null) return;
        
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hexString.append(String.format("%02X ", data[i]));
        }
        android.util.Log.d(TAG, "触控原始数据: " + hexString.toString());
        
        int bitOffset = 0;
        int contactId = -1;
        int x = 0, y = 0;
        boolean isTouched = false;
        int pressure = 0;
        boolean foundFirstValidPoint = false;
        
        StringBuilder fieldLog = new StringBuilder("字段解析:\n");
        
        for (TouchInputFormat.FieldInfo field : inputFormat.fields) {
            long value = extractBits(data, bitOffset, field.size);
            
            fieldLog.append(String.format("位置 %d-%d: %d位 = 0x%X (%s)\n",
                bitOffset, bitOffset + field.size - 1,
                field.size, value, getUsageDescription(field.usagePage, field.usage)));
            
            if (!foundFirstValidPoint) {
                if (field.usagePage == 0x0D) { // Digitizer
                    switch (field.usage) {
                        case 0x51: contactId = (int)value; break;
                        case 0x42: isTouched = value != 0; break;
                        case 0x30: pressure = (int)value; break;
                    }
                } else if (field.usagePage == 0x01) { // Generic Desktop
                    switch (field.usage) {
                        case 0x30: 
                            x = (int)value;
                            if (value != 0) foundFirstValidPoint = true;
                            break;
                        case 0x31: 
                            y = (int)value;
                            if (value != 0) foundFirstValidPoint = true;
                            break;
                    }
                }
            }
            
            bitOffset += field.size * field.count;
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
} 