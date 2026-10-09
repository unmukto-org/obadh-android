package org.unmukto.obadh.engine

import org.unmukto.obadh.emoji.BanglaEmojiSuggesting

data class KeyboardSuggestion(val text: String, val source: Source) {
    enum class Source { DETERMINISTIC, AUTOCORRECT, AUTOSUGGEST }
}

/**
 * Keeps the raw Roman keys and the suggestion ribbon for the word being typed.
 * The deterministic transliteration is synchronous; the expensive autocorrect
 * result merges later, guarded by [generation] so stale results are dropped.
 * New capabilities plug in here rather than into key handling.
 */
class KeyboardComposer(
    private val engine: BanglaTypingEngine,
    var compositionSuggestionLimit: Int = 3,
    /** Set once the emoji index is mapped; null until then (typing never waits for it). */
    var emojiSuggester: BanglaEmojiSuggesting? = null,
) {
    var romanBuffer = ""
        private set
    private var compositionSuggestions: List<KeyboardSuggestion> = emptyList()

    /** Bumped on every buffer change so stale async results can be discarded. */
    var generation = 0
        private set
    var autocorrectTarget: String? = null
        private set
    var exactLoanwordTarget: String? = null
        private set
    private var correctionsResolved = false

    /**
     * Up to 3 exact-match emoji for the composed word, best first. They take over the
     * ribbon's third slot and are kept apart from the text candidates so the top two
     * always survive. A binary search on a mapped index: safe on the keystroke path.
     */
    var activeEmojis: List<String> = emptyList()
        private set

    val hasActiveInput: Boolean get() = romanBuffer.isNotEmpty()

    /** Canonical engine input; romanBuffer stays raw so qq precedence and deletion are reversible. */
    val engineInput: String get() = engineInput(romanBuffer)

    /** One extra so the deterministic entry never crowds out corrections. */
    val autocorrectFetchLimit: Int get() = compositionSuggestionLimit + 1

    val preview: String get() = compositionSuggestions.firstOrNull()?.text ?: ""

    val activeSuggestions: List<KeyboardSuggestion>
        get() {
            val loanword = exactLoanwordTarget ?: return compositionSuggestions
            return mergeSuggestions(
                primary = listOf(KeyboardSuggestion(loanword, KeyboardSuggestion.Source.AUTOCORRECT)) +
                    compositionSuggestions.take(1),
                fallback = compositionSuggestions,
                limit = maxOf(2, compositionSuggestionLimit),
            )
        }

    /** Exact loanwords always offer the literal as a quoted second choice. */
    fun quotedLiteral(isOutOfVocabulary: Boolean): String? =
        if (exactLoanwordTarget != null || isOutOfVocabulary) preview else null

    /** What committing now (space, return, punctuation) inserts. */
    val commitText: String get() = exactLoanwordTarget ?: autocorrectTarget ?: preview

    /**
     * Exact English loanwords are default transliterations regardless of the
     * correction toggle. Everything else goes through the opt-in [AutoInsertGate].
     */
    fun resolveAutocorrectTarget(
        autoInsertEnabled: Boolean,
        baselineFrequency: Long,
        detailedCorrections: List<DetailedCorrection>,
        isProtectedWord: (String) -> Boolean,
    ) {
        autocorrectTarget = null
        exactLoanwordTarget = null
        correctionsResolved = true
        if (!hasActiveInput) return
        val shown = compositionSuggestions.firstOrNull() ?: return
        if (shown.source != KeyboardSuggestion.Source.DETERMINISTIC) return
        val loanword = detailedCorrections.firstOrNull {
            it.source == DetailedCorrection.Source.ENGLISH_LOANWORD_EXACT &&
                it.romanRepairCost == 0 && it.text.isNotEmpty()
        }
        if (loanword != null) {
            if (loanword.text != shown.text) exactLoanwordTarget = loanword.text
            return
        }
        if (!autoInsertEnabled) return
        if (isProtectedWord(shown.text)) return
        val top = detailedCorrections.firstOrNull { it.text != shown.text } ?: return
        if (!AutoInsertGate.shouldAutoInsert(baselineFrequency, top, isProtectedWord(top.text))) return
        // The ribbon must show what space will insert.
        if (compositionSuggestions.none { it.text == top.text }) return
        autocorrectTarget = top.text
    }

    fun append(key: String) {
        romanBuffer += key
        refreshDeterministic()
    }

    fun deleteBackward(): Boolean {
        if (!hasActiveInput) return false
        val remove = RomanInputRules.deleteCount(romanBuffer)
        romanBuffer = romanBuffer.dropLast(remove)
        refreshDeterministic()
        return true
    }

    fun commitActiveInput(): String? {
        if (!hasActiveInput) return null
        // A rapid delimiter can beat the async ribbon query; resolve once here so
        // exact loanwords do not depend on typing speed.
        if (!correctionsResolved) {
            resolveAutocorrectTarget(
                autoInsertEnabled = false,
                baselineFrequency = 0,
                detailedCorrections = engine.detailedCorrections(engineInput, autocorrectFetchLimit),
                isProtectedWord = { false },
            )
        }
        val committed = commitText
        clear()
        return committed
    }

    fun clear() {
        romanBuffer = ""
        compositionSuggestions = emptyList()
        activeEmojis = emptyList()
        autocorrectTarget = null
        exactLoanwordTarget = null
        correctionsResolved = false
        generation++
    }

    /** Synchronous, cheap: deterministic transliteration only. */
    private fun refreshDeterministic() {
        generation++
        autocorrectTarget = null
        exactLoanwordTarget = null
        correctionsResolved = false
        if (!hasActiveInput) {
            compositionSuggestions = emptyList()
            activeEmojis = emptyList()
            return
        }
        val deterministic = engine.transliterate(engineInput)
        compositionSuggestions = if (deterministic.isEmpty()) emptyList()
        else listOf(KeyboardSuggestion(deterministic, KeyboardSuggestion.Source.DETERMINISTIC))
        activeEmojis = if (deterministic.isEmpty()) emptyList() else emojiSuggester?.emojis(deterministic).orEmpty()
    }

    /** Merge async autocorrect candidates behind the deterministic preview; ignored if stale. */
    fun mergeAutocorrectCandidates(candidates: List<String>, generation: Int) {
        if (generation != this.generation || !hasActiveInput) return
        val merged = ArrayList<KeyboardSuggestion>(compositionSuggestionLimit)
        val seen = HashSet<String>()
        compositionSuggestions.firstOrNull()?.takeIf { it.source == KeyboardSuggestion.Source.DETERMINISTIC }?.let {
            merged += it; seen += it.text
        }
        for (text in candidates) {
            if (text.isEmpty() || !seen.add(text)) continue
            merged += KeyboardSuggestion(text, KeyboardSuggestion.Source.AUTOCORRECT)
            if (merged.size == compositionSuggestionLimit) break
        }
        if (merged.isNotEmpty()) compositionSuggestions = merged
    }

    companion object {
        /**
         * Only t/T + a single q is the khanda-ta shortcut (`tq` -> t``). A following
         * qq belongs to the engine's chandrabindu rule: `tqq` stays `tqq`.
         */
        fun engineInput(input: String): String = RomanInputRules.engineInput(input)

        fun mergeSuggestions(
            primary: List<KeyboardSuggestion>,
            fallback: List<KeyboardSuggestion>,
            limit: Int,
        ): List<KeyboardSuggestion> {
            if (limit <= 0) return emptyList()
            val merged = ArrayList<KeyboardSuggestion>(limit)
            val seen = HashSet<String>()
            for (s in primary + fallback) {
                if (s.text.isEmpty() || !seen.add(s.text)) continue
                merged += s
                if (merged.size == limit) break
            }
            return merged
        }
    }
}
