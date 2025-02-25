#include <jni.h>
#include <string>
#include "logging.h"
#include "config.h"
#include "nvhttp.h"
#include "globals.h"
#include "sunshine.h"
#include "rtsp.h"

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

using namespace std::literals;

extern "C" {

static std::unique_ptr<logging::deinit_t> deinit;
static JavaVM* jvm = nullptr;
static jclass sunshineServerClass = nullptr;

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_start(JNIEnv *env, jclass clazz) {
    env->GetJavaVM(&jvm);
    
    jclass localClass = env->FindClass("com/connect_screen/mirror/job/SunshineServer");
    if (localClass != nullptr) {
        sunshineServerClass = (jclass)env->NewGlobalRef(localClass);
        env->DeleteLocalRef(localClass);
    } else {
        BOOST_LOG(error) << "无法在启动时找到 SunshineServer 类"sv;
    }
    
    deinit = logging::init(0, "/dev/null");
    BOOST_LOG(info) << "start sunshine server"sv;
    mail::man = std::make_shared<safe::mail_raw_t>();
    
    std::thread httpThread {nvhttp::start};
    rtsp_stream::rtpThread();
    httpThread.join();
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

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_setFileStatePath(JNIEnv *env, jclass clazz, jstring path) {
    const char *str = env->GetStringUTFChars(path, nullptr);
    config::nvhttp.file_state = str;
    env->ReleaseStringUTFChars(path, str);
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_submitPin(JNIEnv *env, jclass clazz, jstring pin) {
    const char *pinStr = env->GetStringUTFChars(pin, nullptr);
    nvhttp::pin(pinStr, "some-moonlight");
    env->ReleaseStringUTFChars(pin, pinStr);
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_cleanup(JNIEnv *env, jclass clazz) {
    if (sunshineServerClass != nullptr) {
        env->DeleteGlobalRef(sunshineServerClass);
        sunshineServerClass = nullptr;
    }
    // 其他清理工作...
}

}

namespace sunshine_callbacks {
    void callJavaOnPinRequested() {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        
        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用为空"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        jmethodID onPinRequestedMethod = env->GetStaticMethodID(sunshineServerClass, "onPinRequested", "()V");
        if (onPinRequestedMethod == nullptr) {
            BOOST_LOG(error) << "找不到 onPinRequested 方法"sv;
            jvm->DetachCurrentThread();
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, onPinRequestedMethod);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }



    void createVirtualDisplay(JNIEnv *env, jobject surface) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用为空"sv;
            return;
        }

        jmethodID createVirtualDisplayMethod = env->GetStaticMethodID(sunshineServerClass, "createVirtualDisplay", "(Landroid/view/Surface;)V");
        if (createVirtualDisplayMethod == nullptr) {
            BOOST_LOG(error) << "找不到 createVirtualDisplay 方法"sv;
            jvm->DetachCurrentThread();
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, createVirtualDisplayMethod, surface);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    static const int WIDTH = 1920;
    static const int HEIGHT = 1080;
    static const int FRAMERATE = 30;
    static const int BITRATE = 5000000; // 5 Mbps

    void captureVideoLoop() {
        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }
        // 创建 MediaFormat
        AMediaFormat *format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "video/avc"); // H264
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, WIDTH);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, HEIGHT);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, BITRATE);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, FRAMERATE);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1); // 关键帧间隔(秒)
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 2130708361); // COLOR_FormatSurface

        // 创建编码器
        AMediaCodec *codec = AMediaCodec_createEncoderByType("video/avc");
        if (!codec) {
            BOOST_LOG(error) << "无法创建 AVC 编码器"sv;
            AMediaFormat_delete(format);
            return;
        }
        
        // 配置编码器
        media_status_t status = AMediaCodec_configure(codec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
        if (status != AMEDIA_OK) {
            BOOST_LOG(error) << "无法配置编码器，错误码: "sv << status;
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // 获取输入 Surface
        ANativeWindow* inputSurface;
        media_status_t surfaceStatus = AMediaCodec_createInputSurface(codec, &inputSurface);
        if (surfaceStatus != AMEDIA_OK) {
            BOOST_LOG(error) << "无法创建输入Surface，错误码: "sv << surfaceStatus;
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // 将 ANativeWindow 转换为 Java Surface 对象并创建虚拟显示
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空，无法创建 Surface"sv;
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // 将 ANativeWindow 转换为 Java Surface 对象
        jobject javaSurface = ANativeWindow_toSurface(env, inputSurface);
        if (javaSurface == nullptr) {
            BOOST_LOG(error) << "无法将 ANativeWindow 转换为 Surface"sv;
            jvm->DetachCurrentThread();
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // 调用 createVirtualDisplay 方法
        createVirtualDisplay(env, javaSurface);
        
        // 清理 Java Surface 引用
        jvm->DetachCurrentThread();
        
        // 这里可以继续编码循环的其他部分...
    }
}