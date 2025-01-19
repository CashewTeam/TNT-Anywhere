package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class MirrorSettingsFragment extends Fragment {
    private SharedPreferences preferences;
    public static final String PREF_NAME = "mirror_settings";
    public static final String KEY_AUTO_ROTATE = "auto_rotate";
    public static final String KEY_AUTO_SCALE = "auto_scale";

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
        EditText widthEditText = view.findViewById(R.id.widthEditText);
        EditText heightEditText = view.findViewById(R.id.heightEditText);
        
        // 加载保存的设置
        boolean autoRotate = preferences.getBoolean(KEY_AUTO_ROTATE, true);
        boolean autoScale = preferences.getBoolean(KEY_AUTO_SCALE, true);
        autoRotateCheckbox.setChecked(autoRotate);
        autoScaleCheckbox.setChecked(autoScale);

        // 设置分辨率初始值
        widthEditText.setText(String.valueOf(DisplaylinkPref.monitorWidth));
        heightEditText.setText(String.valueOf(DisplaylinkPref.monitorHeight));

        // 监听复选框变化
        autoRotateCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_ROTATE, isChecked).apply();
        });

        autoScaleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_AUTO_SCALE, isChecked).apply();
        });

        // 监听分辨率输入变化
        widthEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    Context context = requireContext();
                    DisplaylinkPref.load(context);
                    int width = Integer.parseInt(widthEditText.getText().toString());
                    DisplaylinkPref.monitorWidth = width;
                    DisplaylinkPref.save(context);
                } catch (NumberFormatException e) {
                    widthEditText.setText(String.valueOf(DisplaylinkPref.monitorWidth));
                }
            }
        });

        heightEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    Context context = requireContext();
                    DisplaylinkPref.load(context);
                    int height = Integer.parseInt(heightEditText.getText().toString());
                    DisplaylinkPref.monitorHeight = height;
                    DisplaylinkPref.save(context);
                } catch (NumberFormatException e) {
                    heightEditText.setText(String.valueOf(DisplaylinkPref.monitorHeight));
                }
            }
        });

        return view;
    }
} 