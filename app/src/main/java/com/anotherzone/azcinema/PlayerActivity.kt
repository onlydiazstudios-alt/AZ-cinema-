package com.anotherzone.azcinema

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

class PlayerActivity : Activity() {
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var title: TextView
    private var items: ArrayList<String> = arrayListOf()
    private var index = 0
    private var isUrl = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        items = intent.getStringArrayListExtra("items") ?: arrayListOf()
        index = intent.getIntExtra("index", 0).coerceAtLeast(0)
        isUrl = intent.getBooleanExtra("isUrl", false)
        if (items.isEmpty()) { finish(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        videoLayout = VLCVideoLayout(this)
        root.addView(videoLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xFF171222.toInt())
        }
        title = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; maxLines = 1 }
        val previous = Button(this).apply { text = "PREVIOUS"; setOnClickListener { playAt(index - 1) } }
        val pause = Button(this).apply {
            text = "PAUSE"
            setOnClickListener {
                val p = player ?: return@setOnClickListener
                if (p.isPlaying) { p.pause(); text = "PLAY" } else { p.play(); text = "PAUSE" }
            }
        }
        val next = Button(this).apply { text = "NEXT"; setOnClickListener { playAt(index + 1) } }
        controls.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(previous); controls.addView(pause); controls.addView(next)
        root.addView(controls)
        setContentView(root)

        libVlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        player = MediaPlayer(libVlc).also { mp ->
            mp.attachViews(videoLayout, null, false, false)
            mp.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    if (event.type == MediaPlayer.Event.EndReached) {
                        runOnUiThread { if (index < items.lastIndex) playAt(index + 1) }
                    }
                }
            })
        }
        playAt(index)
        videoLayout.requestFocus()
    }

    private fun playAt(newIndex: Int) {
        if (newIndex !in items.indices) return
        index = newIndex
        title.text = items[index].substringAfterLast('/').substringAfterLast('\\')
        val uri = if (isUrl) Uri.parse(items[index]) else Uri.fromFile(File(items[index]))
        val media = Media(libVlc, uri)
        player?.media = media
        media.release()
        player?.play()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { playAt(index + 1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { playAt(index - 1); return true }
            KeyEvent.KEYCODE_BACK -> { finish(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        player?.let { it.stop(); it.detachViews(); it.release() }
        player = null
        libVlc?.release()
        libVlc = null
        super.onStop()
    }
}
