#include <jni.h>
#include <string>
#include "logging.h"
#include "config.h"
#include "nvhttp.h"
#include "globals.h"

using namespace std::literals;

extern "C" {

static std::unique_ptr<logging::deinit_t> deinit;

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_start(JNIEnv *env, jclass clazz) {
    deinit = logging::init(0, "/dev/null");
    BOOST_LOG(info) << "start sunshine server"sv;
    mail::man = std::make_shared<safe::mail_raw_t>();
    nvhttp::start();
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_setPkeyPath(JNIEnv *env, jclass clazz, jstring path) {
    const char *str = env->GetStringUTFChars(path, nullptr);
    config::nvhttp.pkey = str;
    env->ReleaseStringUTFChars(path, str);
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_setCertPath(JNIEnv *env, jclass clazz, jstring path) {
    const char *str = env->GetStringUTFChars(path, nullptr);
    config::nvhttp.cert = str;
    env->ReleaseStringUTFChars(path, str);
}

} 