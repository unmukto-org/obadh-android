package org.unmukto.obadh.emoji

import java.nio.ByteBuffer

/**
 * Bangla emoji-panel SEARCH over `emoji-bn-search.bin` (OBEMOJIBN1: token -> up to 16
 * ranked emoji). Loaded lazily, only when the panel is in Bangla search, so the
 * English default pays nothing. Exact + prefix + AND-of-terms, plus a fuzzy fallback
 * against the closed emoji keyword vocabulary, where being aggressive is safe.
 */
class BanglaEmojiSearchStore private constructor(
    private val byToken: Map<String, List<String>>,
    private val allKeys: List<String>,
) {
    val isEmpty: Boolean get() = byToken.isEmpty()

    fun search(query: String, limit: Int): List<String> {
        if (byToken.isEmpty() || limit <= 0) return emptyList()
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()

        val perTerm = terms.map { emojiForTerm(it) }.toMutableList()
        // Fuzzy only on a miss (the common case pays nothing).
        for (i in terms.indices) {
            if (perTerm[i].isEmpty() && graphemeCount(terms[i]) >= 2) perTerm[i] = fuzzyEmoji(terms[i])
        }
        if (perTerm.size == 1) return perTerm[0].take(limit)

        val later = perTerm.drop(1).map { it.toSet() }
        val result = ArrayList<String>()
        for (e in perTerm[0]) {
            if (later.all { e in it }) {
                result += e
                if (result.size == limit) break
            }
        }
        return result
    }

    /** Exact-token emoji first (strongest), then emoji from tokens starting with the term. */
    private fun emojiForTerm(term: String): List<String> {
        val seen = LinkedHashSet<String>()
        byToken[term]?.let { seen.addAll(it) }
        var scanned = 0
        for (key in allKeys) {
            if (key != term && key.startsWith(term)) {
                byToken[key]?.let { seen.addAll(it) }
                if (++scanned >= 24) break
            }
        }
        return seen.toList()
    }

    private fun fuzzyEmoji(term: String): List<String> {
        val termG = graphemes(term)
        val maxDistance = if (termG.size >= 6) 2 else 1
        val matches = ArrayList<Pair<String, Int>>()
        for (key in allKeys) {
            val keyG = graphemes(key)
            if (kotlin.math.abs(keyG.size - termG.size) > maxDistance) continue
            boundedEditDistance(termG, keyG, maxDistance)?.let { matches += key to it }
        }
        matches.sortBy { it.second }
        val seen = LinkedHashSet<String>()
        for ((key, _) in matches.take(6)) byToken[key]?.let { seen.addAll(it) }
        return seen.toList()
    }

    companion object {
        val EMPTY = BanglaEmojiSearchStore(emptyMap(), emptyList())

        fun tokenize(query: String): List<String> =
            BanglaEmojiSuggestionStore.normalize(query).split(' ', '\t', '\n', ',').filter { it.isNotEmpty() }

        fun decode(data: ByteBuffer): BanglaEmojiSearchStore? {
            val b = EmojiBytes(data)
            if (b.size < 30 || !b.hasMagic("OBEMOJIBN1")) return null
            val keyCount = b.u32(14)
            val keyRecords = b.u32(18)
            val blob = b.u32(22)
            val blobSize = b.u32(26)
            if (keyCount < 0 || keyRecords.toLong() + keyCount * 8L > b.size || blob.toLong() + blobSize > b.size) return null

            val byToken = HashMap<String, List<String>>(keyCount * 2)
            val keys = ArrayList<String>(keyCount)
            for (i in 0 until keyCount) {
                val rec = keyRecords + i * 8
                val key = b.cString(blob + b.u32(rec)) ?: ""
                val value = b.cString(blob + b.u32(rec + 4)) ?: ""
                byToken[key] = value.split(US).filter { it.isNotEmpty() }
                keys += key
            }
            return BanglaEmojiSearchStore(byToken, keys)
        }

        /** Extended grapheme clusters, so a Bangla conjunct counts as one edit unit. */
        internal fun graphemes(s: String): List<String> {
            val it = java.text.BreakIterator.getCharacterInstance()
            it.setText(s)
            val out = ArrayList<String>()
            var start = it.first()
            var end = it.next()
            while (end != java.text.BreakIterator.DONE) {
                out += s.substring(start, end)
                start = end
                end = it.next()
            }
            return out
        }

        private fun graphemeCount(s: String) = graphemes(s).size

        /** Levenshtein with early exit; null if it exceeds [max]. */
        internal fun <T> boundedEditDistance(lhs: List<T>, rhs: List<T>, max: Int): Int? {
            val n = lhs.size
            val m = rhs.size
            if (kotlin.math.abs(n - m) > max) return null
            if (n == 0) return if (m <= max) m else null
            if (m == 0) return if (n <= max) n else null
            var previous = IntArray(m + 1) { it }
            var current = IntArray(m + 1)
            for (i in 1..n) {
                current[0] = i
                var rowMin = i
                for (j in 1..m) {
                    val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                    rowMin = minOf(rowMin, current[j])
                }
                if (rowMin > max) return null
                val t = previous; previous = current; current = t
            }
            return if (previous[m] <= max) previous[m] else null
        }
    }
}
