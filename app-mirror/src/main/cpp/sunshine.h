#pragma once
#include "stream.h"
#include "moonlight-common-c/src/input.h"

namespace sunshine_callbacks {
    void callJavaOnPinRequested();
    void captureVideoLoop(void *channel_data, safe::mail_t mail, const video::config_t& config, const audio::config_t& audioConfig);
    void captureAudioLoop(void *channel_data, safe::mail_t mail, const audio::config_t& config);
    void callJavaOnTouch(SS_TOUCH_PACKET* touchPacket);
}