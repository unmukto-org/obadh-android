package org.unmukto.obadh.emoji

import java.nio.ByteBuffer
import java.text.Normalizer

/** Up to 3 high-confidence emoji for an exact Bangla word. Exact only, never fuzzy. */
interface BanglaEmojiSuggesting {
    fun emojis(banglaWord: String): List<String>
}

/**
 * Reads `emoji-bn.bin` (OBEMOJIBN1): a sorted-key binary mapping a normalized
 * Bangla word to up to 3 emoji, answered by binary search over the (mapped)
 * bytes. A miss allocates nothing but the needle, so it is safe on the
 * per-keystroke path. Built by obadh-ios's generate-emoji-data.py.
 */
class BanglaEmojiSuggestionStore private constructor(
    private val bytes: EmojiBytes?,
    private val keyCount: Int,
    private val keyRecordsOffset: Int,
    private val stringBlobOffset: Int,
) : BanglaEmojiSuggesting {

    override fun emojis(banglaWord: String): List<String> {
        val b = bytes ?: return emptyList()
        if (keyCount == 0) return emptyList()
        val key = normalize(banglaWord)
        if (key.isEmpty()) return emptyList()
        val needle = key.toByteArray(Charsets.UTF_8)

        var low = 0
        var high = keyCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val record = keyRecordsOffset + mid * 8
            val cmp = b.compareCString(stringBlobOffset + b.u32(record), needle)
            when {
                cmp == 0 -> {
                    val list = b.cString(stringBlobOffset + b.u32(record + 4)) ?: return emptyList()
                    return list.split(US).filter { it.isNotEmpty() }
                }
                cmp < 0 -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return emptyList()
    }

    companion object {
        val EMPTY = BanglaEmojiSuggestionStore(null, 0, 0, 0)

        /**
         * Must stay byte-for-byte identical to `normalize_bangla` in the generator so the
         * composed word matches the interned key: NFC, strip ZWNJ/ZWJ, trim. Never strip
         * combining marks: Bangla matras are essential.
         */
        fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFC)
                .filter { it != '‌' && it != '‍' }
                .trim()

        fun decode(data: ByteBuffer): BanglaEmojiSuggestionStore? {
            val b = EmojiBytes(data)
            if (b.size < 30 || !b.hasMagic("OBEMOJIBN1")) return null
            if (b.u16(10) != 1) return null
            val keyCount = b.u32(14)
            val keyRecords = b.u32(18)
            val blob = b.u32(22)
            val blobSize = b.u32(26)
            val valid = keyCount >= 0 && keyRecords >= 30 && keyRecords.toLong() + keyCount * 8L <= b.size &&
                blob >= 0 && blobSize >= 0 && blob.toLong() + blobSize <= b.size
            return if (valid) BanglaEmojiSuggestionStore(b, keyCount, keyRecords, blob) else null
        }
    }
}
