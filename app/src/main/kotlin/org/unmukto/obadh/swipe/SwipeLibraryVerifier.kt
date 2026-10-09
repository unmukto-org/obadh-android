package org.unmukto.obadh.swipe

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Bounded, streaming verification; never expose a partially written executable file. */
internal object SwipeLibraryVerifier {
    fun copyVerified(
        source: InputStream,
        temporary: File,
        expectedSha256: String,
        maxBytes: Long = 64L * 1024 * 1024,
        checkCancelled: () -> Unit = {},
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            check(!temporary.exists() || temporary.delete()) { "Couldn't prepare download storage. Try again." }
            temporary.outputStream().use { target ->
                // Android 14+ requires downloaded executable code to be read-only before writing.
                check(temporary.setReadOnly()) { "Couldn't secure the downloaded file. Try again." }
                val buffer = ByteArray(32768)
                var size = 0L
                while (true) {
                    checkCancelled()
                    val count = source.read(buffer)
                    if (count < 0) break
                    size += count
                    check(size <= maxBytes) { "Download is larger than expected. Please retry." }
                    digest.update(buffer, 0, count)
                    target.write(buffer, 0, count)
                }
                target.fd.sync()
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == expectedSha256) { "Downloaded file could not be verified. Please retry." }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }
}
