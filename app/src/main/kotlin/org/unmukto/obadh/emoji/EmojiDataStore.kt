package org.unmukto.obadh.emoji

import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.Locale

enum class EmojiCategory {
    RECENTS, SMILEYS, PEOPLE, ANIMALS, FOOD, ACTIVITIES, TRAVEL, OBJECTS, SYMBOLS, FLAGS;

    val label: String
        get() = when (this) {
            RECENTS -> "Recently Used"
            SMILEYS -> "Smileys and People"
            PEOPLE -> "People"
            ANIMALS -> "Animals and Nature"
            FOOD -> "Food and Drink"
            ACTIVITIES -> "Activities"
            TRAVEL -> "Travel and Places"
            OBJECTS -> "Objects"
            SYMBOLS -> "Symbols"
            FLAGS -> "Flags"
        }

    /** Glyph shown on the category bar (system emoji font, no icon assets to ship). */
    val glyph: String
        get() = when (this) {
            RECENTS -> "🕘"
            SMILEYS, PEOPLE -> "🙂"
            ANIMALS -> "🐾"
            FOOD -> "🍴"
            ACTIVITIES -> "⚽"
            TRAVEL -> "🚗"
            OBJECTS -> "💡"
            SYMBOLS -> "❤"
            FLAGS -> "🏳"
        }

    companion object {
        /** People merge into Smileys, as in native keyboards. */
        val visible = listOf(RECENTS, SMILEYS, ANIMALS, FOOD, ACTIVITIES, TRAVEL, OBJECTS, SYMBOLS, FLAGS)

        fun fromStorageCode(code: Int): EmojiCategory? = when (code) {
            1 -> SMILEYS; 2 -> PEOPLE; 3 -> ANIMALS; 4 -> FOOD; 5 -> ACTIVITIES
            6 -> TRAVEL; 7 -> OBJECTS; 8 -> SYMBOLS; 9 -> FLAGS
            else -> null
        }
    }
}

data class EmojiItem(
    val emoji: String,
    val category: EmojiCategory,
    val group: String,
    val subgroup: String,
    val name: String,
    val keywords: String,
    val normalizedName: String,
    val normalizedKeywords: List<String>,
    val searchText: String,
)

/**
 * The Unicode/CLDR catalog (`emoji.bin`, OBEMOJI1) with English search and skin-tone
 * variant grouping. Never touched on the typing path: the panel loads it lazily, off
 * the main thread.
 */
class EmojiDataStore private constructor(
    val items: List<EmojiItem>,
    private val index: SearchIndex,
) {
    private val displayByCategory: Map<EmojiCategory, List<EmojiItem>> =
        items.filter { !isSkinToneVariant(it.name) }.groupBy { it.category }
    private val byEmoji: Map<String, EmojiItem> = items.associateBy { it.emoji }
    private val variantOptions: Map<String, List<EmojiItem>> = makeVariantOptions(items)

    fun items(category: EmojiCategory): List<EmojiItem> =
        if (category == EmojiCategory.SMILEYS) {
            (displayByCategory[EmojiCategory.SMILEYS] ?: emptyList()) +
                (displayByCategory[EmojiCategory.PEOPLE] ?: emptyList())
        } else displayByCategory[category] ?: emptyList()

    fun item(emoji: String): EmojiItem? = byEmoji[emoji]
    fun items(emojis: List<String>): List<EmojiItem> = emojis.mapNotNull { byEmoji[it] }
    fun variantOptions(item: EmojiItem): List<EmojiItem> = variantOptions[item.emoji] ?: emptyList()

    fun search(query: String, limit: Int): List<EmojiItem> {
        val q = normalize(query)
        if (q.isEmpty() || limit <= 0) return emptyList()
        return index.search(q, items, limit)
    }

    companion object {
        val EMPTY = EmojiDataStore(emptyList(), SearchIndex(emptyMap()))

        fun normalize(value: String): String =
            Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
                .lowercase(Locale.ROOT)

        fun keywordTokens(value: String): List<String> =
            value.split(Regex("[^\\p{L}\\p{N}]+")).map { normalize(it) }.filter { it.isNotEmpty() }

        fun decode(data: ByteBuffer): EmojiDataStore? {
            val b = EmojiBytes(data)
            if (b.size < HEADER || !b.hasMagic("OBEMOJI1") || b.u32(8) != 1) return null
            val itemCount = b.u32(12)
            val tokenCount = b.u32(16)
            val postingCount = b.u32(20)
            val itemOffset = b.u32(24)
            val tokenOffset = b.u32(28)
            val postingOffset = b.u32(32)
            val stringOffset = b.u32(36)
            val stringSize = b.u32(40)
            fun ok(off: Int, n: Int, stride: Int) =
                off >= 0 && n >= 0 && (n == 0 || (off <= b.size && n <= (b.size - off) / stride))
            if (!ok(itemOffset, itemCount, ITEM) || !ok(tokenOffset, tokenCount, TOKEN) ||
                !ok(postingOffset, postingCount, POSTING) || stringOffset < 0 || stringSize < 0 ||
                stringOffset.toLong() + stringSize > b.size
            ) return null

            fun str(rel: Int): String? {
                val start = stringOffset + rel
                if (start < stringOffset || start >= stringOffset + stringSize) return null
                return b.cString(start, stringOffset + stringSize)
            }

            val items = ArrayList<EmojiItem>(itemCount)
            for (i in 0 until itemCount) {
                val off = itemOffset + i * ITEM
                val category = EmojiCategory.fromStorageCode(b.u8(off)) ?: return null
                val f = Array(8) { str(b.u32(off + 4 + it * 4)) ?: return null }
                items += EmojiItem(
                    emoji = f[0], category = category, group = f[1], subgroup = f[2], name = f[3],
                    keywords = f[4], normalizedName = f[5],
                    normalizedKeywords = f[6].split(US).filter { it.isNotEmpty() }, searchText = f[7],
                )
            }

            val postings = HashMap<String, List<Posting>>(tokenCount * 2)
            for (t in 0 until tokenCount) {
                val off = tokenOffset + t * TOKEN
                val token = str(b.u32(off)) ?: return null
                val start = b.u32(off + 4)
                val count = b.u32(off + 8)
                if (start < 0 || count < 0 || start.toLong() + count > postingCount) return null
                val list = ArrayList<Posting>(count)
                for (p in start until start + count) {
                    val po = postingOffset + p * POSTING
                    val itemIndex = b.u32(po)
                    if (itemIndex < 0 || itemIndex >= itemCount) return null
                    list += Posting(itemIndex, b.u16(po + 4))
                }
                postings[token] = list
            }
            return EmojiDataStore(items, SearchIndex(postings))
        }

        private const val HEADER = 44
        private const val ITEM = 36
        private const val TOKEN = 12
        private const val POSTING = 8

        // --- skin tones

        internal fun isSkinToneVariant(name: String) = skinToneBaseKey(name) != null

        private fun skinToneBaseKey(name: String): String? {
            val sep = name.indexOf(':')
            if (sep < 0) return null
            val suffix = normalize(name.substring(sep + 1))
            if (!suffix.endsWith("skin tone")) return null
            return normalize(name.substring(0, sep))
        }

        private fun skinToneRank(name: String): Int {
            val n = normalize(name)
            return when {
                "light skin tone" in n && "medium-light" !in n -> 1
                "medium-light skin tone" in n -> 2
                "medium skin tone" in n && "medium-light" !in n && "medium-dark" !in n -> 3
                "medium-dark skin tone" in n -> 4
                "dark skin tone" in n && "medium-dark" !in n -> 5
                else -> 99
            }
        }

        private fun makeVariantOptions(items: List<EmojiItem>): Map<String, List<EmojiItem>> {
            val baseByName = HashMap<String, EmojiItem>()
            for (item in items) if (!isSkinToneVariant(item.name)) baseByName.putIfAbsent(normalize(item.name), item)
            val groups = HashMap<String, MutableList<EmojiItem>>()
            for (item in items) skinToneBaseKey(item.name)?.let { groups.getOrPut(it) { ArrayList() } += item }

            val out = HashMap<String, List<EmojiItem>>()
            for ((baseKey, variants) in groups) {
                val base = baseByName[baseKey] ?: continue
                val sorted = variants.sortedWith(compareBy({ skinToneRank(it.name) }, { it.name }))
                val options = listOf(base) + sorted
                if (options.size > 1) for (o in options) out[o.emoji] = options
            }
            return out
        }
    }

    internal data class Posting(val itemIndex: Int, val weight: Int)

    internal class SearchIndex(private val postingsByToken: Map<String, List<Posting>>) {
        private val sortedTokens: List<String> = postingsByToken.keys.sorted()

        fun search(normalizedQuery: String, items: List<EmojiItem>, limit: Int): List<EmojiItem> {
            val terms = keywordTokens(normalizedQuery).take(5)
            if (terms.isEmpty()) return emptyList()

            // item index -> (term index -> best score)
            val acc = HashMap<Int, HashMap<Int, Int>>()
            terms.forEachIndexed { termIndex, term ->
                for ((itemIndex, score) in bestTokenScores(term)) {
                    val m = acc.getOrPut(itemIndex) { HashMap() }
                    m[termIndex] = minOf(m[termIndex] ?: Int.MAX_VALUE, score)
                }
            }
            val wantsSkin = "skin" in normalizedQuery || "tone" in normalizedQuery
            val ranked = ArrayList<Triple<EmojiItem, Int, Int>>() // item, score, index
            for ((itemIndex, perTerm) in acc) {
                if (perTerm.size != terms.size || itemIndex !in items.indices) continue
                val item = items[itemIndex]
                if (isSkinToneVariant(item.name) && !wantsSkin) continue
                ranked += Triple(item, phraseAdjusted(perTerm.values.sum(), item, normalizedQuery), itemIndex)
            }
            ranked.sortWith(compareBy({ it.second }, { it.third }))
            return ranked.take(limit).map { it.first }
        }

        private fun bestTokenScores(term: String): Map<Int, Int> {
            val best = HashMap<Int, Int>(24)
            fun add(token: String, base: Int) {
                for (p in postingsByToken[token] ?: return) {
                    val s = base + p.weight
                    best[p.itemIndex] = minOf(best[p.itemIndex] ?: Int.MAX_VALUE, s)
                }
            }
            add(term, 0)
            if (term.length >= 2) for (t in prefixTokens(term)) if (t != term) add(t, 20)
            if (term.length >= 3) {
                for (t in sortedTokens) if (t.contains(term) && !t.startsWith(term)) add(t, 46)
                val maxD = if (term.length >= 6) 2 else 1
                val termChars = term.toList()
                for (t in sortedTokens) {
                    if (kotlin.math.abs(t.length - term.length) > maxD) continue
                    BanglaEmojiSearchStore.boundedEditDistance(termChars, t.toList(), maxD)?.let { add(t, 54 + it * 10) }
                }
            }
            return best
        }

        private fun prefixTokens(prefix: String): List<String> {
            var lo = 0
            var hi = sortedTokens.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sortedTokens[mid] < prefix) lo = mid + 1 else hi = mid
            }
            var end = lo
            while (end < sortedTokens.size && sortedTokens[end].startsWith(prefix)) end++
            return sortedTokens.subList(lo, end)
        }

        private fun phraseAdjusted(score: Int, item: EmojiItem, q: String): Int = when {
            item.normalizedName == q -> score - 80
            q in item.normalizedKeywords -> score - 70
            item.normalizedName.startsWith(q) -> score - 54
            item.searchText.startsWith(q) -> score - 42
            q in item.searchText -> score - 18
            else -> score
        }
    }
}
