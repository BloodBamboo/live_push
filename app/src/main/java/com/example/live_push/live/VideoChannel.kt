package com.example.live_push.live

import android.app.Activity
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.view.SurfaceHolder
import com.example.live_push.LivePusher

class VideoChannel(
    livePusher: LivePusher,
    activity: Activity,
    width: Int,
    height: Int,
    fps: Int,
    bitrate: Int,
    cameraId: Int
) : PreviewCallback {

    private val _livePusher: LivePusher = livePusher
    private var _cameraHelper: CameraHelper = CameraHelper(activity, width, height, cameraId)
    private val _bitrate = bitrate
    private val _fps = fps
    private var _isLiving = false

    init {
        //1、让camerahelper的
        _cameraHelper.setPreviewCallback(this)
        //2、回调 真实的摄像头数据宽、高
        _cameraHelper.setOnChangedSizeListener(fun(width: Int, height: Int) {
            //初始化编码器
            _livePusher.native_setVideoEncInfo(width, height, _fps, _bitrate)
        })
    }

    fun setPreviewDisplay(holder: SurfaceHolder) {
        _cameraHelper.setPreviewDisplay(holder)

    }

    override fun onPreviewFrame(data: ByteArray, p1: Camera) {
        if (_isLiving) {
            _livePusher.native_pushVideo(data)
        }
    }

    fun switchCamera() {
        _cameraHelper.switchCamera()
    }

    fun startLive() {
        _isLiving = true
    }

    fun stopLive() {
        _isLiving = false
    }

    fun release() {
        _cameraHelper.release()
    }
}