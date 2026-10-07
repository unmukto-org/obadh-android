package org.unmukto.obadh.emoji

import kotlin.math.pow

/** Where recents persist. SharedPreferences in the app, a map in tests. */
interface EmojiKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Recently used emoji, newest first. Order is pure most-recently-used (the emoji you just
 * tapped is always first); a time-decayed use count decides only what LEAVES a full list,
 * so a weekly favourite survives a burst of one-off novelty. The list holds one screenful:
 * past the first page nobody scrolls, they search.
 */
class EmojiRecentStore(
    private val store: EmojiKeyValueStore,
    private val limit: Int = DEFAULT_LIMIT,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(val score: Double, val updatedAtMs: Long)

    fun load(): List<String> = store.getString(ORDER_KEY)?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()

    fun record(emoji: String) {
        val ts = now()
        val order = load().filter { it != emoji }.toMutableList()
        order.add(0, emoji)

        val scores = loadScores().toMutableMap()
        scores[emoji] = Entry(decayed(scores[emoji], ts) + 1, ts)

        while (order.size > maxOf(1, limit)) {
            val idx = leastValuableIndex(order, scores, ts) ?: break
            scores.remove(order.removeAt(idx))
        }
        val listed = order.toSet()
        store.putString(ORDER_KEY, order.joinToString(SEP))
        store.putString(SCORES_KEY, scores.filterKeys { it in listed }
            .entries.joinToString(SEP) { "${it.key}$FIELD${it.value.score}$FIELD${it.value.updatedAtMs}" })
    }

    /** Lowest decayed score, oldest first on a tie; index 0 was just used and is never a candidate. */
    private fun leastValuableIndex(order: List<String>, scores: Map<String, Entry>, ts: Long): Int? {
        if (order.size <= 1) return null
        var worst = -1
        var worstScore = Double.MAX_VALUE
        for (i in 1 until order.size) {
            val s = decayed(scores[order[i]], ts)
            if (s <= worstScore) { worst = i; worstScore = s }
        }
        return worst.takeIf { it >= 0 }
    }

    private fun decayed(entry: Entry?, ts: Long): Double {
        entry ?: return 0.0
        val elapsed = maxOf(0L, ts - entry.updatedAtMs)
        return entry.score * 2.0.pow(-elapsed.toDouble() / HALF_LIFE_MS)
    }

    private fun loadScores(): Map<String, Entry> {
        val raw = store.getString(SCORES_KEY) ?: return emptyMap()
        val out = HashMap<String, Entry>()
        for (row in raw.split(SEP)) {
            val f = row.split(FIELD)
            if (f.size == 3) {
                val s = f[1].toDoubleOrNull(); val t = f[2].toLongOrNull()
                if (s != null && t != null) out[f[0]] = Entry(s, t)
            }
        }
        return out
    }

    companion object {
        /** One page of the grid on a standard phone (8 columns x 4 rows). */
        const val DEFAULT_LIMIT = 32
        private const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000
        private const val ORDER_KEY = "keyboard.emoji.recents"
        private const val SCORES_KEY = "keyboard.emoji.recentScores"
        private const val SEP = "\n"
        private const val FIELD = "\t"
    }
}

/** Remembered skin tone per base emoji (chosen by long-press). */
class EmojiVariantPreferenceStore(private val store: EmojiKeyValueStore) {
    fun preferred(baseEmoji: String): String? = load()[baseEmoji]

    fun record(baseEmoji: String, selected: String) {
        val values = load().toMutableMap()
        if (baseEmoji == selected) values.remove(baseEmoji) else values[baseEmoji] = selected
        store.putString(KEY, values.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }

    fun load(): Map<String, String> =
        store.getString(KEY)?.split("\n")?.mapNotNull {
            val f = it.split("\t"); if (f.size == 2) f[0] to f[1] else null
        }?.toMap() ?: emptyMap()

    private companion object { const val KEY = "keyboard.emoji.variantPreferences" }
}
