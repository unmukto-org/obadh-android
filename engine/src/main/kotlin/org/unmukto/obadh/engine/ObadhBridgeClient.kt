package org.unmukto.obadh.engine

import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Kotlin side of the engine bridge. Owns the opaque handles and marshals UTF-8.
 *
 * The ABI forbids using ONE handle from two threads at once, which is a
 * per-handle contract, so each handle has its own lock. Do not collapse them:
 * a keystroke's ~1 us transliterate must never queue behind a 1-5 ms FST
 * traversal on another handle (measured on iOS; the same reasoning applies).
 * No method takes more than one lock, so there is no ordering hazard.
 */
class ObadhBridgeClient : BanglaTypingEngine, AutoCloseable {
    private val engineLock = ReentrantLock()
    private val autocorrectLock = ReentrantLock()
    private val autosuggestLock = ReentrantLock()
    private var engineHandle = 0L
    private var autocorrectHandle = 0L
    private var autosuggestHandle = 0L
    private val committedContext = ArrayDeque<String>()
    private val whitespace = Regex("\\s+")

    /** Cheap startup path, independent of model I/O and suggestion handles. */
    fun initializeTransliteration() {
        check(ObadhNative.abiVersion() == ABI_VERSION) { "Bridge built against a different engine C ABI" }
        engineLock.withLock { if (engineHandle == 0L) engineHandle = ObadhNative.engineNew() }
        check(engineHandle != 0L) { "Obadh engine initialization failed" }
    }

    fun configureModels(modelsDir: File): ModelConfiguration {
        check(ObadhNative.abiVersion() == ABI_VERSION) { "Bridge built against a different engine C ABI" }
        engineLock.withLock { if (engineHandle == 0L) engineHandle = ObadhNative.engineNew() }
        val ac = autocorrectLock.withLock {
            if (autocorrectHandle == 0L) {
                val fst = File(modelsDir, "autocorrect/bn.fst")
                val loan = File(modelsDir, "autocorrect/en_bn_loanwords.fst")
                if (fst.exists() && loan.exists()) {
                    autocorrectHandle = ObadhNative.autocorrectOpen(
                        fst.path.toByteArray(), loan.path.toByteArray(),
                    )
                }
            }
            autocorrectHandle != 0L
        }
        val asg = autosuggestLock.withLock {
            if (autosuggestHandle == 0L) {
                val bin = File(modelsDir, "autosuggest/autosuggest-ngram-c64.bin")
                if (bin.exists()) autosuggestHandle = ObadhNative.autosuggestOpen(bin.path.toByteArray())
            }
            autosuggestHandle != 0L
        }
        return ModelConfiguration(ac, asg)
    }

    fun autocorrectFingerprint(): Long = autocorrectLock.withLock {
        if (autocorrectHandle == 0L) 0L else ObadhNative.autocorrectFingerprint(autocorrectHandle)
    }

    fun autosuggestFingerprint(): Long = autosuggestLock.withLock {
        if (autosuggestHandle == 0L) 0L else ObadhNative.autosuggestFingerprint(autosuggestHandle)
    }

    // Deterministic

    override fun transliterate(input: String): String = engineLock.withLock {
        if (engineHandle == 0L) "" else String(ObadhNative.transliterate(engineHandle, input.toByteArray()), Charsets.UTF_8)
    }

    // Autocorrect

    override fun compositionSuggestions(romanInput: String, limit: Int): List<String> = autocorrectLock.withLock {
        if (autocorrectHandle == 0L) emptyList()
        else PackedRecords.parseStringList(ObadhNative.composeSuggestions(autocorrectHandle, romanInput.toByteArray(), limit.coerceAtLeast(0)))
    }

    override fun detailedCorrections(romanInput: String, limit: Int): List<DetailedCorrection> = autocorrectLock.withLock {
        if (autocorrectHandle == 0L) emptyList()
        else PackedRecords.parseDetailedCorrections(ObadhNative.autocorrectSuggestDetailed(autocorrectHandle, romanInput.toByteArray(), limit.coerceAtLeast(0)))
    }

    /** Lexicon frequency of [word]; 0 if it is not an entry, so presence is `> 0`. */
    fun wordFrequency(word: String): Long = autocorrectLock.withLock {
        if (autocorrectHandle == 0L) 0L else ObadhNative.autocorrectWordFrequency(autocorrectHandle, word.toByteArray())
    }

    fun isLexiconWord(word: String): Boolean = wordFrequency(word) > 0

    /** Re-correction alternatives for an already-committed Bangla word. */
    fun wordAlternatives(banglaWord: String, limit: Int): List<String> = autocorrectLock.withLock {
        if (autocorrectHandle == 0L) emptyList()
        else PackedRecords.parseStringList(ObadhNative.autocorrectWordAlternatives(autocorrectHandle, banglaWord.toByteArray(), limit.coerceAtLeast(0)))
    }

    // Autosuggest

    override fun autosuggestSuggestions(context: String, limit: Int): List<String> = autosuggestSuggestions(context, limit, true)

    /** Session ABI merges learned words. Stateless ABI is model-only and never learns editor text. */
    fun autosuggestSuggestions(context: String, limit: Int, personalized: Boolean): List<String> = autosuggestLock.withLock {
        if (autosuggestHandle == 0L) return@withLock emptyList()
        val tail = context.trim().split(whitespace).filter(String::isNotBlank).takeLast(3)
        val matchesSession = tail.isNotEmpty() && tail == committedContext.toList()
        val bytes = if (personalized && matchesSession)
            ObadhNative.autosuggestSuggest(autosuggestHandle, limit.coerceAtLeast(0))
        else ObadhNative.autosuggestSuggestForContext(autosuggestHandle, context.toByteArray(), limit.coerceAtLeast(0))
        PackedRecords.parseStringList(bytes)
    }

    fun autosuggestSessionSuggestions(limit: Int): List<String> = autosuggestLock.withLock {
        if (autosuggestHandle == 0L) emptyList()
        else PackedRecords.parseStringList(ObadhNative.autosuggestSuggest(autosuggestHandle, limit.coerceAtLeast(0)))
    }

    fun commitAutosuggestToken(token: String): Boolean = autosuggestLock.withLock {
        if (autosuggestHandle == 0L) return@withLock false
        val committed = ObadhNative.autosuggestCommit(autosuggestHandle, token.toByteArray()) == 1
        if (committed) {
            committedContext.addLast(token)
            while (committedContext.size > 3) committedContext.removeFirst()
        }
        committed
    }

    fun clearAutosuggestSession() = autosuggestLock.withLock {
        committedContext.clear()
        if (autosuggestHandle != 0L) ObadhNative.autosuggestClearSession(autosuggestHandle)
    }

    fun clearPersonalAutosuggest() = autosuggestLock.withLock {
        if (autosuggestHandle != 0L) ObadhNative.autosuggestClearPersonal(autosuggestHandle)
    }

    fun exportPersonalAutosuggestSnapshot(): ByteArray? = autosuggestLock.withLock {
        if (autosuggestHandle == 0L) null
        else ObadhNative.autosuggestExportPersonal(autosuggestHandle).takeIf { it.isNotEmpty() }
    }

    fun importPersonalAutosuggestSnapshot(data: ByteArray): Boolean = autosuggestLock.withLock {
        autosuggestHandle != 0L && data.isNotEmpty() && ObadhNative.autosuggestImportPersonal(autosuggestHandle, data) == 1
    }

    override fun close() {
        engineLock.withLock { if (engineHandle != 0L) ObadhNative.engineFree(engineHandle); engineHandle = 0 }
        autocorrectLock.withLock { if (autocorrectHandle != 0L) ObadhNative.autocorrectFree(autocorrectHandle); autocorrectHandle = 0 }
        autosuggestLock.withLock {
            if (autosuggestHandle != 0L) ObadhNative.autosuggestFree(autosuggestHandle)
            autosuggestHandle = 0
            committedContext.clear()
        }
    }

    companion object {
        const val ABI_VERSION = 2
    }
}
