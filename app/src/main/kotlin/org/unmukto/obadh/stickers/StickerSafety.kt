// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Fail closed on archive contents; shared by background installs and local catalog validation. */
object StickerSafety {
    const val MAX_ARCHIVE = 8 * 1024 * 1024
    const val MAX_EXPANDED = 24 * 1024 * 1024
    const val MAX_FILE = 256 * 1024
    fun validId(value: String) = value.length in 1..90 && value.matches(Regex("[a-z0-9][a-z0-9_-]*"))
    fun acceptsPng(types: List<String>) = types.any { it in listOf("image/png", "image/*", "*/*") }
    fun digest(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= limit) { "File too large" }; output.write(buffer, 0, n) }
        return output.toByteArray()
    }
    fun validPng(bytes: ByteArray): Boolean {
        if (bytes.size < 33 || !bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) return false
        fun int(at: Int) = java.nio.ByteBuffer.wrap(bytes, at, 4).int
        if (!bytes.copyOfRange(12,16).contentEquals("IHDR".toByteArray())) return false
        if (int(16) !in 1..512 || int(20) !in 1..512) return false
        // Never decode APNG/animations on the keyboard. Reject truncated/overlong chunks too.
        var at = 8
        while (at + 12 <= bytes.size) {
            val size = int(at); if (size < 0 || size > bytes.size - at - 12) return false
            val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
            if (type == "acTL") return false
            if (type == "IEND") return size == 0 && at + 12 == bytes.size
            at += size + 12
        }
        return false
    }
    fun extract(input: InputStream, destination: File, expected: Map<String, String>, cancelled: () -> Boolean = { false }) {
        val seen = HashSet<String>(); var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                check(!cancelled()) { "Cancelled" }; val entry = zip.nextEntry ?: break
                val name = entry.name
                require(name in expected && seen.add(name) && !entry.isDirectory) { "Unexpected archive entry" }
                require(!name.startsWith('/') && name.split('/').all { it != ".." && it != "." && it.isNotEmpty() })
                val bytes = readLimited(zip, MAX_FILE); total += bytes.size; require(total <= MAX_EXPANDED)
                expected[name]?.takeIf(String::isNotEmpty)?.let { require(digest(bytes) == it) { "Image checksum mismatch" } }
                if (name.endsWith(".png")) require(validPng(bytes)) { "Invalid sticker image" }
                val file = File(destination, name); require(file.canonicalPath.startsWith(destination.canonicalPath + File.separator))
                file.parentFile!!.mkdirs(); file.writeBytes(bytes)
            }
        }
        require(seen == expected.keys) { "Incomplete pack" }
    }
}
