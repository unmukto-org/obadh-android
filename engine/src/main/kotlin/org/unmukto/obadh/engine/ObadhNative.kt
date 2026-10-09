package org.unmukto.obadh.engine

/**
 * Raw JNI surface over the engine's C ABI (v2). Mirrors obadh.h one to one; all
 * strings are UTF-8 byte arrays and packed lists come back as raw bytes.
 * Parsing and locking live in [ObadhBridgeClient].
 */
internal object ObadhNative {
    init {
        System.loadLibrary("obadh_jni")
    }

    external fun abiVersion(): Int
    external fun engineVersion(): ByteArray

    external fun engineNew(): Long
    external fun engineFree(handle: Long)
    external fun transliterate(handle: Long, roman: ByteArray): ByteArray

    external fun autocorrectOpen(fstPath: ByteArray, loanwordPath: ByteArray): Long
    external fun autocorrectFree(handle: Long)
    external fun autocorrectFingerprint(handle: Long): Long
    external fun autocorrectWordFrequency(handle: Long, word: ByteArray): Long
    external fun autocorrectSuggestDetailed(handle: Long, roman: ByteArray, limit: Int): ByteArray
    external fun composeSuggestions(handle: Long, roman: ByteArray, limit: Int): ByteArray
    external fun autocorrectWordAlternatives(handle: Long, word: ByteArray, limit: Int): ByteArray

    external fun autosuggestOpen(path: ByteArray): Long
    external fun autosuggestFree(handle: Long)
    external fun autosuggestFingerprint(handle: Long): Long
    external fun autosuggestCommit(handle: Long, token: ByteArray): Int
    external fun autosuggestSuggest(handle: Long, limit: Int): ByteArray
    external fun autosuggestSuggestForContext(handle: Long, context: ByteArray, limit: Int): ByteArray
    external fun autosuggestClearSession(handle: Long)
    external fun autosuggestClearPersonal(handle: Long)
    external fun autosuggestExportPersonal(handle: Long): ByteArray
    external fun autosuggestImportPersonal(handle: Long, snapshot: ByteArray): Int
}
