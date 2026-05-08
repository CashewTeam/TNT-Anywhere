package com.connect_screen.mirror.job;

import com.connect_screen.mirror.MirrorMainActivity;
import com.connect_screen.mirror.SunshineService;
import com.connect_screen.mirror.State;

public class StartSunshineService implements Job {
    private final TntDisplaySelector tntDisplaySelector = new TntDisplaySelector();

    @Override
    public void start() throws YieldException {
        if (SunshineService.getLifecycleState() != SunshineService.LifecycleState.STOPPED) {
            State.log("SunshineService is already starting or running");
            return;
        }

        MirrorMainActivity activity = State.getCurrentActivity();
        if (activity == null) {
            State.showErrorStatus("Cannot start SunshineService without an active UI");
            return;
        }

        if (!tntDisplaySelector.ensureSelected()) {
            return;
        }

        SunshineService.markStarting();
        activity.refresh();
        activity.startMediaProjectionService();
    }
}
