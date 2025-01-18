package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class MirrorSettingsFragment extends Fragment {
    private SharedPreferences preferences;
    private static final String PREF_NAME = "mirror_settings";
    private static final String KEY_AUTO_ROTATE = "auto_rotate";
    private static final String KEY_AUTO_SCALE = "auto_scale";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mirror_settings, container, false);

        CheckBox autoRotateCheckbox = view.findViewById(R.id.autoRotateCheckbox);
        CheckBox autoScaleCheckbox = view.findViewById(R.id.autoScaleCheckbox);
        
        // 加载保存的设置
        boolean autoRotate = preferences.getBoolean(KEY_AUTO_ROTATE, true);
        boolean autoScale = preferences.getBoolean(KEY_AUTO_SCALE, true);
        autoRotateCheckbox.setChecked(autoRotate);
        autoScaleCheckbox.setChecked(autoScale);

        // 监听复选框变化
        autoRotateCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_ROTATE, isChecked).apply();
        });

        // 添加新复选框的监听器
        autoScaleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_SCALE, isChecked).apply();
        });

        return view;
    }
} 