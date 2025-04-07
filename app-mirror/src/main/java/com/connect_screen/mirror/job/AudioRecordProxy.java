package com.connect_screen.mirror.job;

import android.os.RemoteException;

import com.connect_screen.mirror.State;

public class AudioRecordProxy {
    public int read(float[] audioData, int offsetInFloats, int sizeInFloats, int readMode) {
        try {
            return State.userService.readAudio(audioData);
        } catch (RemoteException e) {
            return 0;
        }
    }
}
