package org.unmukto.obadh.swipe

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class SwipeLibraryVerifierTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "verified executable fixture".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun verifiedBytesSurviveExactly() {
        val file = folder.root.resolve("library.tmp")
        SwipeLibraryVerifier.copyVerified(ByteArrayInputStream(bytes), file, hash)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun corruptedDownloadIsRemoved() {
        val file = folder.root.resolve("library.tmp")
        assertThrows(IllegalStateException::class.java) {
            SwipeLibraryVerifier.copyVerified(ByteArrayInputStream(bytes + 0), file, hash)
        }
        assertFalse(file.exists())
    }

    @Test fun oversizedDownloadStopsBeforeInstallation() {
        val file = folder.root.resolve("library.tmp")
        assertThrows(IllegalStateException::class.java) {
            SwipeLibraryVerifier.copyVerified(ByteArrayInputStream(bytes), file, hash, maxBytes = 8)
        }
        assertFalse(file.exists())
    }

    @Test fun interruptedCopyCannotLeaveExecutablePartialFile() {
        val file = folder.root.resolve("library.tmp")
        val source = object : InputStream() {
            override fun read(): Int = throw IOException("connection lost")
        }
        assertThrows(IOException::class.java) { SwipeLibraryVerifier.copyVerified(source, file, hash) }
        assertFalse(file.exists())
    }

    @Test fun cancellationRemovesPartialFile() {
        val file = folder.root.resolve("library.tmp")
        assertThrows(InterruptedException::class.java) {
            SwipeLibraryVerifier.copyVerified(ByteArrayInputStream(bytes), file, hash) { throw InterruptedException() }
        }
        assertFalse(file.exists())
    }
}
