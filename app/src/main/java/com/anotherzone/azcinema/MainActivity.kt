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
        @JavascriptInterface fun openTelegram() { runOnUiThread { startActivity(Intent(this@MainActivity, TelegramActivity::class.java)) } }
        @JavascriptInterface fun updateApp() { runOnUiThread { AppUpdater(this@MainActivity).checkAndInstall() } }
        @JavascriptInterface fun getVideosJson(category: String): String {
            val files = allVideos(libraryDir(category))
            val arr = JSONArray()
            files.forEach { f ->
                val relative = f.relativeTo(libraryDir(category)).invariantSeparatorsPath
                val parts = relative.split("/")
                val show = if (category == "TV Shows" && parts.size >= 3) parts[0] else ""
                val season = if (category == "TV Shows" && parts.size >= 3) parts[1] else ""
                arr.put(JSONObject()
                    .put("name", f.name.substringAfter("_", f.name))
                    .put("path", f.absolutePath)
                    .put("sizeLabel", readableSize(f.length()))
                    .put("isMkv", f.extension.equals("mkv", true))
                    .put("show", show)
                    .put("season", season))
            }
            return arr.toString()
        }
        @JavascriptInterface fun playUrl(url: String) { runOnUiThread { launchPlayer(arrayOf(url), 0, true) } }
        @JavascriptInterface fun playCategory(category: String, index: Int) {
            val files = allVideos(libraryDir(category))
            if (files.isNotEmpty()) runOnUiThread {
                launchPlayer(files.map { it.absolutePath }.toTypedArray(), index.coerceIn(0, files.lastIndex), false)
            }
        }
        @JavascriptInterface fun playFolder(show: String, season: String, index: Int) {
            val base = libraryDir("TV Shows")
            val safeShow = File(base, show).canonicalFile
            val safeBase = base.canonicalFile
            if (safeShow.canonicalPath != safeBase.canonicalPath && !safeShow.canonicalPath.startsWith(safeBase.canonicalPath + File.separator)) return
            val folder = File(safeShow, season)
            val files = folder.listFiles()?.filter { it.isFile && isVideo(it) }?.sortedBy { it.name.substringAfter("_").lowercase() } ?: emptyList()
            if (files.isNotEmpty()) runOnUiThread {
                launchPlayer(files.map { it.absolutePath }.toTypedArray(), index.coerceIn(0, files.lastIndex), false)
            }
        }
    }

    private fun libraryDir(category: String): File {
        val safe = if (category == "TV Shows") "TV Shows" else "Movies"
        return File(filesDir, "AZLibrary/$safe").apply { mkdirs() }
    }

    private fun allVideos(dir: File): List<File> =
        (dir.listFiles()?.toList() ?: emptyList()).flatMap { f ->
            if (f.isDirectory) allVideos(f) else if (isVideo(f)) listOf(f) else emptyList()
        }.sortedBy { it.name.substringAfter("_").lowercase() }

    private fun isVideo(file: File): Boolean =
        file.extension.lowercase() in setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "mpg", "mpeg")

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

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("azcinema_updates", MODE_PRIVATE)
        if (prefs.getBoolean("awaiting_install_permission", false)) {
            prefs.edit().putBoolean("awaiting_install_permission", false).apply()
            if (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
                android.widget.Toast.makeText(this, "Install permission enabled. AZ CINEMA can now check for and install updates. Choose CHECK FOR UPDATES.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "Install permission is still off. Enable it in Android settings, then return to AZ CINEMA.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
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
