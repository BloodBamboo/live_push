#include <jni.h>
#include <string>
#include "librtmp/rtmp.h"
#include "x264.h"
#include "VideoChannel.h"
#include "SafeQueue.cpp"
#include "AudioChannel.h"


VideoChannel *_videoChannel = nullptr;
AudioChannel *_audioChannel = nullptr;
SafeQueue<RTMPPacket *> packets = SafeQueue<RTMPPacket *>();
uint32_t start_time;
int isStart = 0;
pthread_t pid;
int readyPushing = 0;

void releasePackets(RTMPPacket *&packet) {
    if (packet) {
        RTMPPacket_Free(packet);
        delete packet;
        packet = nullptr;
    }
}

void callback(RTMPPacket *packet) {
    if (packet) {
        //设置时间戳
        packet->m_nTimeStamp = RTMP_GetTime() - start_time;
        packets.push(packet);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1init(JNIEnv *env, jobject thiz) {
    //准备一个Video编码器的工具类 ：进行编码
    _videoChannel = new VideoChannel;
    _videoChannel->setVideoCallback(callback);
    _audioChannel = new AudioChannel;
    _audioChannel->setAudioCallback(callback);
    //准备一个队列,打包好的数据 放入队列，在线程中统一的取出数据再发送给服务器
    packets.setReleaseCallback(releasePackets);
}

void *start(void *args) {
    char *url = static_cast<char *>(args);
    RTMP *rtmp = nullptr;
    do {
        //创建rtmp对象指针
        rtmp = RTMP_Alloc();
        if (!rtmp) {
            LOGE("alloc rtmp失败");
            break;
        }
//        初始化temp
        RTMP_Init(rtmp);
        int ret = RTMP_SetupURL(rtmp, url);
        if (!ret) {
            LOGE("设置地址失败:%s", url);
            break;
        }
//5s超时时间
        rtmp->Link.timeout = 5;
//        开启写入功能
        RTMP_EnableWrite(rtmp);
//        连接服务器
        ret = RTMP_Connect(rtmp, 0);
        if (!ret) {
            LOGE("连接服务器失败:%s", url);
            break;
        }
        ret = RTMP_ConnectStream(rtmp, 0);
        if (!ret) {
            LOGE("连接流失败:%s", url);
            break;
        }
//记录一个开始时间
        start_time = RTMP_GetTime();
        //表示可以开始推流了
        readyPushing = 1;
//        设置队列开始工作
        packets.setWork(1);
        RTMPPacket *packet = 0;
        while (readyPushing) {
            packets.pop(packet);
            if (!readyPushing) {
                LOGE("结束readyPushing%d", readyPushing);
                break;
            }
            if (!packet) {
                continue;
            }
            packet->m_nInfoField2 = rtmp->m_stream_id;
            //发送rtmp包 1：队列
            // 意外断网？发送失败，rtmpdump 内部会调用RTMP_Close
            // RTMP_Close 又会调用 RTMP_SendPacket
            // RTMP_SendPacket  又会调用 RTMP_Close
            // 将rtmp.c 里面WriteN方法的 Rtmp_Close注释掉
            ret = RTMP_SendPacket(rtmp, packet, 1);
            releasePackets(packet);
            if (!ret) {
                LOGE("发送失败");
                break;
            }
        }

        releasePackets(packet);
    } while (0);
    LOGE("结束跳出循环readyPushing%d", readyPushing);
    isStart = 0;
    readyPushing = 0;

    packets.setWork(0);
    packets.clear();
    if (rtmp) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
    }
    delete (url);
    LOGE("正式结束readyPushing%d", readyPushing);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1start(JNIEnv *env, jobject thiz, jstring path) {
    if (isStart) {
        return;
    }
    isStart = 1;
    const char *temp_path = env->GetStringUTFChars(path, nullptr);
    char *url = new char[strlen(temp_path) + 1];
    strcpy(url, temp_path);
    pthread_create(&pid, nullptr, start, url);
    env->ReleaseStringUTFChars(path, temp_path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1setVideoEncInfo(JNIEnv *env, jobject thiz,
                                                               jint width, jint height, jint fps,
                                                               jint bitrate) {
    if (_videoChannel) {
        _videoChannel->setVideoEncInfo(width, height, fps, bitrate);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1pushVideo(JNIEnv *env, jobject thiz,
                                                         jbyteArray data) {
    if (!_videoChannel || !readyPushing) {
        LOGE("视频推送数据失败readyPushing%d", readyPushing);
        return;
    }

    jbyte *temp_data = env->GetByteArrayElements(data, 0);
    _videoChannel->encodeData(temp_data);
    env->ReleaseByteArrayElements(data, temp_data, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1stop(JNIEnv *env, jobject thiz) {
    readyPushing = 0;
    //关闭队列工作
    packets.setWork(0);
    pthread_join(pid, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1release(JNIEnv *env, jobject thiz) {
    DELETE(_videoChannel);
    DELETE(_audioChannel);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1setAudioEncInfo(JNIEnv *env, jobject thiz,
                                                               jint sample_rate_in_hz,
                                                               jint channels) {
    if (_audioChannel) {
        _audioChannel->setAudioEncInfo(sample_rate_in_hz, channels);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_live_1push_LivePusher_getInputSamples(JNIEnv *env, jobject thiz) {
    if (_audioChannel) {
        return _audioChannel->getInputSamples();
    }
    return -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_live_1push_LivePusher_native_1pushAudio(JNIEnv *env, jobject thiz,
                                                         jbyteArray data) {
    if (!_audioChannel || !readyPushing) {
        LOGE("音频推送数据失败readyPushing%d", readyPushing);
        return;
    }
    jbyte *temp_data = env->GetByteArrayElements(data, NULL);
    _audioChannel->encodeData(temp_data);
    env->ReleaseByteArrayElements(data, temp_data, 0);
}