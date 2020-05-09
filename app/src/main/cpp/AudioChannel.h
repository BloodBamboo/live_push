//
// Created by 12 on 2020/4/15.
//

#ifndef LIVE_PUSH_AUDIOCHANNEL_H
#define LIVE_PUSH_AUDIOCHANNEL_H

#include "librtmp/rtmp.h"
#include "faac.h"
#include <sys/types.h>
#include "macro.h"
#include <cstring>

class AudioChannel {
    typedef void (*AudioCallback)(RTMPPacket *packet);

public:
    AudioChannel();

    ~AudioChannel();

    //设置音频编码器
    void setAudioEncInfo(int samplesInHZ, int channels);

//    设置编码器回调
    void setAudioCallback(AudioCallback audioCallback);

//获取一次最大能输入编码器的样本数量
    int getInputSamples();

//开始编码数据
    void encodeData(int8_t *data);

//获取音频头信息
    RTMPPacket *getAudioTag();

private:
    AudioCallback audioCallback;
    int mChannels;
    faacEncHandle audioCodec = 0;
    u_long inputSamples;
    u_long maxOutputBytes;
    u_char *buffer = 0;
};


#endif //LIVE_PUSH_AUDIOCHANNEL_H
