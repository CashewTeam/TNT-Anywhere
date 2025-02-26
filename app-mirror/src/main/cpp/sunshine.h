#pragma once
#include "stream.h"

namespace sunshine_callbacks {
    void callJavaOnPinRequested();
    void captureVideoLoop(safe::mail_t mail, const video::config_t& config);
}