package com.anotherzone.azcinema

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class TransferServer(private val context: Context) : NanoHTTPD("0.0.0.0", 8765) {
    private val token = UUID.randomUUID().toString()
    private fun root(): File = File(context.filesDir, "AZLibrary")
    private fun folder(category: String): File = File(root(), if (category == "TV Shows") "TV Shows" else "Movies").apply { mkdirs() }
    fun transferUrl(): String = "http://${MainActivity.localIp()}:8765/?token=$token"

    override fun serve(session: IHTTPSession): Response {
        val supplied = session.parameters["token"]?.firstOrNull()
        if (supplied != token) return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Pairing token missing or incorrect")
        return when {
            session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", uploadPage())
            session.method == Method.POST && session.uri == "/upload" -> receive(session)
            session.method == Method.GET && session.uri == "/health" -> newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun receive(session: IHTTPSession): Response {
        return try {
            val tempFiles = HashMap<String, String>()
            session.parseBody(tempFiles)
            val temp = tempFiles["file"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file received")
            val params = session.parameters
            val category = params["category"]?.firstOrNull() ?: "Movies"
            val rawName = params["name"]?.firstOrNull() ?: "video"
            val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "video" }

            val tvMatch = Regex("(?i)^(.+?)[ ._-]+S(\\d{1,2})E(\\d{1,3})").find(cleanName)
            val destinationFolder = if (category == "TV Shows" && tvMatch != null) {
                val showName = tvMatch.groupValues[1].replace(Regex("[._]+"), " ").trim().take(70).ifBlank { "TV Show" }
                val seasonName = "Season " + tvMatch.groupValues[2].padStart(2, '0')
                File(File(folder("TV Shows"), showName), seasonName).apply { mkdirs() }
            } else folder(category)
            val destination = File(destinationFolder, "${System.currentTimeMillis()}_$cleanName")
            FileInputStream(temp).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
            newFixedLengthResponse(Response.Status.OK, "application/json",
                JSONObject().put("ok", true).put("name", cleanName).put("folder", destinationFolder.name).toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${e.message}")
        }
    }

    private fun uploadPage(): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Send to AZ CINEMA</title>
<style>body{background:#100c19;color:#fff;font:18px Arial;padding:24px;max-width:650px;margin:auto}h1{color:#c084fc}input,select,button{font-size:18px;padding:14px;margin:10px 0;width:100%;box-sizing:border-box;border-radius:9px}button{background:#9b5de5;color:white;border:0;font-weight:bold}small{color:#cbbddc}</style></head><body>
<h1>AZ CINEMA · Send to TV</h1><p>Select multiple video files. Keep this page open until all transfers finish.</p>
<label>Save in</label><select id="category"><option>Movies</option><option>TV Shows</option></select>
<input id="files" type="file" multiple><small>For TV episodes named like Show_S01E01_Title.mkv, AZ CINEMA automatically creates a show folder and Season 01 folder. If an MKV file does not appear, choose Browse / On My iPhone.</small><button onclick="send()">UPLOAD SELECTED VIDEOS</button><p id="status">Ready.</p><small>After a successful upload, AZ CINEMA has its own saved copy on the TV. You can delete the original from your phone.</small>
<script>
const token=new URLSearchParams(location.search).get('token');
async function send(){const files=[...document.getElementById('files').files];if(!files.length){alert('Choose at least one video');return}
const status=document.getElementById('status');let done=0;
for(const file of files){status.textContent='Uploading '+file.name+' ('+(done+1)+'/'+files.length+')…';const form=new FormData();form.append('file',file);form.append('category',document.getElementById('category').value);form.append('name',file.name);
try{const r=await fetch('/upload?token='+encodeURIComponent(token),{method:'POST',body:form});if(!r.ok)throw new Error(await r.text());done++}catch(e){status.textContent='Stopped at '+file.name+': '+e.message;return}}
status.textContent='Finished! '+done+' video(s) saved on your TV. You can now delete the phone copies.';document.getElementById('files').value='';
}
</script></body></html>
""".trimIndent()
}
