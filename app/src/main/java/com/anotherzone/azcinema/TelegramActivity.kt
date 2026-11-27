package com.anotherzone.azcinema

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.net.URLDecoder
import java.net.URL
import java.io.File
import java.io.FileOutputStream

class TelegramActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF08070B.toInt()) }
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.setSupportMultipleWindows(false)
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
        }
        web.webChromeClient = WebChromeClient()
        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, _, _ ->
            Thread {
                try {
                    val connection = URL(url).openConnection()
                    connection.setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0")
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
                    connection.connect()
                    val downloadedName = safeName(contentDisposition)
                    val episode = Regex("(?i)^(.+?)[ ._-]+S(\\d{1,2})E(\\d{1,3})").find(downloadedName)
                    val dir = if (episode != null) {
                        val show = episode.groupValues[1].replace('_', ' ').replace('.', ' ').trim().ifBlank { "Telegram Shows" }
                        val season = episode.groupValues[2].toIntOrNull() ?: 1
                        File(filesDir, "AZLibrary/TV Shows/$show/Season $season")
                    } else File(filesDir, "AZLibrary/Movies")
                    dir.mkdirs()
                    val name = downloadedName.ifBlank { "Telegram_" + java.lang.System.currentTimeMillis() + "_video" }
                    val output = File(dir, name)
                    connection.getInputStream().use { input -> FileOutputStream(output).use { stream -> input.copyTo(stream) } }
                    runOnUiThread { Toast.makeText(this, "Saved to AZ CINEMA: " + output.relativeTo(File(filesDir, "AZLibrary")).invariantSeparatorsPath, Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Download failed: " + e.message, Toast.LENGTH_LONG).show() }
                }
            }.start()
        })
        val hint = TextView(this).apply {
            text = "TELEGRAM WEB  •  Scan the QR code with your phone"
            setTextColor(0xFFE5D5FF.toInt())
            textSize = 13f
            setPadding(18, 10, 18, 10)
            setBackgroundColor(0xFF171020.toInt())
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(hint, FrameLayout.LayoutParams(-1, -2))
        setContentView(root)
        if (savedInstanceState == null) web.loadUrl("https://web.telegram.org/k/")
        else web.restoreState(savedInstanceState)
    }

    private fun safeName(disposition: String?): String {
        val raw = disposition?.substringAfter("filename=", "telegram-file")?.trim('"', ' ', ';') ?: "telegram-file"
        val decoded = try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        return decoded.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "telegram-file" }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        web.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true }
        return super.onKeyDown(keyCode, event)
    }
    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
