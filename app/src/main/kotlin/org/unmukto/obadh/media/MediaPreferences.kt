// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Only the :keyboard process owns this cache, including its settings activity. */
object MediaPreferences {
    private val mutex=Mutex()
    private var prefs: SharedPreferences?=null
    @Volatile var enabled=false;private set
    @Volatile var customer="";private set
    @Volatile private var history: List<RecentMedia> = emptyList()
    suspend fun load(context: Context) = mutex.withLock {
        if(prefs!=null)return@withLock
        withContext(Dispatchers.IO) {
            val p=context.getSharedPreferences("online-media",Context.MODE_PRIVATE)
            enabled=p.getBoolean("enabled",false)
            customer=p.getString("customer",null) ?: UUID.randomUUID().toString().also { p.edit().putString("customer",it).apply() }
            history=runCatching {
                val rows=JSONArray(p.getString("recents","[]"))
                buildList { for(i in 0 until minOf(rows.length(),40)) {
                    val row=rows.getJSONObject(i);val file=row.getJSONObject("preview")
                    val preview=MediaFile(file.getString("url"),file.getString("mime"),file.getInt("width"),file.getInt("height"),file.getInt("bytes"))
                    val slug=row.getString("slug")
                    if(MediaSafety.slug(slug) && MediaSafety.mediaUrl(preview.url) && preview.bytes in 1..MediaSafety.MAX_THUMB &&
                        preview.width in 1..256 && preview.height in 1..256 && preview.mime in listOf("image/gif","image/webp"))
                        add(RecentMedia(MediaKind.valueOf(row.getString("kind")),slug,row.getString("title").take(160),row.optString("attribution").take(260),preview))
                }}
            }.getOrDefault(emptyList())
            prefs=p
        }
    }
    fun setEnabled(value: Boolean) {
        enabled=value
        if(!value)MediaController.stopBackground()
        prefs?.edit()?.putBoolean("enabled",value)?.apply()
    }
    fun recent(kind: MediaKind)=history.filter { it.kind==kind }.take(20)
    fun remember(kind: MediaKind,item: MediaItem) {
        if(!enabled)return
        val ref=RecentMedia(kind,item.slug,item.title,item.attribution,item.preview)
        history=(listOf(ref)+history.filterNot { it.kind==kind && it.slug==item.slug }).groupBy { it.kind }.values.flatMap { it.take(20) }
        val rows=JSONArray()
        history.forEach { r -> rows.put(JSONObject().put("kind",r.kind.name).put("slug",r.slug).put("title",r.title).put("attribution",r.attribution)
            .put("preview",JSONObject().put("url",r.preview.url).put("mime",r.preview.mime).put("width",r.preview.width).put("height",r.preview.height).put("bytes",r.preview.bytes))) }
        prefs?.edit()?.putString("recents",rows.toString())?.apply()
    }
    fun clear() { MediaController.stopBackground();history=emptyList();prefs?.edit()?.remove("recents")?.apply() }
}
