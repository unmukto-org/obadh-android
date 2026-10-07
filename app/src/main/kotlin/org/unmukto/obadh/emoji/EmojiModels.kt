package org.unmukto.obadh.emoji

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Opens the emoji artifacts from the installed models directory, memory-mapped. */
object EmojiModels {
    private fun map(file: File): ByteBuffer? {
        if (!file.exists()) return null
        return runCatching {
            RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
        }.getOrNull()
    }

    /** Small index, hit on the typing path: load at startup. */
    fun suggestionStore(modelsDir: File): BanglaEmojiSuggestionStore =
        map(File(modelsDir, "emoji/emoji-bn.bin"))?.let(BanglaEmojiSuggestionStore::decode)
            ?: BanglaEmojiSuggestionStore.EMPTY

    /** ~1 MB catalog: load lazily, off the main thread, when the panel first opens. */
    fun dataStore(modelsDir: File): EmojiDataStore =
        map(File(modelsDir, "emoji/emoji.bin"))?.let(EmojiDataStore::decode) ?: EmojiDataStore.EMPTY

    /** Loaded only when emoji search is switched to Bangla. */
    fun banglaSearchStore(modelsDir: File): BanglaEmojiSearchStore =
        map(File(modelsDir, "emoji/emoji-bn-search.bin"))?.let(BanglaEmojiSearchStore::decode)
            ?: BanglaEmojiSearchStore.EMPTY
}
