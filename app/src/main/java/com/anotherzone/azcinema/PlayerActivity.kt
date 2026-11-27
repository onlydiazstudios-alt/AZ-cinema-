package com.anotherzone.azcinema

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var overlay: LinearLayout
    private lateinit var title: TextView
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var duration: TextView
    private var items: ArrayList<String> = arrayListOf()
    private var index = 0
    private var isUrl = false
    private var seeking = false
    private val handler = Handler(Looper.getMainLooper())
    private val progressTask = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && !seeking) {
                val length = p.length.coerceAtLeast(0L)
                val time = p.time.coerceAtLeast(0L)
                seekBar.max = if (length > Int.MAX_VALUE) Int.MAX_VALUE else length.toInt().coerceAtLeast(1)
                seekBar.progress = if (length > Int.MAX_VALUE) ((time.toDouble() / length) * Int.MAX_VALUE).toInt() else time.toInt()
                elapsed.text = formatTime(time)
                duration.text = formatTime(length)
                playButton.text = if (p.isPlaying) "Ⅱ" else "▶"
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()
        items = intent.getStringArrayListExtra("items") ?: arrayListOf()
        index = intent.getIntExtra("index", 0).coerceAtLeast(0)
        isUrl = intent.getBooleanExtra("isUrl", false)
        if (items.isEmpty()) { finish(); return }

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        videoLayout = VLCVideoLayout(this)
        root.addView(videoLayout, FrameLayout.LayoutParams(-1, -1))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(34, 24, 34, 28)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00101010, 0xCC080808.toInt(), 0xF2080808.toInt())
            )
        }
        title = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 21f; maxLines = 1
            setShadowLayer(5f, 1f, 1f, 0xFF000000.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "AZ CINEMA"; setTextColor(0xFFCEB5F2.toInt()); textSize = 12f
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        top.addView(title); top.addView(subtitle)
        overlay.addView(top)

        val spacer = View(this)
        overlay.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val center = FrameLayout(this)
        playButton = Button(this).apply {
            text = "▶"; textSize = 30f; isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xAA171222.toInt())
                setStroke(2, 0xFFCEB5F2.toInt())
            }
            setOnClickListener { togglePlayback() }
        }
        center.addView(playButton, FrameLayout.LayoutParams(76, 76, Gravity.CENTER))
        overlay.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))

        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        elapsed = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; text = "00:00" }
        duration = TextView(this).apply { setTextColor(0xFFD7D0DF.toInt()); textSize = 13f; text = "00:00" }
        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { seeking = true; showControls() }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    val p = player
                    if (p != null && p.length > 0) p.time = (seekBar!!.progress.toLong() * p.length) / seekBar.max
                    seeking = false
                }
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) elapsed.text = formatTime((player?.length ?: 0L) * progress / (bar?.max ?: 1).coerceAtLeast(1))
                }
            })
        }
        timeRow.addView(elapsed)
        timeRow.addView(seekBar, LinearLayout.LayoutParams(0, 42, 1f).apply { leftMargin = 12; rightMargin = 12 })
        timeRow.addView(duration)
        overlay.addView(timeRow)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        val prev = Button(this).apply { text = "⏮ PREVIOUS"; setOnClickListener { playAt(index - 1); showControls() } }
        val next = Button(this).apply { text = "NEXT ⏭"; setOnClickListener { playAt(index + 1); showControls() } }
        actions.addView(prev); actions.addView(next)
        overlay.addView(actions)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        videoLayout.setOnClickListener { if (overlay.visibility == View.VISIBLE) hideControls() else showControls() }
        libVlc = LibVLC(this, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        player = MediaPlayer(libVlc).also { mp ->
            mp.attachViews(videoLayout, null, false, false)
            mp.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    when (event.type) {
                        MediaPlayer.Event.EndReached -> runOnUiThread { if (index < items.lastIndex) playAt(index + 1) }
                        MediaPlayer.Event.Playing, MediaPlayer.Event.Paused -> runOnUiThread { playButton.text = if (mp.isPlaying) "Ⅱ" else "▶" }
                        MediaPlayer.Event.EncounteredError -> runOnUiThread {
                            playButton.text = "▶"
                            Toast.makeText(this@PlayerActivity, "AZ CINEMA could not decode this video. Try another file or check the file is complete.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
        playAt(index)
        handler.post(progressTask)
        playButton.requestFocus()
    }

    private fun enterImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val total = ms / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
            else String.format("%02d:%02d", minutes, seconds)
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        playButton.text = if (p.isPlaying) "Ⅱ" else "▶"
        showControls()
    }

    private fun showControls() {
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideTask)
        handler.postDelayed(hideTask, 5000)
    }
    private fun hideControls() {
        if (player?.isPlaying == true) overlay.visibility = View.GONE
    }
    private val hideTask = Runnable { hideControls() }

    private fun playAt(newIndex: Int) {
        if (newIndex !in items.indices) return
        index = newIndex
        val raw = items[index].substringAfterLast('/').substringAfterLast('\\')
        title.text = raw.substringAfter("_", raw).replace('_', ' ').replace('.', ' ')
        val uri = if (isUrl) Uri.parse(items[index]) else Uri.fromFile(File(items[index]))
        player?.stop()
        val media = Media(libVlc, uri).apply {
            addOption(":avcodec-hw=any")
            addOption(if (isUrl) ":network-caching=5000" else ":file-caching=2000")
            addOption(":no-video-title-show")
        }
        player?.media = media
        media.release()
        player?.play()
        seekBar.progress = 0
        elapsed.text = "00:00"
        duration.text = "00:00"
        playButton.text = "Ⅱ"
        showControls()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (overlay.visibility != View.VISIBLE) showControls() else togglePlayback()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { player?.let { it.time = (it.time - 10000).coerceAtLeast(0) }; showControls(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.let { it.time = (it.time + 10000).coerceAtMost(it.length) }; showControls(); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { showControls(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { playAt(index + 1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { playAt(index - 1); return true }
            KeyEvent.KEYCODE_BACK -> { finish(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        handler.removeCallbacks(progressTask)
        handler.removeCallbacks(hideTask)
        player?.let { it.stop(); it.detachViews(); it.release() }
        player = null
        libVlc?.release()
        libVlc = null
        super.onStop()
    }
}
