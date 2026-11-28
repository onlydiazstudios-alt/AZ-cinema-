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
    private val telegram = TelegramManager.get(context)
    private fun root(): File = File(context.filesDir, "AZLibrary")
    private fun folder(category: String): File = File(root(), if (category == "TV Shows") "TV Shows" else "Movies").apply { mkdirs() }
    fun transferUrl(): String = "http://${MainActivity.localIp()}:8765/companion?token=$token"

    override fun serve(session: IHTTPSession): Response {
        val supplied = session.parameters["token"]?.firstOrNull()
        if (supplied != token) return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Pairing token missing or incorrect")
        return when {
            session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html" || session.uri == "/companion") ->
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", companionPage())
            session.method == Method.POST && session.uri == "/upload" -> receive(session)
            session.method == Method.GET && session.uri == "/telegram/status" -> json(telegram.statusJson())
            session.method == Method.GET && session.uri == "/telegram/chats" -> json(telegram.chatsJson())
            session.method == Method.GET && session.uri == "/telegram/messages" -> {
                val chatId = session.parameters["chat_id"]?.firstOrNull()?.toLongOrNull() ?: 0L
                val fromId = session.parameters["from_message_id"]?.firstOrNull()?.toLongOrNull() ?: 0L
                if (chatId == 0L) newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing chat_id")
                else try { json(telegram.messagesJson(chatId, fromId)) } catch (e: Exception) { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Telegram error") }
            }
            session.method == Method.GET && session.uri == "/telegram/download" -> {
                val fileId = session.parameters["file_id"]?.firstOrNull()?.toIntOrNull() ?: 0
                val name = session.parameters["name"]?.firstOrNull() ?: "telegram-video.mp4"
                try { json(JSONObject().put("ok", true).put("status", telegram.enqueueDownload(fileId, name)).toString()) }
                catch (e: Exception) { newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, e.message ?: "Download could not start") }
            }
            session.method == Method.GET && session.uri == "/health" ->
                newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("ok", true).put("app", "AZ CINEMA").toString())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun json(body: String): Response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun receive(session: IHTTPSession): Response {
        return try {
            val tempFiles = HashMap<String, String>()
            session.parseBody(tempFiles)
            val temp = tempFiles["file"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file received")
            val params = session.parameters
            val rawName = params["name"]?.firstOrNull() ?: "video"
            val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "video" }

            val tvMatch = Regex("(?i)^(.+?)[ ._-]+S(\\d{1,2})E(\\d{1,3})").find(cleanName)
            val destinationFolder = if (tvMatch != null) {
                val showName = tvMatch.groupValues[1].replace(Regex("[._]+"), " ").trim().take(70).ifBlank { "TV Show" }
                val seasonName = "Season " + tvMatch.groupValues[2].toInt()
                File(File(folder("TV Shows"), showName), seasonName).apply { mkdirs() }
            } else folder("Movies")
            val destination = File(destinationFolder, "${System.currentTimeMillis()}_$cleanName")
            FileInputStream(temp).use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
            newFixedLengthResponse(Response.Status.OK, "application/json",
                JSONObject().put("ok", true).put("name", cleanName).put("folder", destinationFolder.name).put("bytes", destination.length()).toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${e.message}")
        }
    }

    private fun companionPage(): String = """
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="#09070f"><title>AZ CINEMA Mobile</title>
<style>
:root{color-scheme:dark;--purple:#b77aff;--muted:#bdb3c9}*{box-sizing:border-box}
body{margin:0;background:#09070f;color:#f8f5ff;font:16px/1.45 -apple-system,BlinkMacSystemFont,Arial,sans-serif}
header{padding:calc(18px + env(safe-area-inset-top)) 20px 17px;background:#100c19;border-bottom:1px solid #34263f;position:sticky;top:0;z-index:3}
.brand{font-size:25px;font-weight:900;letter-spacing:3px}.brand span{color:var(--purple)}.sub{color:var(--muted);font-size:13px}
main{max-width:760px;margin:auto;padding:18px 16px 55px}.status{display:flex;align-items:center;gap:9px;color:#a8efc2;font-size:13px;margin:14px 0 18px}.dot{width:9px;height:9px;background:#42d68b;border-radius:50%;box-shadow:0 0 12px #42d68b}
h1{font-size:29px;line-height:1.15;margin:4px 0 7px}h2{font-size:19px;margin:0 0 8px}.muted{color:var(--muted);font-size:14px}
.panel{background:linear-gradient(145deg,#191223,#100d17);border:1px solid #382847;border-radius:17px;padding:17px;margin:14px 0}
.kicker{font-size:11px;letter-spacing:2px;color:#d8b8ff;font-weight:800;margin-bottom:7px}
button,.filepick{display:block;width:100%;border:0;border-radius:12px;background:#9253cf;color:white;font-size:16px;font-weight:800;padding:15px 16px;text-align:center;margin-top:13px}
button.secondary{background:#241a30;border:1px solid #59436d;color:#eee4fa}
button:disabled{opacity:.48}
input[type=file]{display:none}.pill{display:inline-block;border:1px solid #493758;color:#d7c5e9;border-radius:30px;padding:6px 10px;font-size:12px;margin:3px 4px 3px 0}
.source{padding:12px 0;border-top:1px solid #30243a;display:flex;justify-content:space-between;gap:12px;align-items:center}
.source small{display:block;color:var(--muted)}.queue{margin-top:10px}.item{background:#0e0b14;border:1px solid #30243a;border-radius:11px;padding:11px;margin-top:8px;font-size:13px;overflow-wrap:anywhere}
.track{height:6px;border-radius:9px;background:#32263e;margin-top:8px;overflow:hidden}.fill{height:100%;background:#b77aff;width:0}
footer{color:#887d96;text-align:center;font-size:12px;padding:22px}
</style></head><body>
<header><div class="brand">AZ <span>CINEMA</span></div><div class="sub">MOBILE COMPANION · ANOTHER ZONE</div></header>
<main>
<div class="status"><span class="dot"></span> Connected to your AZ CINEMA TV</div>
<h1>Your downloads.<br>Your cinema.</h1><p class="muted">Choose videos on your iPhone and send them directly to the TV. The TV keeps its own copy and sorts episode files automatically.</p>
<section class="panel">
<div class="kicker">01 · TELEGRAM ACCOUNT</div><h2>Your chats, channels and bots</h2>
<div id="tgStatus" class="muted">Checking the Telegram connection…</div>
<p class="muted">If the TV is not connected, use Telegram on your iPhone: Settings → Devices → Link Desktop Device, then scan the Telegram QR shown on the TV screen. Do not send your login code or password to anyone.</p>
<button class="secondary" onclick="loadTelegram()">REFRESH TELEGRAM</button>
<div id="tgChats" class="queue"><div class="muted">Your Telegram chats will appear here after login.</div></div>
<div id="tgMessages" class="queue"></div>
<button id="tgDownload" onclick="downloadTelegramSelected()" disabled>DOWNLOAD SELECTED TO TV</button>
<div id="tgQueue" class="queue"></div>
</section>
<section class="panel">
<div class="kicker">02 · CHOOSE DOWNLOADS</div><h2>Select videos from your iPhone</h2>
<p class="muted">For now, save the Telegram videos to the iPhone Files app, then select them here. You can select multiple files, including MKV videos.</p>
<label class="filepick" for="files">CHOOSE DOWNLOADS</label><input id="files" type="file" accept="video/*,.mkv,.avi,.mov,.webm,.ts" multiple>
<div id="selected" class="muted" style="margin-top:10px">No videos selected</div>
<button id="send" onclick="sendFiles()" disabled>DOWNLOAD & UPLOAD TO TV</button>
</section>
<section class="panel">
<div class="kicker">03 · TRANSFER QUEUE</div><h2>Sending to your TV</h2><p class="muted">Keep this page open and your iPhone connected to the same Wi-Fi as the TV during transfers.</p><div id="queue" class="queue"><div class="muted">Your transfer queue is empty.</div></div>
</section>
<section class="panel">
<div class="kicker">04 · YOUR TV LIBRARY</div><h2>Automatic organisation</h2>
<div class="source"><strong>Movies</strong><span class="pill">Movies/</span></div>
<div class="source"><strong>TV Shows</strong><span class="pill">Show / Season / Episode</span></div>
<p class="muted">Filenames containing S01E01-style episode numbers are sorted into the matching show and season. Other videos go into Movies. The TV stores the files independently of your phone.</p>
</section>
</main><footer>ANOTHER ZONE · DIAZ DIMENSION · 707</footer>
<script>
const params=new URLSearchParams(location.search);const token=params.get('token');const picker=document.getElementById('files');const sendButton=document.getElementById('send');let selected=[];
picker.addEventListener('change',()=>{selected=[...picker.files];document.getElementById('selected').textContent=selected.length?selected.length+' file(s) selected · '+selected.map(f=>f.name).join(', '):'No videos selected';sendButton.disabled=!selected.length;});
function addQueue(name,status,percent){const el=document.createElement('div');el.className='item';const label=document.createElement('div');label.textContent=name+' — '+status;const track=document.createElement('div');track.className='track';const fill=document.createElement('div');fill.className='fill';fill.style.width=percent+'%';track.append(fill);el.append(label,track);document.getElementById('queue').append(el);return {label,fill};}
function sendOne(file){return new Promise((resolve,reject)=>{const ui=addQueue(file.name,'Waiting',0);const xhr=new XMLHttpRequest();xhr.open('POST','/upload?token='+encodeURIComponent(token));xhr.upload.onprogress=e=>{if(e.lengthComputable){const p=Math.round(e.loaded/e.total*100);ui.label.textContent=file.name+' — Uploading '+p+'%';ui.fill.style.width=p+'%';}};xhr.onload=()=>{if(xhr.status>=200&&xhr.status<300){ui.label.textContent=file.name+' — Saved on TV';ui.fill.style.width='100%';resolve();}else{ui.label.textContent=file.name+' — Failed: '+xhr.responseText;reject(new Error(xhr.responseText||'Upload failed'));}};xhr.onerror=()=>{ui.label.textContent=file.name+' — Network error';reject(new Error('Network error'));};const form=new FormData();form.append('file',file);form.append('name',file.name);xhr.send(form);});}
async function sendFiles(){if(!token){alert('Pairing token is missing. Reopen the TV pairing link.');return;}sendButton.disabled=true;let completed=0;for(const f of selected){try{await sendOne(f);completed++;}catch(e){alert('Transfer stopped after '+completed+' file(s). '+e.message);sendButton.disabled=false;return;}}document.getElementById('selected').textContent='Finished: '+completed+' video(s) saved on your TV.';picker.value='';selected=[];sendButton.disabled=true;}
const tgSelected=new Map();
async function tgFetch(path){const response=await fetch(path+(path.includes('?')?'&':'?')+'token='+encodeURIComponent(token));const body=await response.text();if(!response.ok)throw new Error(body||'Telegram request failed');return JSON.parse(body);}
function tgEsc(v){return String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
async function loadTelegram(){const statusBox=document.getElementById('tgStatus');statusBox.textContent='Checking Telegram…';try{const s=await tgFetch('/telegram/status');statusBox.textContent=s.connected?'✓ '+s.state:(s.state||'Telegram is not connected')+(s.error?' · '+s.error:'');if(s.connected){await loadTelegramChats();}else{document.getElementById('tgChats').innerHTML='<div class="muted">Complete QR login on the TV, then tap Refresh Telegram.</div>';document.getElementById('tgMessages').innerHTML='';}renderTgQueue(s.downloads||[]);}catch(e){statusBox.textContent='Telegram unavailable: '+e.message;}}
async function loadTelegramChats(){const chats=await tgFetch('/telegram/chats');const box=document.getElementById('tgChats');if(!chats.length){box.innerHTML='<div class="muted">No chats found yet.</div>';return;}box.innerHTML=chats.map(ch=>'<div class="source"><div><strong>'+tgEsc(ch.title||'Telegram chat')+'</strong><small>'+tgEsc(ch.type||'Chat')+'</small></div><button class="secondary" onclick="loadTelegramMessages('+Number(ch.id)+',\''+tgEsc(ch.title||'').replace(/'/g,'&#39;')+'\')">OPEN</button></div>').join('');}
async function loadTelegramMessages(chatId,title){const box=document.getElementById('tgMessages');box.innerHTML='<div class="muted">Loading videos from '+tgEsc(title)+'…</div>';try{const items=await tgFetch('/telegram/messages?chat_id='+encodeURIComponent(chatId));if(!items.length){box.innerHTML='<div class="muted">No recent video or video-file messages found in this chat.</div>';return;}box.innerHTML='<h2>'+tgEsc(title)+'</h2>'+items.map(x=>'<label class="source"><span><input type="checkbox" data-file-id="'+Number(x.fileId)+'" data-name="'+tgEsc(x.name)+'" style="display:inline;width:auto;margin-right:8px" onchange="toggleTgFile(this)"><strong>'+tgEsc(x.name)+'</strong><small>'+((Number(x.size)||0)/1048576).toFixed(1)+' MB</small></span><span class="pill">VIDEO</span></label>').join('');}catch(e){box.innerHTML='<div class="muted">Could not load messages: '+tgEsc(e.message)+'</div>';}}
function toggleTgFile(input){const id=Number(input.dataset.fileId);if(input.checked)tgSelected.set(id,input.dataset.name);else tgSelected.delete(id);document.getElementById('tgDownload').disabled=tgSelected.size===0;}
async function downloadTelegramSelected(){const btn=document.getElementById('tgDownload');btn.disabled=true;for(const [id,name] of tgSelected){try{await tgFetch('/telegram/download?file_id='+id+'&name='+encodeURIComponent(name));}catch(e){alert('Could not queue '+name+': '+e.message);}}tgSelected.clear();document.querySelectorAll('#tgMessages input[type=checkbox]').forEach(x=>x.checked=false);await loadTelegram();btn.disabled=true;}
function renderTgQueue(items){const box=document.getElementById('tgQueue');box.innerHTML=items.length?'<h2>TV download queue</h2>'+items.map(x=>'<div class="item">File '+x.fileId+' — '+tgEsc(x.status)+'</div>').join(''):'<div class="muted">No Telegram downloads queued.</div>';}
setInterval(()=>{if(token)loadTelegram();},7000);

</script></body></html>
""".trimIndent()
}
