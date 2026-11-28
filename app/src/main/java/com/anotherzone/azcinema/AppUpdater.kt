package com.anotherzone.azcinema

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdater(private val activity: Activity) {
    fun checkAndInstall() {
        Toast.makeText(activity, "Checking the latest AZ CINEMA release…", Toast.LENGTH_SHORT).show()
        Thread {
            var apk: File? = null
            try {
                val releaseConnection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AZ-CINEMA-Android")
                }
                val releaseText = try {
                    val code = releaseConnection.responseCode
                    val stream = if (code in 200..299) releaseConnection.inputStream else releaseConnection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw Exception("GitHub returned HTTP $code: ${body.take(160)}")
                    body
                } finally {
                    releaseConnection.disconnect()
                }

                val release = JSONObject(releaseText)
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    throw Exception("The GitHub release is not marked as a stable release")
                }
                val releaseTag = release.optString("tag_name")
                if (releaseTag != EXPECTED_TAG) throw Exception("Unexpected release tag: $releaseTag")

                val assets = release.optJSONArray("assets") ?: throw Exception("GitHub release has no APK assets")
                var downloadUrl: String? = null
                var expectedDigest: String? = null
                for (i in 0 until assets.length()) {
                    val item = assets.getJSONObject(i)
                    if (item.optString("name") == APK_NAME) {
                        downloadUrl = item.optString("browser_download_url")
                        expectedDigest = item.optString("digest")
                            .takeIf { it.startsWith("sha256:") }
                            ?.substringAfter("sha256:")
                    }
                }
                if (downloadUrl.isNullOrBlank()) throw Exception("The latest release is missing $APK_NAME")

                @Suppress("DEPRECATION")
                val currentInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val currentCode = if (Build.VERSION.SDK_INT >= 28) currentInfo.longVersionCode else currentInfo.versionCode.toLong()

                apk = File(activity.cacheDir, "az-cinema-update.apk")
                apk.delete()
                val download = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 120000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AZ-CINEMA-Android")
                }
                try {
                    val code = download.responseCode
                    if (code !in 200..299) throw Exception("APK download failed (HTTP $code)")
                    download.inputStream.use { input ->
                        apk!!.outputStream().buffered().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                } finally {
                    download.disconnect()
                }

                if (apk!!.length() < 20L * 1024L * 1024L) throw Exception("APK download is incomplete (${apk!!.length()} bytes)")

                if (expectedDigest != null) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    apk!!.inputStream().buffered().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expectedDigest, ignoreCase = true)) {
                        throw Exception("APK integrity check failed. Nothing was installed.")
                    }
                }

                @Suppress("DEPRECATION")
                val archive = activity.packageManager.getPackageArchiveInfo(apk!!.absolutePath, 0)
                    ?: throw Exception("Downloaded APK is invalid or damaged")
                if (archive.packageName != activity.packageName) throw Exception("Downloaded APK is not AZ CINEMA")

                val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
                if (newCode <= currentCode) {
                    apk!!.delete()
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "No newer build is available. Installed build: $currentCode; GitHub build: $newCode.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@Thread
                }

                activity.runOnUiThread {
                    Toast.makeText(activity, "AZ CINEMA update found (build $newCode). Preparing installation…", Toast.LENGTH_LONG).show()
                    install(apk!!)
                }
            } catch (e: Exception) {
                apk?.delete()
                activity.runOnUiThread {
                    Toast.makeText(activity, "Update failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            activity.getSharedPreferences("azcinema_updates", Activity.MODE_PRIVATE)
                .edit().putBoolean("awaiting_install_permission", true).apply()
            Toast.makeText(activity, "Allow AZ CINEMA to install apps, return here, then press CHECK FOR UPDATES again.", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }

        var sessionId = -1
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(activity.packageName)
                setSize(apk.length())
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().buffered().use { input ->
                    session.openWrite("az-cinema-update.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 128 * 1024)
                        session.fsync(output)
                    }
                }
                val callback = Intent(activity, UpdateStatusReceiver::class.java)
                    .setAction("com.anotherzone.azcinema.UPDATE_STATUS")
                val pending = PendingIntent.getBroadcast(
                    activity,
                    sessionId,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_MUTABLE else 0
                )
                session.commit(pending.intentSender)
            }
            apk.delete()
            Toast.makeText(activity, "Update downloaded. Confirm the Android installation prompt on your TV.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            if (sessionId >= 0) try { installerAbandon(sessionId) } catch (_: Exception) { }
            Toast.makeText(activity, "Could not start installation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installerAbandon(sessionId: Int) {
        activity.packageManager.packageInstaller.abandonSession(sessionId)
    }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/onlydiazstudios-alt/AZ-cinema-/releases/tags/az-cinema-latest"
        private const val EXPECTED_TAG = "az-cinema-latest"
        private const val APK_NAME = "app-debug.apk"
    }
}
