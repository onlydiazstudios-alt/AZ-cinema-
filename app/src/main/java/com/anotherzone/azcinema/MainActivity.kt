package com.anotherzone.azcinema

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var transferServer: TransferServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transferServer = TransferServer(this)
        try { transferServer.start() } catch (_: Exception) { }
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.addJavascriptInterface(AZBridge(), "AZCinema")
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        setContentView(web)
        if (savedInstanceState == null) web.loadUrl("file:///android_asset/index.html")
        else web.restoreState(savedInstanceState)
        web.requestFocus()
    }

    inner class AZBridge {
        @JavascriptInterface fun getTransferUrl(): String = transferServer.transferUrl()
        @JavascriptInterface fun getVideosJson(category: String): String {
            val dir = libraryDir(category)
            val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() } ?: emptyList()
            val arr = JSONArray()
            files.forEach { f ->
                arr.put(JSONObject().put("name", f.name.substringAfter("_", f.name))
                    .put("path", f.absolutePath).put("sizeLabel", readableSize(f.length()))
                    .put("isMkv", f.extension.equals("mkv", true)))
            }
            return arr.toString()
        }
        @JavascriptInterface fun playUrl(url: String) { runOnUiThread { launchPlayer(arrayOf(url), 0, true) } }
        @JavascriptInterface fun playCategory(category: String, index: Int) {
            val files = libraryDir(category).listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() } ?: emptyList()
            if (files.isNotEmpty()) runOnUiThread { launchPlayer(files.map { it.absolutePath }.toTypedArray(), index.coerceIn(0, files.lastIndex), false) }
        }
    }

    private fun libraryDir(category: String): File {
        val safe = if (category == "TV Shows") "TV Shows" else "Movies"
        return File(filesDir, "AZLibrary/$safe").apply { mkdirs() }
    }

    private fun launchPlayer(items: Array<String>, index: Int, isUrl: Boolean) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra("items", ArrayList(items.toList()))
            putExtra("index", index)
            putExtra("isUrl", isUrl)
        })
    }

    private fun readableSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else String.format("%.0f MB", mb)
    }

    override fun onDestroy() {
        try { transferServer.stop() } catch (_: Exception) { }
        web.destroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        fun localIp(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) return addr.hostAddress!!
                }
            } catch (_: Exception) { }
            return "TV-IP-NOT-FOUND"
        }
    }
}
