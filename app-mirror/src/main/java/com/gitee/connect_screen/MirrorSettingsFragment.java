package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.connect_screen.R;

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
        DisplaylinkPref.load(requireContext());
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
                    // ignore
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
                    // ignore
                }
            }
        });

        // 添加分辨率预设选项
        Spinner resolutionPresetSpinner = view.findViewById(R.id.resolutionPresetSpinner);
        String[] resolutionPresets = new String[]{"快捷设置", "1080p", "1440p", "2160p", "ipad4"};
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
            getContext(),
            android.R.layout.simple_spinner_item,
            resolutionPresets
        );
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionPresetSpinner.setAdapter(resolutionAdapter);
        
        resolutionPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    switch (position) {
                        case 1: // 1080p
                            widthEditText.setText("1920");
                            heightEditText.setText("1080");
                            break;
                        case 2: // 1440p
                            widthEditText.setText("2560");
                            heightEditText.setText("1440");
                            break;
                        case 3: // 2160p
                            widthEditText.setText("3840");
                            heightEditText.setText("2160");
                            break;
                        case 4: // ipad4
                            widthEditText.setText("2048");
                            heightEditText.setText("1536");
                            break;
                    }
                    
                    Context context = requireContext();
                    DisplaylinkPref.load(context);
                    int height = Integer.parseInt(heightEditText.getText().toString());
                    int width = Integer.parseInt(widthEditText.getText().toString());
                    DisplaylinkPref.monitorHeight = height;
                    DisplaylinkPref.monitorWidth = width;
                    DisplaylinkPref.save(context);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何操作
            }
        });

        return view;
    }
} 