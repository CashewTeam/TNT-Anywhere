#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <string>
#include "logging.h"
#include "config.h"
#include "nvhttp.h"
#include "globals.h"
#include "sunshine.h"
#include "stream.h"
#include "rtsp.h"
#include "audio.h"
#include "moonlight-common-c/src/Input.h"
#include "video_colorspace.h"

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <boost/endian/buffers.hpp>

using namespace std::literals;

extern "C" {

static std::unique_ptr<logging::deinit_t> deinit;
static JavaVM* jvm = nullptr;
static jclass sunshineServerClass = nullptr;
static jclass sunshineMouseClass = nullptr;
static audio::sample_queue_t samples = nullptr;
static std::atomic_bool serverRunning {false};

// 声明全局变量来存储音频录制状态
static std::thread audioRecordingThread;
static std::atomic_bool audioRecordingActive {false};
static std::atomic_bool stoppingVirtualDisplay {false};
static std::atomic_int encoderBitratePercent {100};
static std::atomic_int encoderBitrateMode {2};
static std::atomic_int encoderComplexity {5};
static std::atomic_int encoderIFrameInterval {3};
static std::atomic_int encoderMaxFps {120};
static std::atomic_bool encoderLowLatency {true};
static std::atomic_bool encoderDisableBFrames {true};
static std::atomic_bool encoderRealtimePriority {true};
static std::atomic_int streamFecPercent {20};

// 缓存常用的方法ID
static jmethodID handleTouchPacketMethod = nullptr;
static jmethodID handleAbsMouseMoveMethod = nullptr;
static jmethodID handleLeftMouseButtonMethod = nullptr;
static jmethodID handleMouseScrollMethod = nullptr;
static jmethodID updateStreamingDebugInfoMethod = nullptr;

// 在全局变量区域添加新的缓存变量
static jclass sunshineKeyboardClass = nullptr;
static jmethodID handleKeyboardMethod = nullptr;

static void resetJavaCaches(JNIEnv *env) {
    if (sunshineServerClass != nullptr) {
        env->DeleteGlobalRef(sunshineServerClass);
        sunshineServerClass = nullptr;
    }
    if (sunshineMouseClass != nullptr) {
        env->DeleteGlobalRef(sunshineMouseClass);
        sunshineMouseClass = nullptr;
    }
    if (sunshineKeyboardClass != nullptr) {
        env->DeleteGlobalRef(sunshineKeyboardClass);
        sunshineKeyboardClass = nullptr;
    }
    handleTouchPacketMethod = nullptr;
    handleAbsMouseMoveMethod = nullptr;
    handleLeftMouseButtonMethod = nullptr;
    handleMouseScrollMethod = nullptr;
    updateStreamingDebugInfoMethod = nullptr;
    handleKeyboardMethod = nullptr;
}

static void shutdownNativeRuntime(JNIEnv *env) {
    if (mail::man) {
        auto shutdown_event = mail::man->event<bool>(mail::shutdown);
        shutdown_event->raise(true);
    }
    task_pool.stop();
    task_pool.join();
    mail::man.reset();
    deinit.reset();
    if (env != nullptr) {
        resetJavaCaches(env);
    }
    serverRunning = false;
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_start(JNIEnv *env, jclass clazz) {
    if (serverRunning.exchange(true)) {
        BOOST_LOG(warning) << "Sunshine native server is already running, skip duplicate start"sv;
        return;
    }
    env->GetJavaVM(&jvm);
    try {

    jclass serverClass = env->FindClass("com/connect_screen/mirror/job/SunshineServer");
    if (serverClass != nullptr) {
        sunshineServerClass = (jclass)env->NewGlobalRef(serverClass);
        updateStreamingDebugInfoMethod = env->GetStaticMethodID(
                sunshineServerClass,
                "updateStreamingDebugInfo",
                "(Ljava/lang/String;)V");
        if (!updateStreamingDebugInfoMethod) {
            BOOST_LOG(warning) << "无法缓存串流调试信息更新方法"sv;
        }
        env->DeleteLocalRef(serverClass);
    } else {
        BOOST_LOG(error) << "无法在启动时找到 SunshineServer 类"sv;
    }
    
    jclass mouseClass = env->FindClass("com/connect_screen/mirror/job/SunshineMouse");
    if (mouseClass != nullptr) {
        sunshineMouseClass = (jclass)env->NewGlobalRef(mouseClass);
        env->DeleteLocalRef(mouseClass);
        
        // 在类引用创建后立即缓存常用方法ID
        handleTouchPacketMethod = env->GetStaticMethodID(sunshineMouseClass, "handleTouchPacket", "(IIIFFFFF)V");
        handleAbsMouseMoveMethod = env->GetStaticMethodID(sunshineMouseClass, "handleAbsMouseMovePacket", "(FFFF)V");
        handleLeftMouseButtonMethod = env->GetStaticMethodID(sunshineMouseClass, "handleLeftMouseButton", "(Z)V");
        handleMouseScrollMethod = env->GetStaticMethodID(sunshineMouseClass, "handleMouseScroll", "(II)V");
        
        if (!handleTouchPacketMethod || !handleAbsMouseMoveMethod || !handleLeftMouseButtonMethod || !handleMouseScrollMethod) {
            BOOST_LOG(warning) << "无法缓存一个或多个输入处理方法ID"sv;
        }
    } else {
        BOOST_LOG(error) << "无法在启动时找到 SunshineMouse 类"sv;
    }
    
    // 在现有类引用创建后添加 SunshineKeyboard 类的缓存
    jclass keyboardClass = env->FindClass("com/connect_screen/mirror/job/SunshineKeyboard");
    if (keyboardClass != nullptr) {
        sunshineKeyboardClass = (jclass)env->NewGlobalRef(keyboardClass);
        env->DeleteLocalRef(keyboardClass);
        
        // 缓存键盘处理方法ID
        handleKeyboardMethod = env->GetStaticMethodID(sunshineKeyboardClass, "handleKeyboardEvent", "(IZI)V");
        
        if (!handleKeyboardMethod) {
            BOOST_LOG(warning) << "无法缓存键盘处理方法ID"sv;
        }
    } else {
        BOOST_LOG(error) << "无法在启动时找到 SunshineKeyboard 类"sv;
    }
    
    deinit = logging::init(1, "/dev/null");
    BOOST_LOG(info) << "start sunshine server"sv;
    mail::man = std::make_shared<safe::mail_raw_t>();
    task_pool.start(1);
    
    std::thread httpThread {nvhttp::start};
    rtsp_stream::rtpThread();
    httpThread.join();
    BOOST_LOG(info) << "sunshine server stopped"sv;
    } catch (const std::exception &e) {
        BOOST_LOG(error) << "Sunshine native start failed with exception: "sv << e.what();
    } catch (...) {
        BOOST_LOG(error) << "Sunshine native start failed with unknown exception"sv;
    }

    shutdownNativeRuntime(env);
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_setSunshineName(JNIEnv *env, jclass clazz, jstring sunshine_name) {
    const char *str = env->GetStringUTFChars(sunshine_name, nullptr);
    config::nvhttp.sunshine_name = str;
    env->ReleaseStringUTFChars(sunshine_name, str);
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
Java_com_connect_1screen_mirror_job_SunshineServer_setEncoderSettings(
        JNIEnv *env,
        jclass clazz,
        jint bitratePercent,
        jint bitrateMode,
        jint complexity,
        jint iFrameInterval,
        jint maxFps,
        jboolean lowLatency,
        jboolean disableBFrames,
        jboolean realtimePriority,
        jint fecPercent) {
    encoderBitratePercent = std::clamp<int>(bitratePercent, 25, 200);
    encoderBitrateMode = std::clamp<int>(bitrateMode, 0, 2);
    encoderComplexity = std::clamp<int>(complexity, 0, 10);
    encoderIFrameInterval = std::clamp<int>(iFrameInterval, 1, 10);
    encoderMaxFps = std::clamp<int>(maxFps, 1, 240);
    encoderLowLatency = lowLatency == JNI_TRUE;
    encoderDisableBFrames = disableBFrames == JNI_TRUE;
    encoderRealtimePriority = realtimePriority == JNI_TRUE;
    streamFecPercent = std::clamp<int>(fecPercent, 0, 50);
    config::stream.fec_percentage = streamFecPercent.load();

    BOOST_LOG(info) << "Encoder settings updated: bitrate="sv
                    << encoderBitratePercent.load() << "% mode="sv
                    << encoderBitrateMode.load() << " complexity="sv
                    << encoderComplexity.load() << " iframe="sv
                    << encoderIFrameInterval.load() << "s maxFps="sv
                    << encoderMaxFps.load() << " lowLatency="sv
                    << encoderLowLatency.load() << " noBFrames="sv
                    << encoderDisableBFrames.load() << " realtimePriority="sv
                    << encoderRealtimePriority.load() << " fec="sv
                    << streamFecPercent.load() << "%";
}

static void callJavaStreamingDebugInfo(JNIEnv *env, const std::string &info) {
    if (sunshineServerClass == nullptr || updateStreamingDebugInfoMethod == nullptr) {
        return;
    }
    jstring jInfo = env->NewStringUTF(info.c_str());
    if (jInfo == nullptr) {
        return;
    }
    env->CallStaticVoidMethod(sunshineServerClass, updateStreamingDebugInfoMethod, jInfo);
    env->DeleteLocalRef(jInfo);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_submitPin(JNIEnv *env, jclass clazz, jstring pin) {
    const char *pinStr = env->GetStringUTFChars(pin, nullptr);
    nvhttp::pin(pinStr, "some-moonlight");
    env->ReleaseStringUTFChars(pin, pinStr);
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_cleanup(JNIEnv *env, jclass clazz) {
    resetJavaCaches(env);
    return;
    if (sunshineServerClass != nullptr) {
        env->DeleteGlobalRef(sunshineServerClass);
        sunshineServerClass = nullptr;
        
        // 清除缓存的方法ID
        handleTouchPacketMethod = nullptr;
        handleAbsMouseMoveMethod = nullptr;
        handleLeftMouseButtonMethod = nullptr;
        handleMouseScrollMethod = nullptr;
        updateStreamingDebugInfoMethod = nullptr;
    }
    
    // 在清理部分添加对 SunshineKeyboard 类的清理
    if (sunshineKeyboardClass != nullptr) {
        env->DeleteGlobalRef(sunshineKeyboardClass);
        sunshineKeyboardClass = nullptr;
        handleKeyboardMethod = nullptr;
    }
    
    // 其他清理工作...
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_startAudioRecording(JNIEnv *env, jclass clazz, jobject audioRecord, jint framesPerPacket) {
    if (audioRecordingActive.exchange(true)) {
        BOOST_LOG(info) << "音频录制线程已在运行，跳过重复启动"sv;
        return;
    }
    if (audioRecordingThread.joinable()) {
        audioRecordingThread.detach();
    }

    // 创建 AudioRecord 的全局引用，以便在线程中使用
    jobject globalAudioRecord = env->NewGlobalRef(audioRecord);
    if (globalAudioRecord == nullptr) {
        BOOST_LOG(error) << "无法创建 AudioRecord 的全局引用"sv;
        audioRecordingActive = false;
        return;
    }
    
    // 获取 AudioRecord 类和方法 ID
    jclass audioRecordClass = env->GetObjectClass(globalAudioRecord);
    if (audioRecordClass == nullptr) {
        BOOST_LOG(error) << "无法获取 AudioRecord 类"sv;
        env->DeleteGlobalRef(globalAudioRecord);
        audioRecordingActive = false;
        return;
    }
    jmethodID readMethod = env->GetMethodID(audioRecordClass, "read", "([FIII)I");
    env->DeleteLocalRef(audioRecordClass);

    if (!readMethod) {
        BOOST_LOG(error) << "无法获取 AudioRecord 方法"sv;
        env->DeleteGlobalRef(globalAudioRecord);
        audioRecordingActive = false;
        return;
    }
    
    // 设置活动标志并启动录制线程
    audioRecordingThread = std::thread([globalAudioRecord, readMethod, framesPerPacket]() {
        JNIEnv *threadEnv;
        jint result = jvm->AttachCurrentThread(&threadEnv, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法将音频线程附加到 JVM"sv;
            audioRecordingActive = false;
            return;
        }
        
        // 创建缓冲区
        jfloatArray buffer = threadEnv->NewFloatArray(framesPerPacket * 2); // 立体声，每帧两个通道
        
        try {
            while (audioRecordingActive) {
                // 读取音频数据
                jint samplesRead = threadEnv->CallIntMethod(globalAudioRecord, readMethod, buffer, 0, framesPerPacket * 2, 0);
                if (threadEnv->ExceptionCheck()) {
                    threadEnv->ExceptionDescribe();
                    threadEnv->ExceptionClear();
                    break;
                }
                
                if (samplesRead > 0) {
                    // 获取缓冲区数据
                    jfloat *audioData = threadEnv->GetFloatArrayElements(buffer, nullptr);
                    if (audioData) {
                        // 将音频数据转换为 std::vector<float>
                        std::vector<float> audioSamples(audioData, audioData + samplesRead);
                        
                        // 将音频数据传递给 Sunshine 的音频处理系统
                        if (samples) {
                            samples->raise(std::move(audioSamples));
                        }

                        // 释放缓冲区
                        threadEnv->ReleaseFloatArrayElements(buffer, audioData, JNI_ABORT);
                    }
                }
            }
        } catch (...) {
            BOOST_LOG(error) << "音频录制过程中发生异常"sv;
        }

        if (buffer != nullptr) {
            threadEnv->DeleteLocalRef(buffer);
        }
        threadEnv->DeleteGlobalRef(globalAudioRecord);
        audioRecordingActive = false;

        // 分离线程
        jvm->DetachCurrentThread();
    });
    audioRecordingThread.detach();
}

JNIEXPORT void JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_enableH265(JNIEnv *env, jclass clazz) {
    video::active_hevc_mode = 2;
}

JNIEXPORT jboolean JNICALL
Java_com_connect_1screen_mirror_job_SunshineServer_exitServer(JNIEnv *env, jclass clazz) {
    if (!serverRunning || !mail::man) {
        return JNI_FALSE;
    }
    auto shutdown_event = mail::man->event<bool>(mail::shutdown);
    auto broadcast_shutdown_event = mail::man->event<bool>(mail::broadcast_shutdown);
    shutdown_event->raise(true);
    broadcast_shutdown_event->raise(true);
    if (stream::session::getRunningSessions() == 0) {
        return JNI_TRUE;
    }
    BOOST_LOG(info) << "退出 Sunshine 服务器"sv;
    for (int i = 0; i < 5; i++) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (stream::session::getRunningSessions() == 0) {
            return JNI_TRUE;
        }
    }
    return JNI_TRUE;
}

}

namespace sunshine_callbacks {
    class ScopedJniEnv {
    public:
        explicit ScopedJniEnv(JavaVM* vm) : vm(vm) {
            if (vm == nullptr) {
                return;
            }
            JNIEnv* currentEnv = nullptr;
            jint status = vm->GetEnv(reinterpret_cast<void**>(&currentEnv), JNI_VERSION_1_6);
            if (status == JNI_OK) {
                env = currentEnv;
                return;
            }
            if (status == JNI_EDETACHED && vm->AttachCurrentThread(&currentEnv, nullptr) == JNI_OK) {
                env = currentEnv;
                attachedHere = true;
            }
        }

        ~ScopedJniEnv() {
            if (attachedHere && vm != nullptr) {
                vm->DetachCurrentThread();
            }
        }

        JNIEnv* get() const {
            return env;
        }

    private:
        JavaVM* vm = nullptr;
        JNIEnv* env = nullptr;
        bool attachedHere = false;
    };

    void callJavaOnPinRequested() {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        
        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用为空"sv;
            return;
        }

        ScopedJniEnv scopedEnv(jvm);
        JNIEnv *env = scopedEnv.get();
        if (env == nullptr) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        jmethodID onPinRequestedMethod = env->GetStaticMethodID(sunshineServerClass, "onPinRequested", "()V");
        if (onPinRequestedMethod == nullptr) {
            BOOST_LOG(error) << "找不到 onPinRequested 方法"sv;
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, onPinRequestedMethod);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

    }

    void createVirtualDisplay(JNIEnv *env, jint width, jint height, jint frameRate, jint packetDuration, jobject surface, jboolean shouldMute) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用为空"sv;
            return;
        }

        jmethodID createVirtualDisplayMethod = env->GetStaticMethodID(sunshineServerClass, "createVirtualDisplay", "(IIIILandroid/view/Surface;Z)V");
        if (createVirtualDisplayMethod == nullptr) {
            BOOST_LOG(error) << "找不到 createVirtualDisplay 方法"sv;
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, createVirtualDisplayMethod, width, height, frameRate, packetDuration, surface, shouldMute);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    void stopVirtualDisplay() {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        if (stoppingVirtualDisplay.exchange(true)) {
            BOOST_LOG(info) << "Moonlight 投屏正在停止，跳过重复停止请求"sv;
            return;
        }

        ScopedJniEnv scopedEnv(jvm);
        JNIEnv *env = scopedEnv.get();
        if (env == nullptr) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            stoppingVirtualDisplay = false;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用为空"sv;
            stoppingVirtualDisplay = false;
            return;
        }

        jmethodID stopVirtualDisplayMethod = env->GetStaticMethodID(sunshineServerClass, "stopVirtualDisplay", "()V");
        if (stopVirtualDisplayMethod == nullptr) {
            BOOST_LOG(error) << "找不到 stopVirtualDisplay 方法"sv;
            stoppingVirtualDisplay = false;
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, stopVirtualDisplayMethod);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        stoppingVirtualDisplay = false;
    }

    void showEncoderError(const char* errorMessage) {
        if (jvm == nullptr || sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "JVM 或 SunshineServer 类引用为空"sv;
            return;
        }

        ScopedJniEnv scopedEnv(jvm);
        JNIEnv *env = scopedEnv.get();
        if (env == nullptr) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        jmethodID showErrorMethod = env->GetStaticMethodID(sunshineServerClass, "showEncoderError", "(Ljava/lang/String;)V");
        if (showErrorMethod == nullptr) {
            BOOST_LOG(error) << "找不到 showEncoderError 方法"sv;
            return;
        }

        jstring jErrorMessage = env->NewStringUTF(errorMessage);
        env->CallStaticVoidMethod(sunshineServerClass, showErrorMethod, jErrorMessage);
        env->DeleteLocalRef(jErrorMessage);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

    }

    void captureVideoLoop(void *channel_data, safe::mail_t mail, const video::config_t& config, const audio::config_t& audioConfig) {
        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }
        auto shutdown_event = mail->event<bool>(mail::shutdown);
        auto idr_events = mail->event<bool>(mail::idr);
        // 添加更详细的客户端配置日志
        BOOST_LOG(info) << "客户端请求视频配置:"sv;
        BOOST_LOG(info) << "  - 分辨率: "sv << config.width << "x"sv << config.height;
        BOOST_LOG(info) << "  - 帧率: "sv << config.framerate;
        BOOST_LOG(info) << "  - 视频格式: "sv << (config.videoFormat == 1 ? "HEVC" : "H.264");
        BOOST_LOG(info) << "  - 色度采样: "sv << (config.chromaSamplingType == 1 ? "YUV 4:4:4" : "YUV 4:2:0");
        BOOST_LOG(info) << "  - 动态范围: "sv << (config.dynamicRange ? "HDR" : "SDR");
        BOOST_LOG(info) << "  - 编码器色彩空间模式: 0x"sv << std::hex << config.encoderCscMode << std::dec;
        
        // 获取并记录详细的色彩空间信息
        bool isHdr = false;
        video::sunshine_colorspace_t colorspace = colorspace_from_client_config(config, isHdr);
        BOOST_LOG(info) << "色彩空间配置:"sv;
        BOOST_LOG(info) << "  - 色彩空间: "sv << static_cast<int>(colorspace.colorspace);
        BOOST_LOG(info) << "  - 位深度: "sv << colorspace.bit_depth;
        BOOST_LOG(info) << "  - 色彩范围: "sv << (colorspace.full_range ? "Full" : "Limited");
        
        // 创建 MediaFormat
        AMediaFormat *format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, config.videoFormat == 1 ? "video/hevc" : "video/avc");

        const auto encoderMaxFrameRate = encoderMaxFps.load();
        const auto encodeFrameRate = std::clamp(config.framerate, 1, encoderMaxFrameRate);
        const auto configuredBitratePercent = encoderBitratePercent.load();
        const auto configuredBitrateKbps = std::max(1, config.bitrate * configuredBitratePercent / 100);
        const auto configuredBitrateMode = encoderBitrateMode.load();
        const auto configuredComplexity = encoderComplexity.load();
        const auto configuredIFrameInterval = encoderIFrameInterval.load();
        const auto configuredLowLatency = encoderLowLatency.load();
        const auto configuredDisableBFrames = encoderDisableBFrames.load();
        const auto configuredRealtimePriority = encoderRealtimePriority.load();
        const auto configuredEncoderPriority = configuredRealtimePriority ? 0 : 1;
        config::stream.fec_percentage = streamFecPercent.load();
        // 基本配置保持不变
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, config.width);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, config.height);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, configuredBitrateKbps * 1000);
        AMediaFormat_setInt32(format, "bitrate-mode", configuredBitrateMode);
        AMediaFormat_setInt32(format, "priority", configuredEncoderPriority);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_OPERATING_RATE, encodeFrameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CAPTURE_RATE, encodeFrameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, encodeFrameRate);
        AMediaFormat_setInt32(format, "max-fps-to-encoder", encodeFrameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, configuredIFrameInterval); // 关键帧间隔(秒)
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 2130708361); // COLOR_FormatSurface
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COMPLEXITY, configuredComplexity);
        if (configuredLowLatency) {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_LATENCY, 0); // 最低延迟
            AMediaFormat_setInt32(format, "vendor.qti-ext-enc-low-latency.enable", 1);
        }
        if (configuredDisableBFrames) {
            AMediaFormat_setInt32(format, "max-bframes", 0);
            AMediaFormat_setInt32(format, "vendor.qti-ext-enc-bframes.num-bframes", 0);
        }

        // 设置编码配置
        if (config.videoFormat == 1) {
            if (colorspace.bit_depth == 10) {
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PROFILE, 2); // HEVCProfileMain10
            } else {
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PROFILE, 1); // HEVCProfileMain
            }
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_LEVEL, 65536); // HEVCMainTierLevel51
        } else {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PROFILE, 0x08); // HIGH profile
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_LEVEL, 0x200); // Level 4.2
        }

        // 设置色彩空间
        switch (colorspace.colorspace) {
            case video::colorspace_e::rec601:
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, 4); // COLOR_STANDARD_BT601_NTSC
                break;
            case video::colorspace_e::rec709:
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, 1); // COLOR_STANDARD_BT709
                break;
            case video::colorspace_e::bt2020:
            case video::colorspace_e::bt2020sdr:
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, 6); // COLOR_STANDARD_BT2020
                break;
        }

        // 设置色彩范围
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_RANGE, 
            colorspace.full_range ? 1 : 2); // 1=FULL, 2=LIMITED

        // 设置位深度
        if (isHdr) {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_TRANSFER, 6); // COLOR_TRANSFER_ST2084
        } else {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_TRANSFER, 3); // COLOR_TRANSFER_SDR_VIDEO
        }

        // 打印最终的媒体格式颜色配置
        int32_t colorStandard = 0, colorRange = 0, colorTransfer = 0;
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, &colorStandard);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_RANGE, &colorRange);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_TRANSFER, &colorTransfer);
        
        BOOST_LOG(info) << "最终媒体格式颜色配置:"sv;
        BOOST_LOG(info) << "  - COLOR_STANDARD: "sv << colorStandard;
        BOOST_LOG(info) << "  - COLOR_RANGE: "sv << colorRange << (colorRange == 1 ? " (FULL)" : " (LIMITED)");
        BOOST_LOG(info) << "  - COLOR_TRANSFER: "sv << colorTransfer;

        // 创建编码器
        AMediaCodec *codec = AMediaCodec_createEncoderByType(config.videoFormat == 1 ? "video/hevc" : "video/avc");
        if (!codec) {
            // 创建编码器
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1920);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 1080);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_OPERATING_RATE, encodeFrameRate);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CAPTURE_RATE, encodeFrameRate);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, encodeFrameRate);
            AMediaFormat_setInt32(format, "max-fps-to-encoder", encodeFrameRate);
            codec = AMediaCodec_createEncoderByType("video/avc");
        }
        if (!codec) {
            BOOST_LOG(error) << "无法创建编码器"sv;
            AMediaFormat_delete(format);
            jvm->DetachCurrentThread();
            return;
        }
        
        // 配置编码器
        media_status_t status = AMediaCodec_configure(codec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
        if (status != AMEDIA_OK) {
            std::string errorMsg = "无法配置编码器，错误码: " + std::to_string(status);
            BOOST_LOG(error) << errorMsg;
            showEncoderError(errorMsg.c_str());
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            jvm->DetachCurrentThread();
            return;
        }
        
        // 获取输入 Surface
        ANativeWindow* inputSurface;
        media_status_t surfaceStatus = AMediaCodec_createInputSurface(codec, &inputSurface);
        if (surfaceStatus != AMEDIA_OK) {
            BOOST_LOG(error) << "无法创建输入Surface，错误码: "sv << surfaceStatus;
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            jvm->DetachCurrentThread();
            return;
        }
        
        // 将 ANativeWindow 转换为 Java Surface 对象并创建虚拟显示
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空，无法创建 Surface"sv;
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            jvm->DetachCurrentThread();
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

        bool shouldMute = true;
        if (audioConfig.flags[audio::config_t::HOST_AUDIO]) {
            BOOST_LOG(info) << "音频配置: 声音将在主机端(Sunshine服务器)播放"sv;
            shouldMute = false;
        } else {
            BOOST_LOG(info) << "音频配置: 声音将在客户端(Moonlight)播放"sv;
        }

        auto buildDebugInfo = [&](const char *status,
                                  double actualFps,
                                  double encodedKbps,
                                  int64_t frames,
                                  int64_t keyFrames,
                                  uint64_t totalBytes,
                                  int64_t noOutputMs) {
            std::ostringstream out;
            out << "Encoder config\n"
                << "Status: " << status << "\n"
                << "Codec: " << (config.videoFormat == 1 ? "HEVC" : "H.264") << "\n"
                << "Size: " << config.width << "x" << config.height << "\n"
                << "Client FPS: " << config.framerate << " / Encoder FPS: " << encodeFrameRate << "\n"
                << "Target bitrate: " << configuredBitrateKbps << " kbps (" << configuredBitratePercent << "%)\n"
                << "Bitrate mode: " << configuredBitrateMode << " / Complexity: " << configuredComplexity << "\n"
                << "Priority hint: " << (configuredRealtimePriority ? "realtime" : "normal")
                << " / Low latency: " << (configuredLowLatency ? "on" : "off") << "\n"
                << "B-frames: " << (configuredDisableBFrames ? "off" : "codec default")
                << " / FEC: " << config::stream.fec_percentage << "%\n"
                << "I-frame interval: " << configuredIFrameInterval << "s\n"
                << "Audio: " << (shouldMute ? "client" : "phone") << "\n"
                << "Color: standard=" << colorStandard
                << " range=" << colorRange
                << " transfer=" << colorTransfer << "\n\n"
                << "Realtime\n"
                << std::fixed << std::setprecision(1)
                << "Output FPS: " << actualFps << "\n"
                << "Encoded bitrate: " << encodedKbps << " kbps\n"
                << "Frames: " << frames << " / Keyframes: " << keyFrames << "\n"
                << "Encoded bytes: " << totalBytes << "\n"
                << "No-output time: " << noOutputMs << " ms";
            return out.str();
        };
        callJavaStreamingDebugInfo(env, buildDebugInfo("initializing", 0, 0, 0, 0, 0, 0));
        
        // 调用 createVirtualDisplay 方法，传递 shouldMute 参数
        createVirtualDisplay(env, config.width, config.height, config.framerate, audioConfig.packetDuration, javaSurface, shouldMute);
        
        // 启动编码器
        status = AMediaCodec_start(codec);
        if (status != AMEDIA_OK) {
            BOOST_LOG(error) << "无法启动编码器，错误码: "sv << status;
            env->DeleteLocalRef(javaSurface);
            jvm->DetachCurrentThread();
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        BOOST_LOG(info) << "MediaCodec 编码器已启动"sv;
        callJavaStreamingDebugInfo(env, buildDebugInfo("running", 0, 0, 0, 0, 0, 0));
        
        // 编码循环
        std::vector<uint8_t> codecConfigData;  // 用于存储完整的编解码器配置数据
        int64_t frameIndex = 0;
        int64_t keyFrameCount = 0;
        uint64_t totalEncodedBytes = 0;
        uint64_t intervalEncodedBytes = 0;
        int64_t lastStatsFrameIndex = 0;
        auto lastOutputAt = std::chrono::steady_clock::now();
        auto lastNoOutputLogAt = lastOutputAt;
        auto lastStatsAt = lastOutputAt;
        
        while (!shutdown_event->peek()) {
            bool requested_idr_frame = false;
            if (idr_events->peek()) {
                requested_idr_frame = true;
                idr_events->pop();
            }

            if (requested_idr_frame) {
                // 使用 Bundle 参数请求同步帧（IDR帧）
                AMediaFormat* params = AMediaFormat_new();
                AMediaFormat_setInt32(params, "request-sync", 0);
                media_status_t status = AMediaCodec_setParameters(codec, params);
                if (status != AMEDIA_OK) {
                    BOOST_LOG(warning) << "请求 IDR 帧失败，错误码: "sv << status;
                } else {
                    BOOST_LOG(info) << "已请求 IDR 帧"sv;
                }
                AMediaFormat_delete(params);
            }
            
            // 获取输出缓冲区，使用1秒的超时时间
            AMediaCodecBufferInfo bufferInfo;
            ssize_t outputBufferIndex = AMediaCodec_dequeueOutputBuffer(codec, &bufferInfo, 1000000); // 1秒 = 1000000微秒
            
            if (outputBufferIndex >= 0) {
                lastOutputAt = std::chrono::steady_clock::now();
                // 获取到有效的输出缓冲区
                size_t bufferSize = bufferInfo.size;
                uint8_t* buffer = nullptr;
                size_t out_size = 0;
                
                // 获取缓冲区数据
                buffer = AMediaCodec_getOutputBuffer(codec, outputBufferIndex, &out_size);
                if (buffer != nullptr) {
                    if (bufferInfo.offset < 0 || bufferInfo.size < 0 ||
                        static_cast<size_t>(bufferInfo.offset) + static_cast<size_t>(bufferInfo.size) > out_size) {
                        BOOST_LOG(error) << "编码器输出缓冲区范围非法: offset="sv << bufferInfo.offset
                                         << " size="sv << bufferInfo.size << " capacity="sv << out_size;
                        AMediaCodec_releaseOutputBuffer(codec, outputBufferIndex, false);
                        break;
                    }
                    uint8_t *payload = buffer + bufferInfo.offset;

                    // 处理编码后的数据
                    if (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                        // 这是编解码器配置数据（SPS/PPS）
                        BOOST_LOG(info) << "收到编解码器配置数据，大小: "sv << bufferSize;
                        
                        // 直接保存整个配置数据
                        codecConfigData.assign(payload, payload + bufferSize);
                        BOOST_LOG(info) << "保存完整的编解码器配置数据，大小: "sv << codecConfigData.size();
                    } else {
                        // 这是正常的编码帧
                        bool isKeyFrame = (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_KEY_FRAME) != 0;
                        BOOST_LOG(verbose) << "收到" << (isKeyFrame ? "关键帧" : "普通帧") << "，大小: "sv << bufferSize;
                        frameIndex++;
                        size_t emittedFrameBytes = 0;
                        
                        if(isKeyFrame) {
                            keyFrameCount++;
                            // 对于关键帧，需要在数据前附加编解码器配置数据
                            if (!codecConfigData.empty()) {
                                // 创建包含配置数据和关键帧的完整数据
                                std::vector<uint8_t> frameData(codecConfigData.size() + bufferSize);
                                std::memcpy(frameData.data(), codecConfigData.data(), codecConfigData.size());
                                std::memcpy(frameData.data() + codecConfigData.size(), payload, bufferSize);

                                BOOST_LOG(verbose) << "发送关键帧(带配置数据)，总大小: "sv << frameData.size();
                                // 发送完整的关键帧数据
                                emittedFrameBytes = frameData.size();
                                stream::postFrame(std::move(frameData), frameIndex, true, channel_data);
                            } else {
                                BOOST_LOG(error) << "没有编解码器配置数据，无法发送完整关键帧"sv;
                            }
                        } else {
                            std::vector<uint8_t> frameData(bufferSize);
                            std::memcpy(frameData.data(), payload, bufferSize);
                            emittedFrameBytes = frameData.size();
                            stream::postFrame(std::move(frameData), frameIndex, false, channel_data);
                        }
                        if (emittedFrameBytes > 0) {
                            totalEncodedBytes += emittedFrameBytes;
                            intervalEncodedBytes += emittedFrameBytes;
                            auto statsNow = std::chrono::steady_clock::now();
                            auto statsElapsed = std::chrono::duration<double>(statsNow - lastStatsAt).count();
                            if (statsElapsed >= 1.0) {
                                double actualFps = (frameIndex - lastStatsFrameIndex) / statsElapsed;
                                double encodedKbps = (intervalEncodedBytes * 8.0) / 1000.0 / statsElapsed;
                                auto noOutputMs = std::chrono::duration_cast<std::chrono::milliseconds>(statsNow - lastOutputAt).count();
                                callJavaStreamingDebugInfo(env, buildDebugInfo(
                                        "running",
                                        actualFps,
                                        encodedKbps,
                                        frameIndex,
                                        keyFrameCount,
                                        totalEncodedBytes,
                                        noOutputMs));
                                intervalEncodedBytes = 0;
                                lastStatsFrameIndex = frameIndex;
                                lastStatsAt = statsNow;
                            }
                        }
                    }
                }
                
                // 释放输出缓冲区
                AMediaCodec_releaseOutputBuffer(codec, outputBufferIndex, false);
            } else if (outputBufferIndex == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                auto now = std::chrono::steady_clock::now();
                if (now - lastNoOutputLogAt >= std::chrono::seconds(2)) {
                    auto noOutputMs = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastOutputAt).count();
                    BOOST_LOG(warning) << "MediaCodec 已 "sv << noOutputMs << "ms 没有输出帧，输入 Surface 可能没有收到镜像画面"sv;
                    auto statsElapsed = std::chrono::duration<double>(now - lastStatsAt).count();
                    double actualFps = statsElapsed > 0 ? (frameIndex - lastStatsFrameIndex) / statsElapsed : 0;
                    double encodedKbps = statsElapsed > 0 ? (intervalEncodedBytes * 8.0) / 1000.0 / statsElapsed : 0;
                    callJavaStreamingDebugInfo(env, buildDebugInfo(
                            "waiting for frames",
                            actualFps,
                            encodedKbps,
                            frameIndex,
                            keyFrameCount,
                            totalEncodedBytes,
                            noOutputMs));
                    intervalEncodedBytes = 0;
                    lastStatsFrameIndex = frameIndex;
                    lastStatsAt = now;
                    lastNoOutputLogAt = now;
                }
                continue;
            } else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                // 输出格式已更改
                AMediaFormat* format = AMediaCodec_getOutputFormat(codec);
                BOOST_LOG(info) << "编码器输出格式已更改"sv;
                // 可以从format中获取更多信息
                AMediaFormat_delete(format);
            } else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                // 输出缓冲区已更改
                BOOST_LOG(info) << "编码器输出缓冲区已更改"sv;
                // 在较新的 NDK 版本中，这个事件通常可以忽略，因为 AMediaCodec_getOutputBuffer 会自动处理缓冲区变化
            } else {
                // 出错
                BOOST_LOG(error) << "编码器出错，错误码: "sv << outputBufferIndex;
                break;
            }
        }

        stopVirtualDisplay();
        callJavaStreamingDebugInfo(env, buildDebugInfo(
                "stopped",
                0,
                0,
                frameIndex,
                keyFrameCount,
                totalEncodedBytes,
                0));
        // 停止编码器
        AMediaCodec_stop(codec);
        
        // 清理资源
        ANativeWindow_release(inputSurface);
        AMediaCodec_delete(codec);
        AMediaFormat_delete(format);
        
        // 清理 Java Surface 引用
        env->DeleteLocalRef(javaSurface);
        jvm->DetachCurrentThread();
    }

    void captureAudioLoop(void *channel_data, safe::mail_t mail, const audio::config_t& config) {
        samples = std::make_shared<audio::sample_queue_t::element_type>(30);
        std::thread encoderThread {audio::encodeThread, samples, config, channel_data};

        auto shutdown_event = mail->event<bool>(mail::shutdown);
        shutdown_event->view();

        audioRecordingActive = false;
        samples->stop();
        if (encoderThread.joinable()) {
            encoderThread.join();
        }
        samples = nullptr;
    }

    float from_netfloat(netfloat f) {
        return boost::endian::endian_load<float, sizeof(float), boost::endian::order::little>(f);
    }

    void callJavaOnTouch(SS_TOUCH_PACKET* touchPacket) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        
        if (sunshineMouseClass == nullptr || handleTouchPacketMethod == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用或方法ID为空"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        // 使用缓存的方法ID
        env->CallStaticVoidMethod(sunshineMouseClass, handleTouchPacketMethod,
                                 static_cast<int>(touchPacket->eventType),
                                 static_cast<int>(touchPacket->rotation),
                                 static_cast<int>(touchPacket->pointerId),
                                 from_netfloat(touchPacket->x),
                                 from_netfloat(touchPacket->y),
                                 from_netfloat(touchPacket->pressureOrDistance),
                                 from_netfloat(touchPacket->contactAreaMajor),
                                 from_netfloat(touchPacket->contactAreaMinor));

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnAbsMouseMove(NV_ABS_MOUSE_MOVE_PACKET* packet) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        
        if (sunshineMouseClass == nullptr || handleAbsMouseMoveMethod == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用或方法ID为空"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        // 使用缓存的方法ID
        float x = util::endian::big(packet->x);
        float y = util::endian::big(packet->y);
        float width = (float) util::endian::big(packet->width);
        float height = (float) util::endian::big(packet->height);
        
        BOOST_LOG(info) << "调用 Java 处理鼠标移动: "sv << x << ","sv << y << " 在 "sv << width << "*"sv << height << " 范围内";

        env->CallStaticVoidMethod(sunshineMouseClass, handleAbsMouseMoveMethod, x, y, width, height);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnMouseButton(std::uint8_t button, bool release) {
        BOOST_LOG(info) << "on mouse button "sv << static_cast<int>(button) << " release "sv << release;
        
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        
        if (sunshineMouseClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer 类引用为空"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        // 根据按钮类型调用不同的 Java 方法
        if (button == BUTTON_LEFT && handleLeftMouseButtonMethod != nullptr) {
            env->CallStaticVoidMethod(sunshineMouseClass, handleLeftMouseButtonMethod, release);
        } else {
            // 对于其他按钮类型，可以添加更多的处理逻辑
            BOOST_LOG(info) << "未处理的鼠标按钮类型: "sv << static_cast<int>(button);
        }

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnMouseScroll(int verticalAmount, int horizontalAmount) {
        BOOST_LOG(info) << "on mouse scroll vertical "sv << verticalAmount << " horizontal "sv << horizontalAmount;

        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 鎸囬拡涓虹┖"sv;
            return;
        }

        if (sunshineMouseClass == nullptr || handleMouseScrollMethod == nullptr) {
            BOOST_LOG(error) << "SunshineMouse 绫诲紩鐢ㄦ垨婊氳疆鏂规硶ID涓虹┖"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "鏃犳硶闄勫姞鍒?Java 绾跨▼"sv;
            return;
        }

        env->CallStaticVoidMethod(sunshineMouseClass, handleMouseScrollMethod, verticalAmount, horizontalAmount);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnConnectScreenClientDiscovered(std::string connectScreenClient) {
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

        jmethodID onClientDiscoveredMethod = env->GetStaticMethodID(sunshineServerClass, "onConnectScreenClientDiscovered", "(Ljava/lang/String;)V");
        if (onClientDiscoveredMethod == nullptr) {
            BOOST_LOG(error) << "找不到 onConnectScreenClientDiscovered 方法"sv;
            jvm->DetachCurrentThread();
            return;
        }

        jstring jClientName = env->NewStringUTF(connectScreenClient.c_str());
        env->CallStaticVoidMethod(sunshineServerClass, onClientDiscoveredMethod, jClientName);
        env->DeleteLocalRef(jClientName);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaSetConnectScreenServerUuid(std::string uuid) {
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

        jmethodID setServerUuidMethod = env->GetStaticMethodID(sunshineServerClass, "setConnectScreenServerUuid", "(Ljava/lang/String;)V");
        if (setServerUuidMethod == nullptr) {
            BOOST_LOG(error) << "找不到 setConnectScreenServerUuid 方法"sv;
            jvm->DetachCurrentThread();
            return;
        }

        jstring jUuid = env->NewStringUTF(uuid.c_str());
        env->CallStaticVoidMethod(sunshineServerClass, setServerUuidMethod, jUuid);
        env->DeleteLocalRef(jUuid);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnKeyboard(uint16_t modcode, bool release, uint8_t flags) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM 指针为空"sv;
            return;
        }
        
        if (sunshineKeyboardClass == nullptr || handleKeyboardMethod == nullptr) {
            BOOST_LOG(error) << "SunshineKeyboard 类引用或方法ID为空"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "无法附加到 Java 线程"sv;
            return;
        }

        // 直接使用缓存的类引用和方法ID
        env->CallStaticVoidMethod(sunshineKeyboardClass, handleKeyboardMethod, 
            static_cast<jint>(modcode),
            static_cast<jboolean>(release),
            static_cast<jint>(flags));

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }
}
