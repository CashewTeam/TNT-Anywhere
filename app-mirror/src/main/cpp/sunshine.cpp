#include <jni.h>
#include <string>
#include "logging.h"
#include "config.h"
#include "nvhttp.h"
#include "globals.h"
#include "sunshine.h"
#include "stream.h"
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
    static const int FRAMERATE = 60;
    static const int BITRATE = 8000000; // 8 Mbps

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
        
        // 编码循环
        bool isRunning = true;
        std::vector<uint8_t> spsData;
        std::vector<uint8_t> ppsData;
        int64_t frameIndex = 0;
        
        while (isRunning) {
            // 获取输出缓冲区
            AMediaCodecBufferInfo bufferInfo;
            ssize_t outputBufferIndex = AMediaCodec_dequeueOutputBuffer(codec, &bufferInfo, -1);
            
            if (outputBufferIndex >= 0) {
                // 获取到有效的输出缓冲区
                size_t bufferSize = bufferInfo.size;
                uint8_t* buffer = nullptr;
                size_t out_size = 0;
                
                // 获取缓冲区数据
                buffer = AMediaCodec_getOutputBuffer(codec, outputBufferIndex, &out_size);
                if (buffer != nullptr) {
                    // 处理编码后的数据
                    if (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                        // 这是编解码器配置数据（SPS/PPS）
                        BOOST_LOG(info) << "收到编解码器配置数据，大小: "sv << bufferSize << ", " << out_size;
                        // 保存SPS/PPS数据用于流传输
                        spsData.clear();
                        ppsData.clear();
                        
                        // 解析SPS和PPS数据
                        // H.264编码配置数据通常格式为: [0, 0, 0, 1, SPS, 0, 0, 0, 1, PPS]
                        uint8_t* p = buffer;
                        int remaining = bufferSize;
                        
                        while (remaining > 4) {
                            // 查找起始码 0x00 0x00 0x00 0x01
                            if (p[0] == 0 && p[1] == 0 && p[2] == 0 && p[3] == 1) {
                                p += 4;
                                remaining -= 4;
                                
                                // 获取NAL单元类型 (5位，在第一个字节的低5位)
                                uint8_t nalType = p[0] & 0x1F;
                                
                                // 查找下一个起始码或到达缓冲区末尾
                                uint8_t* nextNal = p + 1;
                                int nalSize = 1;
                                while (nalSize < remaining - 3) {
                                    if (nextNal[0] == 0 && nextNal[1] == 0 && nextNal[2] == 0 && nextNal[3] == 1) {
                                        break;
                                    }
                                    nextNal++;
                                    nalSize++;
                                }
                                
                                // 根据NAL类型保存SPS或PPS
                                if (nalType == 7) { // SPS
                                    spsData.assign(p, p + nalSize);
                                    BOOST_LOG(info) << "保存SPS数据，大小: "sv << nalSize;
                                } else if (nalType == 8) { // PPS
                                    ppsData.assign(p, p + nalSize);
                                    BOOST_LOG(info) << "保存PPS数据，大小: "sv << nalSize;
                                }
                                
                                p = nextNal;
                                remaining = bufferSize - (p - buffer);
                            } else {
                                p++;
                                remaining--;
                            }
                        }
                    } else {
                        // 这是正常的编码帧
                        bool isKeyFrame = (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_KEY_FRAME) != 0;
                        BOOST_LOG(info) << "收到" << (isKeyFrame ? "关键帧" : "普通帧") << "，大小: "sv << bufferSize;
                        frameIndex++;
                        if(isKeyFrame) {
                            // 对于关键帧，需要在数据前附加SPS和PPS
                            if (!spsData.empty() && !ppsData.empty()) {
                                // 创建包含SPS、PPS和关键帧的完整数据
                                std::vector<uint8_t> frameData;
                                
                                // 添加SPS
                                frameData.insert(frameData.end(), spsData.begin(), spsData.end());
                                
                                // 添加PPS
                                frameData.insert(frameData.end(), ppsData.begin(), ppsData.end());
                                
                                // 添加关键帧数据
                                frameData.insert(frameData.end(), buffer, buffer + bufferSize);

                                BOOST_LOG(info) << "发送关键帧(带SPS/PPS)，总大小: "sv << frameData.size();
                                // 发送完整的关键帧数据
                                stream::postFrame(std::move(frameData), frameIndex, true);
                            } else {
                                BOOST_LOG(error) << "没有SPS/PPS数据，无法发送完整关键帧"sv;
                            }
                        } else {
                            std::vector<uint8_t> frameData;
                            frameData.insert(frameData.end(), buffer, buffer + bufferSize);
                            stream::postFrame(std::move(frameData), frameIndex, false);
                        }
                    }
                }
                
                // 释放输出缓冲区
                AMediaCodec_releaseOutputBuffer(codec, outputBufferIndex, false);
            } else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                // 输出格式已更改
                AMediaFormat* format = AMediaCodec_getOutputFormat(codec);
                BOOST_LOG(info) << "编码器输出格式已更改"sv;
                // 可以从format中获取更多信息
                AMediaFormat_delete(format);
            } else {
                // 出错
                BOOST_LOG(error) << "编码器出错，错误码: "sv << outputBufferIndex;
                isRunning = false;
            }
            
            // 这里可以添加停止条件
            // if (shouldStop) isRunning = false;
        }
        
        // 停止编码器
        AMediaCodec_stop(codec);
        
        // 清理资源
        ANativeWindow_release(inputSurface);
        AMediaCodec_delete(codec);
        AMediaFormat_delete(format);
        
        // 清理 Java Surface 引用
        jvm->DetachCurrentThread();
    }
}