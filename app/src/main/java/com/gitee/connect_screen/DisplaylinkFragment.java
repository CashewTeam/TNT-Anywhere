package com.gitee.connect_screen;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.gitee.connect_screen.job.MirrorViaDisplaylink;
import com.gitee.connect_screen.UsbState;
import java.util.Arrays;

public class DisplaylinkFragment extends Fragment {
    private static final String ARG_DEVICE = "device";
    private UsbDevice device;
    private UsbState usbState;

    public static DisplaylinkFragment newInstance(UsbDevice device) {
        DisplaylinkFragment fragment = new DisplaylinkFragment();
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
            usbState = State.getOrCreateUsbState(device);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_displaylink, container, false);

        TextView detailContent = view.findViewById(R.id.detailContent);
        Button mirrorViaDisplaylinkButton = view.findViewById(R.id.mirrorViaDisplaylinkButton);
        EditText displayWidthInput = view.findViewById(R.id.displayWidthInput);
        EditText displayHeightInput = view.findViewById(R.id.displayHeightInput);

        Spinner projectionModeSpinner = view.findViewById(R.id.projectionModeSpinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            Arrays.stream(ProjectionMode.values())
                  .map(mode -> {
                      switch(mode) {
                          case MIRROR: return "普通镜像";
                          case MIRROR_AND_CROP_16_9: return "16:9裁剪";
                          default: return mode.name();
                      }
                  })
                  .toArray(String[]::new)
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        projectionModeSpinner.setAdapter(spinnerAdapter);
        
        if (usbState != null && usbState.projectionMode != null) {
            projectionModeSpinner.setSelection(usbState.projectionMode.ordinal());
        }
        
        projectionModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (usbState != null) {
                    usbState.projectionMode = ProjectionMode.values()[position];
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("设备名称: ").append(device.getDeviceName()).append("\n");
        sb.append("厂商ID: ").append(device.getVendorId()).append("\n");
        sb.append("产品ID: ").append(device.getProductId()).append("\n");
        sb.append("设备类: ").append(device.getDeviceClass()).append("\n");
        sb.append("设备子类: ").append(device.getDeviceSubclass()).append("\n");
        sb.append("协议: ").append(device.getDeviceProtocol()).append("\n");

        if (usbState != null) {
            sb.append("Native Driver: ").append(usbState.nativeDriver != null ? "已连接" : "未连接").append("\n");
            if (usbState.monitorInfo != null) {
                int width = usbState.overrideMonitorWidth != 0 ? usbState.overrideMonitorWidth : usbState.monitorInfo.a[0].width;
                int height = usbState.overrideMonitorHeight != 0 ? usbState.overrideMonitorHeight : usbState.monitorInfo.a[0].height;
                displayWidthInput.setText(String.valueOf(width));
                displayHeightInput.setText(String.valueOf(height));
            } else {
                displayWidthInput.setText("未连接");
                displayHeightInput.setText("未连接");
            }
            if (usbState.virtualDisplay != null) {
                Display display = usbState.virtualDisplay.getDisplay();
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                sb.append("虚拟显示器: ").append(display.getDisplayId()).append(", 宽: ").append(metrics.widthPixels).append(", 高: ").append(metrics.heightPixels).append("\n");
            } else {
                sb.append("虚拟显示器: ").append("未连接").append("\n");
            }
            sb.append("总发送帧数: ").append(usbState.frameCounter).append("\n");
            sb.append("最近发送帧状态码: ").append(Arrays.toString(usbState.recentPostFrameResultCodes)).append("\n");
        }

        detailContent.setText(sb.toString());

        mirrorViaDisplaylinkButton.setOnClickListener(v -> {
            try {
                usbState.overrideMonitorWidth = Integer.parseInt(displayWidthInput.getText().toString());
                usbState.overrideMonitorHeight = Integer.parseInt(displayHeightInput.getText().toString());
                State.log("用户设置的显示器宽度: " + usbState.overrideMonitorWidth + ", 高度: " + usbState.overrideMonitorHeight);
            } catch (NumberFormatException e) {
                // ignore
            }
            State.startNewJob(new MirrorViaDisplaylink(device));
        });

        return view;
    }
}