package org.unmukto.obadh.engine

/** What the composer needs from the engine. Fakeable in off-device tests. */
interface BanglaTypingEngine {
    fun transliterate(input: String): String
    fun detailedCorrections(romanInput: String, limit: Int): List<DetailedCorrection> = emptyList()
    fun compositionSuggestions(romanInput: String, limit: Int): List<String>
    fun autosuggestSuggestions(context: String, limit: Int): List<String>
}

/** Content of the shipped artifact, via the engine's fingerprint accessors. */
data class ModelConfiguration(val autocorrectAvailable: Boolean, val autosuggestAvailable: Boolean)
