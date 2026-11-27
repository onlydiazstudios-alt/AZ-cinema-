package com.anotherzone.azcinema

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setTitle("Telegram download")
                request.setDescription("Downloading from Telegram")
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setAllowedOverMetered(true)
                request.setAllowedOverRoaming(false)
                request.addRequestHeader("User-Agent", userAgent ?: "Mozilla/5.0")
                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MOVIES, "AZLibrary/Movies/Telegram_" + java.lang.System.currentTimeMillis() + "_" + safeName(contentDisposition))
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this, "Download started. Check Android notifications.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not start download: " + e.message, Toast.LENGTH_LONG).show()
            }
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
