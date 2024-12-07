package com.gitee.connect_screen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class TouchscreenFragment extends Fragment {
    private static final String ARG_HID_DESC = "hid_descriptor";
    private static final String ARG_REPORT_DESC = "report_descriptor";
    
    private byte[] hidDescriptor;
    private byte[] reportDescriptor;
    
    public static TouchscreenFragment newInstance(byte[] hidDesc, byte[] reportDesc) {
        TouchscreenFragment fragment = new TouchscreenFragment();
        Bundle args = new Bundle();
        args.putByteArray(ARG_HID_DESC, hidDesc);
        args.putByteArray(ARG_REPORT_DESC, reportDesc);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            hidDescriptor = getArguments().getByteArray(ARG_HID_DESC);
            reportDescriptor = getArguments().getByteArray(ARG_REPORT_DESC);
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_touchscreen, container, false);
        // TODO: 实现触摸屏界面
        return view;
    }
} 