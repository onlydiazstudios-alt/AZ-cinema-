package com.anotherzone.azcinema

import android.content.Context
import android.os.Build
import android.util.Base64
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Local Telegram client hosted by the TV. The iPhone companion talks to this
 * manager through the token-protected LAN endpoints in TransferServer.
 */
class TelegramManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val downloadStates = ConcurrentHashMap<Int, String>()
    @Volatile private var client: Client? = null
    @Volatile private var state = "Starting Telegram…"
    @Volatile private var qrLink = ""
    @Volatile private var ready = false
    @Volatile private var lastError = ""
    private val earlyUpdates = ConcurrentLinkedQueue<TdApi.Object>()

    @Synchronized
    fun start() {
        if (client != null) return
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            state = "Telegram API credentials are not configured in this build"
            return
        }
        try {
            val created = Client.create(Client.ResultHandler { update ->
                if (client == null) earlyUpdates.add(update) else handleUpdate(update)
            }, null, null)
            client = created
            while (true) { val pending = earlyUpdates.poll() ?: break; handleUpdate(pending) }
            state = "Connecting to Telegram…"
        } catch (e: Exception) {
            state = "Telegram could not start"
            lastError = e.message ?: e.javaClass.simpleName
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update.javaClass.simpleName) {
            "UpdateAuthorizationState" -> {
                val auth = field(update, "authorizationState") ?: return
                when (auth.javaClass.simpleName) {
                    "AuthorizationStateWaitTdlibParameters" -> {
                        state = "Preparing secure Telegram database…"
                        sendAsync("SetTdlibParameters", mapOf(
                            "useTestDc" to false,
                            "databaseDirectory" to File(app.filesDir, "telegram/database").apply { mkdirs() }.absolutePath,
                            "filesDirectory" to File(app.filesDir, "telegram/files").apply { mkdirs() }.absolutePath,
                            "databaseEncryptionKey" to databaseKey(),
                            "useFileDatabase" to true,
                            "useChatInfoDatabase" to true,
                            "useMessageDatabase" to true,
                            "useSecretChats" to false,
                            "apiId" to BuildConfig.TELEGRAM_API_ID,
                            "apiHash" to BuildConfig.TELEGRAM_API_HASH,
                            "systemLanguageCode" to "en",
                            "deviceModel" to "Android TV",
                            "systemVersion" to Build.VERSION.RELEASE,
                            "applicationVersion" to "AZ CINEMA 1.9"
                        ))
                    }
                    "AuthorizationStateWaitPhoneNumber" -> {
                        state = "Waiting for Telegram QR login"
                        sendAsync("RequestQrCodeAuthentication")
                    }
                    "AuthorizationStateWaitOtherDeviceConfirmation" -> {
                        qrLink = field(auth, "link")?.toString().orEmpty()
                        state = "Scan the QR code using Telegram on your phone"
                    }
                    "AuthorizationStateWaitCode" -> state = "Telegram sent a login code"
                    "AuthorizationStateWaitPassword" -> state = "Telegram two-step verification required"
                    "AuthorizationStateWaitEmailAddress" -> state = "Telegram email verification required"
                    "AuthorizationStateWaitEmailCode" -> state = "Telegram email code required"
                    "AuthorizationStateReady" -> {
                        ready = true
                        qrLink = ""
                        state = "Connected to Telegram"
                        lastError = ""
                    }
                    "AuthorizationStateLoggingOut" -> { ready = false; state = "Logging out of Telegram…" }
                    "AuthorizationStateClosing" -> { ready = false; state = "Closing Telegram…" }
                    "AuthorizationStateClosed" -> { ready = false; state = "Telegram disconnected"; client = null }
                }
            }
            "UpdateFile" -> {
                // Download workers poll GetFile so the saved copy is only moved after
                // TDLib reports a complete local file.
            }
        }
    }

    private fun databaseKey(): ByteArray {
        val prefs = app.getSharedPreferences("az_telegram", Context.MODE_PRIVATE)
        var encoded = prefs.getString("database_key", null)
        if (encoded.isNullOrBlank()) {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs.edit().putString("database_key", encoded).apply()
        }
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun newFunction(name: String, values: Map<String, Any?> = emptyMap()): TdApi.Function<*> {
        val type = Class.forName("org.drinkless.tdlib.TdApi\$$name")
        val instance = type.getDeclaredConstructor().newInstance()
        values.forEach { (key, value) -> type.getField(key).set(instance, value) }
        @Suppress("UNCHECKED_CAST")
        return instance as TdApi.Function<*>
    }

    private fun sendAsync(name: String, values: Map<String, Any?> = emptyMap()) {
        val active = client ?: return
        try {
            active.send(newFunction(name, values), Client.ResultHandler { result ->
                if (result.javaClass.simpleName == "Error") {
                    lastError = "${field(result, "message") ?: "Telegram request failed"}"
                }
            })
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
        }
    }

    private fun request(name: String, values: Map<String, Any?> = emptyMap(), timeoutSeconds: Long = 20): TdApi.Object {
        val active = client ?: throw IllegalStateException("Telegram is not initialized")
        val result = AtomicReference<TdApi.Object?>()
        val latch = CountDownLatch(1)
        active.send(newFunction(name, values), Client.ResultHandler { value ->
            result.set(value)
            latch.countDown()
        })
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) throw IllegalStateException("Telegram request timed out")
        val value = result.get() ?: throw IllegalStateException("Telegram returned no response")
        if (value.javaClass.simpleName == "Error") {
            throw IllegalStateException("${field(value, "message") ?: "Telegram request failed"}")
        }
        return value
    }

    private fun field(value: Any?, name: String): Any? = try {
        value?.javaClass?.getField(name)?.get(value)
    } catch (_: Exception) { null }

    fun currentQrLink(): String = qrLink

    fun statusJson(): String = JSONObject()
        .put("configured", BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank())
        .put("connected", ready)
        .put("state", state)
        .put("qrLink", qrLink)
        .put("error", lastError)
        .put("downloads", downloadsJsonArray())
        .toString()

    fun submitPhone(phone: String): String {
        require(phone.matches(Regex("^\\+[0-9]{7,15}$"))) { "Enter the phone number in international format, for example +234…" }
        sendAsync("SetAuthenticationPhoneNumber", mapOf("phoneNumber" to phone))
        state = "Requesting Telegram login code…"
        return statusJson()
    }

    fun submitCode(code: String): String {
        require(code.matches(Regex("^[0-9]{4,8}$"))) { "Enter the Telegram login code" }
        sendAsync("CheckAuthenticationCode", mapOf("code" to code))
        state = "Checking Telegram login code…"
        return statusJson()
    }

    fun submitPassword(password: String): String {
        require(password.isNotBlank()) { "Enter your Telegram two-step verification password" }
        sendAsync("CheckAuthenticationPassword", mapOf("password" to password))
        state = "Checking Telegram password…"
        return statusJson()
    }

    fun chatsJson(): String {
        if (!ready) return JSONArray().toString()
        val chatList = Class.forName("org.drinkless.tdlib.TdApi\\$ChatListMain").getDeclaredConstructor().newInstance()
        val response = request("GetChats", mapOf("chatList" to chatList, "limit" to 100))
        val ids = field(response, "chatIds")
        val list = JSONArray()
        val values: List<Long> = when (ids) {
            is LongArray -> ids.toList()
            is Array<*> -> ids.mapNotNull { (it as? Number)?.toLong() }
            else -> emptyList()
        }
        values.forEach { id ->
            try {
                val chat = request("GetChat", mapOf("chatId" to id))
                list.put(JSONObject()
                    .put("id", id)
                    .put("title", field(chat, "title")?.toString().orEmpty())
                    .put("type", field(field(chat, "type"), "javaClass")?.toString() ?: field(chat, "type")?.javaClass?.simpleName.orEmpty()))
            } catch (_: Exception) { }
        }
        return list.toString()
    }

    fun messagesJson(chatId: Long, fromMessageId: Long = 0): String {
        require(ready) { "Connect Telegram first" }
        val response = request("GetChatHistory", mapOf(
            "chatId" to chatId,
            "fromMessageId" to fromMessageId.toInt(),
            "offset" to 0,
            "limit" to 100
        ))
        val messages = field(response, "messages") as? Array<*> ?: emptyArray<Any>()
        val list = JSONArray()
        messages.forEach { item ->
            if (item == null) return@forEach
            val content = field(item, "content") ?: return@forEach
            val extracted = extractMedia(content) ?: return@forEach
            val file = extracted.first
            val name = extracted.second.ifBlank { "telegram-video-${field(item, "id")}.mp4" }
            val fileId = (field(file, "id") as? Number)?.toInt() ?: return@forEach
            val size = (field(file, "size") as? Number)?.toLong() ?: 0L
            list.put(JSONObject()
                .put("messageId", (field(item, "id") as? Number)?.toLong() ?: 0L)
                .put("fileId", fileId)
                .put("name", name)
                .put("size", size)
                .put("date", (field(item, "date") as? Number)?.toLong() ?: 0L))
        }
        return list.toString()
    }

    private fun extractMedia(content: Any): Pair<Any, String>? {
        return when (content.javaClass.simpleName) {
            "MessageVideo" -> {
                val video = field(content, "video") ?: return null
                val file = field(video, "video") ?: return null
                file to (field(video, "fileName")?.toString() ?: "video.mp4")
            }
            "MessageDocument" -> {
                val document = field(content, "document") ?: return null
                val file = field(document, "document") ?: return null
                val name = field(document, "fileName")?.toString() ?: "video"
                val mime = field(document, "mimeType")?.toString().orEmpty()
                if (mime.startsWith("video/") || name.substringAfterLast('.', "").lowercase() in setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "mpeg", "mpg")) file to name else null
            }
            "MessageAnimation" -> {
                val animation = field(content, "animation") ?: return null
                val file = field(animation, "animation") ?: return null
                file to (field(animation, "fileName")?.toString() ?: "animation.mp4")
            }
            else -> null
        }
    }

    fun enqueueDownload(fileId: Int, originalName: String): String {
        require(ready) { "Connect Telegram first" }
        require(fileId > 0) { "Invalid Telegram file" }
        if (downloadStates[fileId] == "Downloading" || downloadStates[fileId] == "Saved") return "Already queued"
        downloadStates[fileId] = "Queued"
        executor.execute {
            try {
                downloadStates[fileId] = "Downloading"
                request("DownloadFile", mapOf(
                    "fileId" to fileId,
                    "priority" to 32,
                    "offset" to 0L,
                    "limit" to 0L,
                    "synchronous" to false
                ))
                var localPath = ""
                val deadline = System.currentTimeMillis() + 30L * 60L * 1000L
                while (System.currentTimeMillis() < deadline) {
                    val file = request("GetFile", mapOf("fileId" to fileId), 30)
                    val local = field(file, "local")
                    val complete = field(local, "isDownloadingCompleted") as? Boolean ?: false
                    localPath = field(local, "path")?.toString().orEmpty()
                    if (complete && localPath.isNotBlank() && File(localPath).isFile) break
                    Thread.sleep(1200)
                }
                val source = File(localPath)
                if (!source.isFile || source.length() <= 0) throw IllegalStateException("Telegram download did not finish")
                val clean = safeName(originalName)
                val match = Regex("(?i)^(.+?)[ ._-]+S(\\d{1,2})E(\\d{1,3})").find(clean)
                val destinationDir = if (match != null) {
                    val show = match.groupValues[1].replace(Regex("[._]+"), " ").trim().take(70).ifBlank { "Telegram Shows" }
                    File(app.filesDir, "AZLibrary/TV Shows/$show/Season ${match.groupValues[2].toInt()}").apply { mkdirs() }
                } else File(app.filesDir, "AZLibrary/Movies").apply { mkdirs() }
                val destination = File(destinationDir, "${System.currentTimeMillis()}_$clean")
                FileInputStream(source).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
                downloadStates[fileId] = "Saved"
            } catch (e: Exception) {
                downloadStates[fileId] = "Failed: ${e.message ?: "unknown error"}"
            }
        }
        return "Queued"
    }

    private fun safeName(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "telegram-video.mp4" }

    private fun downloadsJsonArray(): JSONArray {
        val array = JSONArray()
        downloadStates.entries.sortedBy { it.key }.forEach { (id, status) ->
            array.put(JSONObject().put("fileId", id).put("status", status))
        }
        return array
    }

    companion object {
        @Volatile private var instance: TelegramManager? = null
        fun get(context: Context): TelegramManager = instance ?: synchronized(this) {
            instance ?: TelegramManager(context).also { instance = it }
        }
    }
}
