package com.anotherzone.azcinema

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AppUpdater(private val activity: Activity) {
    fun checkAndInstall() {
        Toast.makeText(activity, "Checking for AZ CINEMA updates…", Toast.LENGTH_SHORT).show()
        Thread {
            var apk: File? = null
            try {
                val api = URL("https://api.github.com/repos/onlydiazstudios-alt/AZ-cinema-/releases/tags/az-cinema-latest")
                val conn = (api.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 20000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AZ-CINEMA-Android")
                }
                val release = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val assets = JSONObject(release).getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val item = assets.getJSONObject(i)
                    if (item.optString("name") == "app-debug.apk") downloadUrl = item.optString("browser_download_url")
                }
                if (downloadUrl.isNullOrBlank()) throw Exception("No APK found in the latest release")
                val download = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000; readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AZ-CINEMA-Android")
                }
                apk = File(activity.cacheDir, "az-cinema-update.apk")
                download.inputStream.use { input -> apk!!.outputStream().use { output -> input.copyTo(output) } }
                download.disconnect()
                val archive = activity.packageManager.getPackageArchiveInfo(apk!!.absolutePath, 0)
                    ?: throw Exception("Downloaded APK could not be read")
                if (archive.packageName != activity.packageName) throw Exception("Update package does not match AZ CINEMA")
                val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
                val currentCode = if (Build.VERSION.SDK_INT >= 28) activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode else @Suppress("DEPRECATION") activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
                if (newCode <= currentCode) {
                    apk!!.delete()
                    activity.runOnUiThread { Toast.makeText(activity, "AZ CINEMA is already up to date.", Toast.LENGTH_LONG).show() }
                    return@Thread
                }
                activity.runOnUiThread { install(apk!!) }
            } catch (e: Exception) {
                apk?.delete()
                activity.runOnUiThread { Toast.makeText(activity, "Update check failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "Allow AZ CINEMA to install updates, then press UPDATE again.", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            apk.inputStream().use { input ->
                session.openWrite("az-cinema-update", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(activity, UpdateStatusReceiver::class.java).setAction("com.anotherzone.azcinema.UPDATE_STATUS")
            val pending = PendingIntent.getBroadcast(activity, sessionId, callback,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(pending.intentSender)
            session.close()
            Toast.makeText(activity, "Update downloaded. Android will ask you to confirm installation.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not start installation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
