package com.example.live_push

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import cn.com.bamboo.easy_common.help.Permission4MultipleHelp
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {
    private lateinit var _live: LivePusher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //权限申请
        Permission4MultipleHelp.request(this, arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        ), success = {
            toast("可以直播了")
        })
        _live = LivePusher(this)
        //  设置摄像头预览的界面
        _live.setPreviewDisplay(surfaceView.holder)
    }

    fun switchCamera(view: View?) {
        _live.switchCamera()
    }

    fun startLive(view: View?) {

        _live.startLive("rtmp://192.168.1.106/myapp/123")
    }

    fun stopLive(view: View?) {
        _live.stopLive()
    }

    override fun onDestroy() {
        super.onDestroy()
        _live.release()
    }
}
