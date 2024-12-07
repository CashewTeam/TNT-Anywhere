package com.gitee.connect_screen;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UsbDeviceAdapter extends RecyclerView.Adapter<UsbDeviceAdapter.ViewHolder> {
    private List<UsbDevice> devices;
    private OnDeviceClickListener listener;
    private UsbManager usbManager;

    public interface OnDeviceClickListener {
        void onDeviceClick(UsbDevice device);
    }

    public UsbDeviceAdapter(List<UsbDevice> devices, OnDeviceClickListener listener, UsbManager usbManager) {
        this.devices = devices;
        this.listener = listener;
        this.usbManager = usbManager;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_usb_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        UsbDevice device = devices.get(position);
        holder.deviceName.setText(device.getDeviceName());
        
        if (device.getVendorId() == 6121 && State.displaylinkDeviceName == null) {
            State.displaylinkDeviceName = device.getDeviceName();
        }
        String vendorInfo = device.getDeviceName().equals(State.displaylinkDeviceName) ? 
            "DisplayLink (6121)" : 
            String.valueOf(device.getVendorId());
        
        String deviceType = getDeviceTypeName(device.getDeviceClass());
        String authStatus = usbManager.hasPermission(device) ? "已授权" : "未授权";
        
        holder.deviceId.setText(deviceType + " - VID: " + vendorInfo + 
            " PID: " + device.getProductId() + " - " + authStatus);
        
        View.OnClickListener clickListener = v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        };
        
        holder.itemView.setOnClickListener(clickListener);
        holder.viewButton.setOnClickListener(clickListener);
        if (device.getDeviceName().equals(State.displaylinkDeviceName)) {
            holder.viewButton.setText("投屏");
        }
    }

    private String getDeviceTypeName(int deviceClass) {
        switch (deviceClass) {
            case UsbConstants.USB_CLASS_APP_SPEC:
                return "应用特定设备";
            case UsbConstants.USB_CLASS_AUDIO:
                return "音频设备";
            case UsbConstants.USB_CLASS_CDC_DATA:
                return "CDC数据设备";
            case UsbConstants.USB_CLASS_COMM:
                return "通信设备";
            case UsbConstants.USB_CLASS_CONTENT_SEC:
                return "内容安全设备";
            case UsbConstants.USB_CLASS_HID:
                return "人机接口设备";
            case UsbConstants.USB_CLASS_HUB:
                return "USB集线器";
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return "大容量存储设备";
            case UsbConstants.USB_CLASS_MISC:
                return "其他设备";
            case UsbConstants.USB_CLASS_PER_INTERFACE:
                return "接口定义设备";
            case UsbConstants.USB_CLASS_PHYSICA:
                return "物理设备";
            case UsbConstants.USB_CLASS_PRINTER:
                return "打印机";
            case UsbConstants.USB_CLASS_STILL_IMAGE:
                return "图像设备";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:
                return "厂商特定设备";
            case UsbConstants.USB_CLASS_VIDEO:
                return "视频设备";
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
                return "无线控制器";
            case UsbConstants.USB_CLASS_CSCID:
                return "智能卡设备";
            default:
                return "未知设备";
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        TextView deviceId;
        Button viewButton;

        ViewHolder(View view) {
            super(view);
            deviceName = view.findViewById(R.id.deviceName);
            deviceId = view.findViewById(R.id.deviceId);
            viewButton = view.findViewById(R.id.viewButton);
        }
    }
}