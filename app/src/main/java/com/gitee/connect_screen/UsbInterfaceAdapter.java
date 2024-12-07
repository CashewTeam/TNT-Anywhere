package com.gitee.connect_screen;

import android.hardware.usb.UsbConstants;
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
        String interfaceText = "接口 #" + position + ":\n  类型: " + intf.getInterfaceClass();
        if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
            interfaceText += " (HID设备)";
        }
        holder.tvInterfaceInfo.setText(interfaceText);
        
        View.OnClickListener clickListener = v -> listener.onInterfaceClick(position);
        holder.itemView.setOnClickListener(clickListener);
        holder.btnViewDetail.setOnClickListener(clickListener);
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