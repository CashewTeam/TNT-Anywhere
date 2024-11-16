package com.gitee.connect_screen.job;

import android.media.ImageReader;

import com.gitee.connect_screen.State;

public class ListenAndPostFrame implements ImageReader.OnImageAvailableListener {
    @Override
    public void onImageAvailable(ImageReader reader) {
        State.currentActivity.get().runOnUiThread(() -> {
            State.log("onImageAvailable");
        });
    }
}
