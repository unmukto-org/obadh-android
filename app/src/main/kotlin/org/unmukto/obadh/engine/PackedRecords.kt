package org.unmukto.obadh.engine

/** Decoders for the engine's packed little-endian lists. Pure, JVM-testable. */
object PackedRecords {
    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun u16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[o + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    /** `[u32 count]` then count x `[u32 len][utf8]`. */
    fun parseStringList(bytes: ByteArray): List<String> {
        if (bytes.size < 4) return emptyList()
        val count = u32(bytes, 0)
        var offset = 4
        val items = ArrayList<String>(count.coerceAtLeast(0))
        repeat(count) {
            if (offset + 4 > bytes.size) return items
            val len = u32(bytes, offset)
            offset += 4
            if (len < 0 || offset + len > bytes.size) return items
            items += String(bytes, offset, len, Charsets.UTF_8)
            offset += len
        }
        return items
    }

    /** `[u32 count]` then per record `[u32 len][utf8][u8 source][u16 edit][u16 repair][u64 freq]`. */
    fun parseDetailedCorrections(bytes: ByteArray): List<DetailedCorrection> {
        if (bytes.size < 4) return emptyList()
        val count = u32(bytes, 0)
        var offset = 4
        val items = ArrayList<DetailedCorrection>(count.coerceAtLeast(0))
        repeat(count) {
            if (offset + 4 > bytes.size) return items
            val len = u32(bytes, offset)
            offset += 4
            if (len < 0 || offset + len + 1 + 2 + 2 + 8 > bytes.size) return items
            val text = String(bytes, offset, len, Charsets.UTF_8)
            offset += len
            val source = bytes[offset].toInt() and 0xFF
            offset += 1
            val edit = u16(bytes, offset); offset += 2
            val repair = u16(bytes, offset); offset += 2
            val freq = u64(bytes, offset); offset += 8
            items += DetailedCorrection(text, source, edit, if (repair == 0xFFFF) null else repair, freq)
        }
        return items
    }
}
