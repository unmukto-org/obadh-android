// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.unmukto.obadh.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All HTTP work is bounded and isolated from Obadh's engine and input executors. */
object KlipyClient {
    val configured get() = BuildConfig.KLIPY_APP_KEY.isNotEmpty()
    private val network=ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,ArrayBlockingQueue(32),
        { r -> Thread(r,"Obadh online media").apply { priority=Thread.NORM_PRIORITY-1 } },ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }
    private fun encode(value: String)=URLEncoder.encode(value,"UTF-8")
    private fun endpoint(kind: MediaKind, route: String, params: Map<String,String>) =
        "https://api.klipy.com/api/v1/${BuildConfig.KLIPY_APP_KEY}/${kind.path}/$route?"+
            params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
    private suspend fun request(url: String, max: Int, post: String?=null, progress: (Int)->Unit={}): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val connection=AtomicReference<HttpURLConnection?>()
            val future=runCatching { network.submit {
                if(!continuation.isActive)return@submit
                var conn: HttpURLConnection?=null
                try {
                    conn=URL(url).openConnection() as HttpURLConnection;connection.set(conn)
                    if(!continuation.isActive)return@submit
                    conn.connectTimeout=10000;conn.readTimeout=12000;conn.instanceFollowRedirects=false
                    conn.useCaches=false;conn.setRequestProperty("User-Agent","Mozilla/5.0 Obadh-Android/${BuildConfig.VERSION_NAME}")
                    conn.setRequestProperty("Accept",if(max==MediaSafety.MAX_JSON)"application/json" else "image/gif,image/webp")
                    if(post!=null) {
                        conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json")
                        conn.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
                    }
                    val code=conn.responseCode
                    if(code!=200) throw MediaException(when(code) {
                        429 -> MediaFailure.RATE_LIMIT
                        401,403 -> MediaFailure.AUTH
                        else -> MediaFailure.SERVER
                    })
                    if(conn.contentLengthLong>max) throw MediaException(MediaFailure.TOO_LARGE)
                    val bytes=conn.inputStream.use { MediaSafety.read(it,max,{ continuation.isActive },progress) }
                    if(continuation.isActive)continuation.resume(bytes)
                } catch(e: Exception) {
                    // URLs contain the application key. Never surface transport messages,
                    // response bodies or exceptions containing those URLs to logs or UI.
                    if(continuation.isActive)continuation.resumeWithException(if(e is MediaException)e else MediaException(MediaFailure.OFFLINE))
                } finally { conn?.disconnect();connection.set(null) }
            }}.getOrElse { continuation.resumeWithException(MediaException(MediaFailure.BUSY));return@suspendCancellableCoroutine }
            continuation.invokeOnCancellation { connection.get()?.disconnect();future.cancel(true);(future as? Runnable)?.let(network::remove) }
        }

    suspend fun browse(kind: MediaKind, query: String, page: Int, customer: String): MediaPage {
        if(!configured)throw MediaException(MediaFailure.AUTH)
        require(page in 1..20 && query.length<=120)
        val route=if(query.isBlank())"trending" else "search"
        val params=linkedMapOf("page" to "$page","per_page" to "12","customer_id" to customer,
            "locale" to "bd","content_filter" to "high","format_filter" to "gif,webp")
        if(query.isNotBlank())params["q"]=query
        return parse(request(endpoint(kind,route,params),MediaSafety.MAX_JSON))
    }
    suspend fun item(kind: MediaKind, slug: String): MediaItem {
        if(!MediaSafety.slug(slug))throw MediaException(MediaFailure.INVALID)
        return parse(request(endpoint(kind,"items",mapOf("slugs" to slug)),MediaSafety.MAX_JSON)).items
            .firstOrNull { it.slug==slug } ?: throw MediaException(MediaFailure.INVALID)
    }
    suspend fun image(file: MediaFile, thumb: Boolean=false, progress: (Int)->Unit={}): ByteArray {
        val max=if(thumb)MediaSafety.MAX_THUMB else MediaSafety.MAX_MEDIA
        if(!MediaSafety.mediaUrl(file.url) || file.bytes !in 1..max)throw MediaException(MediaFailure.TOO_LARGE)
        val bytes=request(file.url,max,progress=progress)
        if(!withContext(Dispatchers.Default) { MediaSafety.valid(bytes,file,thumb) })throw MediaException(MediaFailure.INVALID)
        return bytes
    }
    suspend fun shared(kind: MediaKind, slug: String, query: String, customer: String) {
        if(!MediaSafety.slug(slug))return
        val body=JSONObject().put("customer_id",customer).put("q",query).toString()
        request(endpoint(kind,"share/$slug",emptyMap()),MediaSafety.MAX_JSON,body)
    }
    private suspend fun parse(bytes: ByteArray): MediaPage = withContext(Dispatchers.Default) { parsePayload(bytes) }
    private fun parsePayload(bytes: ByteArray): MediaPage {
        try {
            val root=JSONObject(String(bytes,Charsets.UTF_8))
            if(!root.optBoolean("result"))throw MediaException(MediaFailure.SERVER)
            val data=root.optJSONObject("data") ?: throw MediaException(MediaFailure.INVALID)
            val rows=data.optJSONArray("data") ?: throw MediaException(MediaFailure.INVALID)
            val items=buildList {
                for(i in 0 until minOf(rows.length(),50)) {
                    val row=rows.optJSONObject(i) ?: continue
                    // Obadh's partner platform has ads disabled. Never interpret an ad as media.
                    if(row.optString("type")=="ad" || row.optBoolean("is_ad") || row.has("advertisement"))continue
                    val slug=row.optString("slug");if(!MediaSafety.slug(slug))continue
                    val formats=row.optJSONObject("file") ?: continue
                    val files=buildList {
                        for(size in listOf("xs","sm","md","hd")) {
                            val variants=formats.optJSONObject(size) ?: continue
                            for(format in listOf("gif","webp")) {
                                val file=variants.optJSONObject(format) ?: continue
                                val url=file.optString("url");val w=file.optInt("width");val h=file.optInt("height");val n=file.optInt("size")
                                if(MediaSafety.mediaUrl(url) && w in 1..1024 && h in 1..1024 && n in 1..MediaSafety.MAX_MEDIA)
                                    add(MediaFile(url,"image/$format",w,h,n))
                            }
                        }
                    }.distinctBy { it.url }
                    val preview=files.filter { maxOf(it.width,it.height)<=(if(android.os.Build.VERSION.SDK_INT<28)160 else 256) && it.bytes<=MediaSafety.MAX_THUMB }
                        .sortedWith(compareBy<MediaFile> { if(it.mime=="image/gif") 0 else 1 }
                            .thenBy { if(minOf(it.width,it.height)>=120) 0 else 1 }.thenBy { it.bytes }).firstOrNull() ?: continue
                    val author=sequenceOf("user","creator","author").mapNotNull { key ->
                        row.optJSONObject(key)?.let { it.optString("username").ifBlank { it.optString("name") } }
                            ?: row.optString(key).takeIf { it.isNotBlank() && it!="null" }
                    }.filter { it.isNotBlank() }.firstOrNull().orEmpty().take(100)
                    val source=row.optString("source").takeIf { it.isNotBlank() && it!="null" }.orEmpty().take(160)
                    add(MediaItem(slug,row.optString("title").ifBlank { "KLIPY animation" }.take(160),
                        listOf(author,source).filter(String::isNotBlank).joinToString(" · "),preview,files))
                }
            }.distinctBy { it.slug }
            return MediaPage(items,data.optBoolean("has_next"))
        } catch(e: MediaException) { throw e }
        catch(_: Exception) { throw MediaException(MediaFailure.INVALID) }
    }
}
