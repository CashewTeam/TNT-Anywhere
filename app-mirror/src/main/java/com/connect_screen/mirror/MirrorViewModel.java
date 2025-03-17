package com.connect_screen.mirror;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MirrorViewModel extends ViewModel {
    public final MutableLiveData<MirrorUiState> uiState = new MutableLiveData<>();
    
    public MirrorViewModel() {
        // 初始状态
        uiState.setValue(new MirrorUiState(
            "请连接屏幕，如果接口是USB2.0的手机需要Displaylink扩展坞或者Moonlight无线投屏",
            true,  // settingsBtnVisibility
            false, // screenOffBtnVisibility
            false, // touchScreenBtnVisibility
            ""     // touchScreenBtnText
        ));
    }

    public LiveData<MirrorUiState> getUiState() {
        return uiState;
    }

    public void updateUiState(boolean isScreenMirroring, boolean isSingleAppMode, 
                             boolean canUseTouchscreen, boolean isPermissionDenied) {
        String statusText;
        boolean showSettings;
        boolean showScreenOff;
        boolean showTouchScreen;
        String touchScreenText = "";
        
        if (isPermissionDenied) {
            statusText = "未获得投屏权限，请手工点击退出按钮";
            showSettings = true;
            showScreenOff = false;
            showTouchScreen = false;
        } else if (isScreenMirroring) {
            statusText = "镜像投屏中，请在系统设置中为屏易连关闭省电，并在任务列表中锁定任务防止被杀";
            showSettings = false;
            showScreenOff = true;
            showTouchScreen = isSingleAppMode;
            
            if (isSingleAppMode) {
                touchScreenText = canUseTouchscreen ? "触摸屏" : "触控板";
            }
        } else {
            statusText = "请连接屏幕，如果接口是USB2.0的手机需要Displaylink扩展坞或者Moonlight无线投屏";
            showSettings = true;
            showScreenOff = false;
            showTouchScreen = false;
        }
        
        uiState.setValue(new MirrorUiState(
            statusText,
            showSettings,
            showScreenOff,
            showTouchScreen,
            touchScreenText
        ));
    }

} 