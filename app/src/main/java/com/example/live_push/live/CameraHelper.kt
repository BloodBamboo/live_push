package com.example.live_push.live

import android.app.Activity
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.Camera.*
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder

class CameraHelper(activity: Activity, width: Int, height: Int, cameraId: Int) :
    SurfaceHolder.Callback, PreviewCallback {
    private final val TAG = "CameraHelper"
    private var _listener: ((width: Int, height: Int) -> Unit)? = null
    private var _previewCallback: Camera.PreviewCallback? = null
    private var _camera: Camera? = null
    private var _cameraId = cameraId
    private var _width = width
    private var _height = height
    private val _activity = activity
    private var _buffer: ByteArray? = null
    private var _surfaceHolder: SurfaceHolder? = null
    private var _rotation = 0
    //旋转后的预览画面数据
    private var _bytes: ByteArray? = null


    fun setPreviewDisplay(holder: SurfaceHolder) {
        _surfaceHolder = holder
        _surfaceHolder?.addCallback(this)
    }

    fun setPreviewCallback(callabck: Camera.PreviewCallback) {
        _previewCallback = callabck
    }

    fun setOnChangedSizeListener(listener: (width: Int, height: Int) -> Unit) {
        _listener = listener
    }

    override fun surfaceChanged(surfaceholder: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
        //释放摄像头
        stopPreview()
        //开启摄像头
        startPreview()
    }

    override fun surfaceDestroyed(surfaceholder: SurfaceHolder?) {
    }

    override fun surfaceCreated(surfaceholder: SurfaceHolder?) {
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        when (_rotation) {
            Surface.ROTATION_0 -> rotation90(data)
            Surface.ROTATION_90 -> {
            }
            Surface.ROTATION_270 -> {
            }
        }
        // data数据依然是倒的
        _previewCallback?.onPreviewFrame(_bytes, camera)
        camera.addCallbackBuffer(_buffer)
    }

    fun switchCamera() {
        if (_cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            _cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            _cameraId = Camera.CameraInfo.CAMERA_FACING_BACK
        }
        stopPreview()
        startPreview()
    }

    private fun startPreview() {
        try {
            Log.e(TAG, "开启摄像头")
            //获得camera对象
            _camera = open(_cameraId)
            //配置camera的属性
            val parameters = _camera!!.parameters
            //设置预览数据格式为nv21
            parameters.previewFormat = ImageFormat.NV21
            //这是摄像头宽、高
            setPreviewSize(parameters)
            // 设置摄像头 图像传感器的角度、方向
            setPreviewOrientation()
            _camera!!.parameters = parameters
            //创建缓冲区大小比宽*高多一些
            _buffer = ByteArray(_width * _height * 3 / 2)
            _bytes = ByteArray(_buffer!!.size)
            //数据缓存区
            _camera!!.addCallbackBuffer(_buffer)
            _camera!!.setPreviewCallbackWithBuffer(this)
            //设置预览画面
            _camera!!.setPreviewDisplay(_surfaceHolder)
            _camera!!.startPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopPreview() {
        _camera?.apply {
            //预览数据回调接口
            this.setPreviewCallback(null)
//停止预览
            this.stopPreview()
            //释放摄像头
            this.release()
        }
        _camera = null
    }

    /**
     * 先获取摄像头支持的第一个宽高然后算出与设置的差值，然后通过再比较摄像头剩余支持的宽高
     * 找出一个差值最小的，作为最终使用的宽高
     */
    private fun setPreviewSize(parameters: Camera.Parameters) { //获取摄像头支持的宽、高
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        var size = supportedPreviewSizes[0]
        Log.d(TAG, "支持 " + size.width + "x" + size.height)
        //选择一个与设置的差距最小的支持分辨率
        // 10x10 20x20 30x30
        // 12x12
        var m: Int = Math.abs(size.height * size.width - _width * _height)
        supportedPreviewSizes.removeAt(0)
        val iterator: Iterator<Camera.Size> = supportedPreviewSizes.iterator()
        //遍历
        while (iterator.hasNext()) {
            val next = iterator.next()
            Log.d(TAG, "支持 " + next.width + "x" + next.height)
            val n: Int = Math.abs(next.height * next.width - _width * _height)
            if (n < m) {
                m = n
                size = next
            }
        }
        _width = size.width
        _height = size.height
        parameters.setPreviewSize(_width, _height)
        Log.d(
            TAG,
            "设置预览分辨率 width:" + size.width + " height:" + size.height
        )
    }

    /**
     * 通过屏幕方向设置画面旋转角度
     */
    private fun setPreviewOrientation() {
        val info = CameraInfo()
        getCameraInfo(_cameraId, info)
        _rotation = _activity.getWindowManager().getDefaultDisplay().getRotation()
        var degrees = 0
        when (_rotation) {
            Surface.ROTATION_0 -> {
                degrees = 0
                _listener?.let { it(_height, _width) }
            }
            Surface.ROTATION_90 -> {
                degrees = 90
                _listener?.let { it(_width, _height) }
            }
            Surface.ROTATION_270 -> {
                degrees = 270
                _listener?.let { it(_width, _height) }
            }
        }
        var result: Int
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        //设置角度
        _camera?.setDisplayOrientation(result)
    }

    /**
     * 旋转数据，不然推送出去收到的数据显示方向会有问题
     */
    private fun rotation90(data: ByteArray) {
        var index = 0
        val ySize: Int = _width * _height
        //u和v
        val uvHeight: Int = _height / 2
        //后置摄像头顺时针旋转90度
        if (_cameraId == CameraInfo.CAMERA_FACING_BACK) { //将y的数据旋转之后 放入新的byte数组
            for (i in 0 until _width) {
                for (j in _height - 1 downTo 0) {
                    _bytes!![index++] = data[_width * j + i]
                }
            }
            //每次处理两个数据
            var i = 0
            while (i < _width) {
                for (j in uvHeight - 1 downTo 0) { // v
                    _bytes!![index++] = data[ySize + _width * j + i]
                    // u
                    _bytes!![index++] = data[ySize + _width * j + i + 1]
                }
                i += 2
            }
        } else { //逆时针旋转90度
            for (i in 0 until _width) {
                var nPos: Int = _width - 1
                for (j in 0 until _height) {
                    _bytes!![index++] = data[nPos - i]
                    nPos += _width
                }
            }
            //u v
            var i = 0
            while (i < _width) {
                var nPos: Int = ySize + _width - 1
                for (j in 0 until uvHeight) {
                    _bytes!![index++] = data[nPos - i - 1]
                    _bytes!![index++] = data[nPos - i]
                    nPos += _width
                }
                i += 2
            }
        }
    }

    fun release() {
        _surfaceHolder?.removeCallback(this)
        stopPreview()
    }
}