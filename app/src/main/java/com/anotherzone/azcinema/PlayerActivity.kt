package com.anotherzone.azcinema

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = PlayerView(this)
        view.useController = true
        setContentView(view)
        val items = intent.getStringArrayListExtra("items") ?: arrayListOf()
        if (items.isEmpty()) { finish(); return }
        val isUrl = intent.getBooleanExtra("isUrl", false)
        val media = items.map { value ->
            val uri = if (isUrl) value else android.net.Uri.fromFile(File(value)).toString()
            MediaItem.fromUri(uri)
        }
        player = ExoPlayer.Builder(this).build().also { exo ->
            view.player = exo
            exo.setMediaItems(media, intent.getIntExtra("index", 0), 0L)
            exo.prepare()
            exo.playWhenReady = true
            exo.repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    override fun onStop() { super.onStop(); player?.release(); player = null }
}