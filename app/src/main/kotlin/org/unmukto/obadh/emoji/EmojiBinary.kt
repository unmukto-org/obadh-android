package org.unmukto.obadh.emoji

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Little-endian reads over a (possibly memory-mapped) buffer, plus NUL-string helpers. */
internal class EmojiBytes(source: ByteBuffer) {
    val buf: ByteBuffer = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val size: Int get() = buf.limit()

    fun u8(at: Int): Int = buf.get(at).toInt() and 0xFF
    fun u16(at: Int): Int = buf.getShort(at).toInt() and 0xFFFF
    fun u32(at: Int): Int = buf.getInt(at)

    fun hasMagic(magic: String): Boolean {
        if (size < magic.length) return false
        return magic.indices.all { buf.get(it).toInt() == magic[it].code }
    }

    /** NUL-terminated UTF-8 string starting at [start], or null if out of range. */
    fun cString(start: Int, limit: Int = size): String? {
        if (start < 0 || start >= limit) return null
        var end = start
        while (end < limit && buf.get(end).toInt() != 0) end++
        val out = ByteArray(end - start)
        for (i in out.indices) out[i] = buf.get(start + i)
        return String(out, Charsets.UTF_8)
    }

    /** Compare the NUL-terminated key at [offset] with [needle] (no NUL): <0, 0, >0. */
    fun compareCString(offset: Int, needle: ByteArray): Int {
        var i = 0
        while (true) {
            val keyByte = if (offset + i < size) u8(offset + i) else 0
            val needleEnded = i >= needle.size
            if (keyByte == 0 && needleEnded) return 0
            if (keyByte == 0) return -1
            if (needleEnded) return 1
            val n = needle[i].toInt() and 0xFF
            if (keyByte != n) return if (keyByte < n) -1 else 1
            i++
        }
    }
}

internal const val US = '\u001f'
