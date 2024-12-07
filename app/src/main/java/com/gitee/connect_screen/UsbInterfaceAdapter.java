package com.gitee.connect_screen;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UsbInterfaceAdapter extends RecyclerView.Adapter<UsbInterfaceAdapter.ViewHolder> {
    private List<UsbInterface> interfaces;
    private OnInterfaceClickListener listener;

    public interface OnInterfaceClickListener {
        void onInterfaceClick(int position);
    }

    public UsbInterfaceAdapter(List<UsbInterface> interfaces, OnInterfaceClickListener listener) {
        this.interfaces = interfaces;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_usb_interface, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        UsbInterface intf = interfaces.get(position);
        StringBuilder interfaceText = new StringBuilder();
        interfaceText.append("接口 #").append(position).append("\n");
        interfaceText.append("  类型: ").append(getInterfaceClassName(intf.getInterfaceClass())).append("\n");
        interfaceText.append("  子类: ").append(getInterfaceSubclassName(intf.getInterfaceClass(), intf.getInterfaceSubclass())).append("\n");
        interfaceText.append("  协议: ").append(getInterfaceProtocolName(intf.getInterfaceClass(), intf.getInterfaceProtocol())).append("\n");
        interfaceText.append("  端点数: ").append(intf.getEndpointCount()).append("\n");
        
        // 添加端点详情
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint endpoint = intf.getEndpoint(i);
            interfaceText.append("    端点 ").append(i + 1).append(": \n");
            interfaceText.append("      地址: 0x").append(Integer.toHexString(endpoint.getAddress())).append("\n");
            interfaceText.append("      类型: ").append(getEndpointType(endpoint.getType())).append("\n");
            interfaceText.append("      方向: ").append(getEndpointDirection(endpoint.getDirection())).append("\n");
            interfaceText.append("      最大包大小: ").append(endpoint.getMaxPacketSize()).append(" 字节\n");
        }
        
        holder.tvInterfaceInfo.setText(interfaceText.toString());
        
        View.OnClickListener clickListener = v -> listener.onInterfaceClick(position);
        holder.itemView.setOnClickListener(clickListener);
        holder.btnViewDetail.setOnClickListener(clickListener);
    }

    private String getInterfaceClassName(int interfaceClass) {
        switch (interfaceClass) {
            case UsbConstants.USB_CLASS_APP_SPEC:
                return "应用程序特定 (0x" + Integer.toHexString(interfaceClass) + ")";
            case UsbConstants.USB_CLASS_AUDIO:
                return "音频设备 (0x" + Integer.toHexString(interfaceClass) + ")";
            case UsbConstants.USB_CLASS_CDC_DATA:
                return "CDC数据 (0x" + Integer.toHexString(interfaceClass) + ")";
            case UsbConstants.USB_CLASS_HID:
                return "HID设备 (0x" + Integer.toHexString(interfaceClass) + ")";
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return "大容量存储 (0x" + Integer.toHexString(interfaceClass) + ")";
            case UsbConstants.USB_CLASS_MISC:
                return "其他设备 (0x" + Integer.toHexString(interfaceClass) + ")";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:
                return "厂商特定 (0x" + Integer.toHexString(interfaceClass) + ")";
            default:
                return "未知类型 (0x" + Integer.toHexString(interfaceClass) + ")";
        }
    }

    private String getEndpointType(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                return "批量传输";
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                return "控制传输";
            case UsbConstants.USB_ENDPOINT_XFER_INT:
                return "中断传输";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                return "同步传输";
            default:
                return "未知类型";
        }
    }

    private String getEndpointDirection(int direction) {
        switch (direction) {
            case UsbConstants.USB_DIR_IN:
                return "输入(IN)";
            case UsbConstants.USB_DIR_OUT:
                return "输出(OUT)";
            default:
                return "未知方向";
        }
    }

    private String getInterfaceSubclassName(int interfaceClass, int subClass) {
        if (interfaceClass == UsbConstants.USB_CLASS_HID) {
            switch (subClass) {
                case 1:
                    return "启动接口";
                case 0:
                    return "无子类";
                default:
                    return "未知子类 (0x" + Integer.toHexString(subClass) + ")";
            }
        }
        // 可以继续添加其他接口类型的子类翻译
        return "子类 0x" + Integer.toHexString(subClass);
    }

    private String getInterfaceProtocolName(int interfaceClass, int protocol) {
        if (interfaceClass == UsbConstants.USB_CLASS_HID) {
            switch (protocol) {
                case 0:
                    return "无";
                case 1:
                    return "键盘";
                case 2:
                    return "鼠标";
                default:
                    return "协议 0x" + Integer.toHexString(protocol);
            }
        }
        // 可以继续添加其他接口类型的协议翻译
        return "协议 0x" + Integer.toHexString(protocol);
    }

    @Override
    public int getItemCount() {
        return interfaces.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvInterfaceInfo;
        Button btnViewDetail;

        ViewHolder(View view) {
            super(view);
            tvInterfaceInfo = view.findViewById(R.id.tvInterfaceInfo);
            btnViewDetail = view.findViewById(R.id.btnViewDetail);
        }
    }
} 