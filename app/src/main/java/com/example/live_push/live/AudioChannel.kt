package com.example.live_push.live

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.live_push.LivePusher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AudioChannel(livePusher: LivePusher) {
    private final val SAMPLE_RATEIN_HZ = 44100
    private var inputSamples = 0
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var audioRecord: AudioRecord
    private val mLivePusher: LivePusher = livePusher
    private val channels = 2
    private var isLiving = false

    init {
        //准备录音机 采集pcm 数据
        val channelConfig = if (channels == 2) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }

        mLivePusher.native_setAudioEncInfo(SAMPLE_RATEIN_HZ, channels)
        //16 位 2个字节
        inputSamples = mLivePusher.getInputSamples() * 2
        //最小需要的缓冲区, 多给一些空间防止不够用出现问题
        var minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATEIN_HZ,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2
        //1、麦克风 2、采样率 3、声道数 4、采样位 5、缓冲区大小
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATEIN_HZ,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            if (minBufferSize > inputSamples) minBufferSize else inputSamples
        )
    }

    fun startLive() {
        isLiving = true
        executor.submit(AudioTeask())
    }

    fun stopLive() {
        isLiving = false
    }

    fun release() {
        audioRecord.release()
    }


    inner class AudioTeask : Runnable {
        override fun run() {
            //启动录音机
            audioRecord.startRecording()
            val bytes = ByteArray(inputSamples)
            while (isLiving) {
                val len = audioRecord.read(bytes, 0, bytes.size)
                if (len > 0) { //送去编码
                    mLivePusher.native_pushAudio(bytes)
                }
            }
            //停止录音机
            audioRecord.stop()
        }
    }
}