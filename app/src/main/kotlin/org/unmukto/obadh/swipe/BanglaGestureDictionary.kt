// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.swipe

import android.content.Context
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.obadh.ObadhSwipeCompatibility
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import org.json.JSONObject
import org.unmukto.obadh.BuildConfig
import org.unmukto.obadh.keyboard.NativeObadhFeatures
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Gesture-only native model. Tap correction and personalization stay in the engine C ABI. */
class BanglaGestureDictionary private constructor(
    private val native: ReadOnlyBinaryDictionary, locale: Locale,
) : Dictionary(TYPE_MAIN, locale) {
    @Volatile private var emitted: Set<String> = emptySet()

    override fun getSuggestions(
        data: ComposedData, context: NgramContext, proximity: Long,
        settings: SettingsValuesForSuggestion, session: Int, weight: Float, ratio: FloatArray?,
    ): ArrayList<SuggestedWordInfo>? {
        if (!data.mIsBatchMode || !NativeObadhFeatures.banglaGestureAllowed(Settings.getValues())) return null
        val start = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        val results = native.getSuggestions(data, context, proximity, settings, session, weight, ratio) ?: return null
        val seen = HashSet<String>()
        val mapped = ArrayList<SuggestedWordInfo>()
        fun add(word: String, source: SuggestedWordInfo, rank: Int) {
            // Never expose the private Roman search keys, even with a different decoder.
            if (word.isEmpty() || word.any { it !in '\u0980'..'\u09ff' && it != '\u200c' && it != '\u200d' } || !seen.add(word)) return
            mapped.add(SuggestedWordInfo(word, "", source.mScore - rank, SuggestedWordInfo.KIND_CORRECTION,
                this, source.mIndexOfTouchPointOfSecondWord, source.mAutoCommitFirstWordConfidence))
        }
        for (result in results.sortedByDescending { it.mScore }) {
            if (result.mWord.any { it in '\u0980'..'\u09ff' }) add(result.mWord, result, 0)
            else native.getWordProperty(result.mWord.lowercase(Locale.ROOT), false)?.mShortcutTargets
                ?.forEachIndexed { index, target -> add(target.mWord, result, index) }
        }
        // The foundation validates returned main-dictionary words after decoding. It
        // must validate Bengali targets, rather than look them up as Roman geometry.
        emitted = seen
        if (BuildConfig.DEBUG) android.util.Log.i("ObadhGestureTiming",
            "decode_map_us=${(System.nanoTime() - start) / 1000} thread=${Thread.currentThread().name}")
        return mapped
    }

    override fun isInDictionary(word: String) = word in emitted || NativeObadhFeatures.isBanglaLexiconWord(word)
    override fun close() = native.close()

    companion object {
        /** Foundation calls this on its dictionary loader, never on the input thread. */
        @Synchronized fun load(context: Context, locale: Locale): Dictionary? {
            if (locale.language != "bn" || !ObadhSwipeCompatibility.supportsDownloadedLibrary() || !JniUtils.sHaveGestureLib) return null
            // Load independently of the toggle: enabling an already installed decoder
            // must work without restarting the IME or stale cross-process preferences.
            return runCatching {
                val metadata = JSONObject(context.assets.open("ObadhGesture/metadata.json").bufferedReader().use { it.readText() })
                val digest = metadata.getString("sha256")
                check(digest.matches(Regex("[0-9a-f]{64}")))
                val expectedSize = metadata.getLong("bytes")
                check(expectedSize in 1L..4L * 1024 * 1024)
                val directory = File(context.filesDir, "ObadhGesture").apply { check(isDirectory || mkdirs()) }
                val file = File(directory, "${digest.take(16)}.dict")
                fun valid(candidate: File): Boolean {
                    if (!candidate.isFile || candidate.length() != expectedSize) return false
                    val hash = MessageDigest.getInstance("SHA-256")
                    candidate.inputStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            hash.update(buffer, 0, count)
                        }
                    }
                    return hash.digest().joinToString("") { "%02x".format(it) } == digest
                }
                if (!valid(file)) {
                    val temporary = File(directory, "model.tmp")
                    try {
                        context.assets.open("ObadhGesture/main.dict").use { input ->
                            temporary.outputStream().use { input.copyTo(it, 16 * 1024) }
                        }
                        check(valid(temporary) && temporary.renameTo(file)) { "Invalid Bangla gesture model" }
                    } finally { temporary.delete() }
                }
                // US is only the decoder's Latin-key geometry locale. The outer dictionary
                // and all outputs remain Bangla. No English model is used for this search.
                val native = ReadOnlyBinaryDictionary(file.absolutePath, 0, file.length(), false, Locale.US, TYPE_MAIN)
                if (!native.isValidDictionary) { native.close(); file.delete(); error("Unsupported Bangla gesture model") }
                directory.listFiles()?.filter { it != file }?.forEach { it.delete() }
                BanglaGestureDictionary(native, locale)
            }.onFailure { android.util.Log.e("ObadhGesture", "Bangla gesture vocabulary unavailable", it) }.getOrNull()
        }
    }
}
