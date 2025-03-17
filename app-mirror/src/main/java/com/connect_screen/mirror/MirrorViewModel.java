package com.connect_screen.mirror;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MirrorViewModel extends ViewModel {
    private final MutableLiveData<MirrorUiState> uiState = new MutableLiveData<>();
    private boolean permissionDenied = false;

    public MirrorViewModel() {
        // 初始状态
        uiState.setValue(new MirrorUiState(false, false, false, false));
    }

    public LiveData<MirrorUiState> getUiState() {
        return uiState;
    }

    public void updateState(boolean screenMirroring, boolean singleAppMode, boolean canUseTouchscreen) {
        uiState.setValue(new MirrorUiState(screenMirroring, singleAppMode, canUseTouchscreen, permissionDenied));
    }
    
    public void setPermissionDenied(boolean denied) {
        this.permissionDenied = denied;
        MirrorUiState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(new MirrorUiState(
                currentState.isScreenMirroring(),
                currentState.isSingleAppMode(),
                currentState.canUseTouchscreen(),
                denied
            ));
        } else {
            uiState.setValue(new MirrorUiState(false, false, false, denied));
        }
    }
} 