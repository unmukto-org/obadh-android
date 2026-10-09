// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class Sticker(val id: String, val name: String, val tags: String, val sha256: String, val bytes: Int) {
    val terms = (name + " " + tags).lowercase(Locale.ROOT)
}
data class StickerPack(val id: String, val title: String, val artist: String, val source: String,
    val license: String, val bundled: Boolean, val archiveBytes: Int, val sha256: String, val items: List<Sticker>) {
    fun directory(context: Context) = File(context.filesDir, "sticker-packs/$id-$sha256")
    fun available(context: Context) = bundled || directory(context).isDirectory
    fun open(context: Context, path: String) = if (bundled) context.assets.open("stickers/$id/$path") else File(directory(context),path).inputStream()
    fun expectedFiles() = buildMap {
        put("NOTICE.txt", ""); put("LICENSE.txt", "")
        items.forEach { put(it.id + ".png", it.sha256); put("thumbs/" + it.id + ".webp", "") }
    }
}
/** Immutable local index. Loading is always requested on an IO dispatcher or sticker worker. */
object StickerCatalog {
    const val REPOSITORY = "https://github.com/unmukto-org/obadh-stickers"
    @Volatile private var cached: List<StickerPack>? = null
    @Volatile private var revision: String = ""
    @Synchronized fun load(context: Context): List<StickerPack> {
        cached?.let { return it }
        val json = JSONObject(context.assets.open("stickers/catalog.json").bufferedReader().use { it.readText() })
        check(json.getInt("schema") == 1); revision = json.getString("revision"); check(revision.matches(Regex("[a-f0-9]{40}")))
        val entries = json.getJSONArray("packs"); require(entries.length() <= 24)
        val packs = (0 until entries.length()).map { i ->
            val p = entries.getJSONObject(i); val a = p.getJSONArray("items"); require(a.length() in 1..512)
            val items = (0 until a.length()).map { j -> a.getJSONObject(j).let {
                Sticker(it.getString("id"), it.getString("name"), it.getString("tags"), it.getString("sha256"), it.getInt("bytes"))
            }}
            val pack = StickerPack(p.getString("id"),p.getString("title"),p.getString("artist"),p.getString("source"),p.getString("license"),
                p.getBoolean("bundled"),p.getInt("archiveBytes"),p.getString("sha256"),items)
            require(StickerSafety.validId(pack.id) && pack.sha256.matches(Regex("[a-f0-9]{64}")))
            require(pack.archiveBytes in 1..StickerSafety.MAX_ARCHIVE && items.all { StickerSafety.validId(it.id) && it.bytes in 1..StickerSafety.MAX_FILE })
            require(items.map { it.id }.distinct().size == items.size)
            pack
        }
        require(packs.map { it.id }.distinct().size == packs.size)
        cached = packs; return packs
    }
    fun downloadUrl(pack: StickerPack): String {
        check(revision.isNotEmpty())
        return "https://raw.githubusercontent.com/unmukto-org/obadh-stickers/$revision/packs/${pack.id}.zip"
    }
    fun search(packs: List<StickerPack>, query: String, limit: Int = 120): List<Pair<StickerPack,Sticker>> {
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty).take(8)
        if (words.isEmpty()) return packs.flatMap { p -> p.items.take(12).map { p to it } }.take(limit)
        return packs.asSequence().flatMap { p -> p.items.asSequence().filter { s -> words.all { it in s.terms } }.map { p to it } }.take(limit).toList()
    }
}
