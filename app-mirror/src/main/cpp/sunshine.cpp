#include <jni.h>
#include <string>
#include "logging.h"

using namespace std::literals;

extern "C" {

static std::unique_ptr<logging::deinit_t> deinit;

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_start(JNIEnv *env, jobject thiz) {
    deinit = logging::init(0, "/dev/null");
    BOOST_LOG(info) << "start sunshine server"sv;
}

} 