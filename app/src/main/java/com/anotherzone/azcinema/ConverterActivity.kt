package com.anotherzone.azcinema

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

class ConverterActivity : Activity() {
    private var transformer: Transformer? = null
    private lateinit var status: TextView
    private lateinit var convertButton: Button
    private lateinit var input: File
    private lateinit var output: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        input = File(path).canonicalFile
        val libraryRoot = File(filesDir, "AZLibrary").canonicalFile
        if (!input.path.startsWith(libraryRoot.path + File.separator) || !input.isFile ||
            !input.extension.equals("mkv", true)) {
            finish()
            return
        }
        output = File(input.parentFile, input.nameWithoutExtension + ".mp4")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xFF090711.toInt())
        }
        val heading = TextView(this).apply {
            text = "AZ CINEMA · MKV CONVERTER"
            textSize = 23f
            setTextColor(0xFFD6B5FF.toInt())
        }
        status = TextView(this).apply {
            text = "Convert " + input.name + " into an MP4 copy for wider playback compatibility. The original MKV will be kept."
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 24)
        }
        convertButton = Button(this).apply {
            text = "CONVERT TO MP4"
            setOnClickListener { startConversion() }
        }
        val close = Button(this).apply {
            text = "CLOSE"
            setOnClickListener { finish() }
        }
        layout.addView(heading)
        layout.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(convertButton)
        layout.addView(close)
        setContentView(layout)
    }

    private fun startConversion() {
        if (output.exists()) {
            status.text = "An MP4 with this name already exists. Rename or delete it from your library before converting again."
            return
        }
        convertButton.isEnabled = false
        status.text = "Converting… Keep AZ CINEMA open. Large videos may take a while."
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                runOnUiThread {
                    status.text = "Conversion complete. Saved as " + output.name + ". Your original MKV is still available."
                    convertButton.text = "CONVERTED"
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                output.delete()
                runOnUiThread {
                    status.text = "Could not convert this MKV: " + (exportException.message ?: "unsupported video/audio codec") + ". The original file has been kept."
                    convertButton.isEnabled = true
                }
            }
        }
        try {
            transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
            val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(input))).build()
            transformer?.start(item, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            status.text = "Could not start conversion: " + e.message
            convertButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        transformer?.cancel()
        transformer = null
        super.onDestroy()
    }
}
