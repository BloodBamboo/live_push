package com.example.live_push

import android.hardware.Camera
import android.view.SurfaceHolder
import com.example.live_push.live.AudioChannel
import com.example.live_push.live.VideoChannel

class LivePusher(
    activity: MainActivity,
    width: Int = 800,
    height: Int = 600,
    fps: Int = 20,
    cameraId: Int = Camera.CameraInfo.CAMERA_FACING_BACK//Camera.CameraInfo.CAMERA_FACING_BACK
) {
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private val _activity = activity
    private val _width = width
    private val _height = height
    private val _fps = fps
    private val _cameraId = cameraId
    private var _videoChannel: VideoChannel? = null
    private var _audioChannel: AudioChannel? = null

    init {
        native_init()
        _videoChannel = VideoChannel(this, _activity, _width, _height, _fps, 800000, _cameraId);
        _audioChannel = AudioChannel(this)
    }

    fun setPreviewDisplay(holder: SurfaceHolder) {
        _videoChannel?.setPreviewDisplay(holder)
    }

    fun switchCamera() {
        _videoChannel?.switchCamera()
    }

    fun startLive(path: String) {
        native_start(path)
        _videoChannel?.startLive()
        _audioChannel?.startLive()
    }

    fun stopLive() {
        _videoChannel?.stopLive()
        _audioChannel?.stopLive()
        native_stop()
    }

    fun release() {
        _videoChannel?.release()
        _audioChannel?.release()
        native_release()
    }

    external fun native_init()

    external fun native_start(path: String)

    external fun native_setVideoEncInfo(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int
    )

    external fun native_setAudioEncInfo(sampleRateInHz: Int, channels: Int)

    external fun native_pushVideo(data: ByteArray)

    external fun native_stop()

    external fun native_release()
    //一次最大能输入编码器的样本数量 也就是编码的数据的个数 (一个样本是16位 2字节)
    external fun getInputSamples(): Int

    external fun native_pushAudio(data: ByteArray)
}