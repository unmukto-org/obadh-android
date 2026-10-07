package org.unmukto.obadh.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.unmukto.obadh.emoji.*
import org.unmukto.obadh.engine.*
import org.unmukto.obadh.settings.*
import java.util.concurrent.Executors

/**
 * The keyboard. Everything real happens here; the containing app only does setup
 * and preferences. Engine owns mechanism, this class owns touch, text mutation,
 * haptics and policy.
 */
class ObadhInputMethodService : InputMethodService(), KeyboardViewListener {
    private val main = Handler(Looper.getMainLooper())
    /** One worker: autocorrect queries are serialized and stale ones dropped by generation. */
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "obadh-engine").apply { isDaemon = true } }

    private lateinit var engine: ObadhBridgeClient
    private lateinit var prefs: KeyboardPreferences
    private lateinit var learned: LearnedWordStore
    private lateinit var personal: PersonalAutosuggestStore
    private lateinit var composer: KeyboardComposer
    private val composition = TextCompositionController()

    private lateinit var keyboardView: KeyboardView
    private lateinit var suggestionBar: SuggestionBarView
    private lateinit var emojiPanel: EmojiPanelView
    @Volatile private var emojiCatalog: EmojiDataStore? = null
    private var emojiPanelOpen = false
    private var emojiSearchActive = false
    private var emojiSearchBangla = false
    private var emojiSearchQuery = ""
    private var emojiPanelFullHeight = 0
    private var emojiSearchGeneration = 0
    @Volatile private var banglaEmojiSearch: BanglaEmojiSearchStore? = null

    private lateinit var emojiRecents: EmojiRecentStore
    private lateinit var emojiVariants: EmojiVariantPreferenceStore

    @Volatile private var modelsReady = false

    /** Emoji for the word just committed, kept on the ribbon after a space until the next letter. */
    private var carriedEmojis: List<String> = emptyList()
    private var lastSpaceAt = 0L
    private var lastShiftTapAt = 0L
    private var spaceTapsInARow = 0

    // The one place that talks to the host. Wraps the CURRENT InputConnection.
    private val document = object : TextDocumentEditing {
        override val contextBeforeInput: String?
            get() = currentInputConnection?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString()

        override fun insertText(text: String) {
            currentInputConnection?.commitText(text, 1)
        }

        override fun deleteBackward() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        override fun deleteBeforeCursor(charCount: Int) {
            if (charCount > 0) currentInputConnection?.deleteSurroundingText(charCount, 0)
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = ObadhBridgeClient()
        prefs = KeyboardPreferences(this)
        learned = LearnedWordStore(this)
        personal = PersonalAutosuggestStore(this)
        composer = KeyboardComposer(engine)
        PrefsEmojiKeyValueStore(this).let {
            emojiRecents = EmojiRecentStore(it)
            emojiVariants = EmojiVariantPreferenceStore(it)
        }
        // Models are copied to app storage then opened off the main thread; until
        // ready the keyboard still types, using the deterministic engine only.
        worker.execute {
            val dir = ModelInstaller.ensureInstalled(this)
            engine.configureModels(dir)
            composer.emojiSuggester = EmojiModels.suggestionStore(dir)
            personal.restore(engine)
            modelsReady = true
        }
    }

    /** No extract/fullscreen editing UI: a fullscreen IME window is laid out against the whole display. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).also { it.listener = this }
        emojiPanel = EmojiPanelView(this).also { it.listener = emojiPanelListener; it.visibility = View.GONE }
        suggestionBar = SuggestionBarView(this).also {
            it.onSelect = ::onSuggestionSelected
            it.onSelectEmoji = ::onEmojiSelected
        }
        // The system paints the navigation-bar strip under the keyboard; match it to the keys
        // instead of leaving the default black.
        window?.window?.navigationBarColor = keyboardView.theme.background
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(keyboardView.theme.background)
            // The panel sits ABOVE the keys: in search mode it is a short field + results row
            // and the letter keyboard stays below it.
            addView(emojiPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
            addView(suggestionBar)
            addView(keyboardView)
            // On Android 15+ the IME window is edge-to-edge and the system draws its
            // hide/switch buttons in the navigation-bar strip: keep keys clear of it.
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                v.setPadding(0, 0, 0, nav.bottom)
                insets
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        closeEmojiPanel()
        resetComposition()
        keyboardView.mode = KeyboardMode.LETTERS
        keyboardView.includesGlobeKey = true
        keyboardView.shiftActive = false
        engine.clearAutosuggestSession()
        refreshRibbon()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        closeEmojiPanel()
        // The word is already real text in the field, so nothing to flush: just stop tracking.
        resetComposition()
        worker.execute { if (modelsReady) personal.save(engine) }
        super.onFinishInputView(finishingInput)
    }

    /** Cursor moved by something other than us: stop tracking the word. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!composer.hasActiveInput) {
            refreshRibbon()
            return
        }
        val before = document.contextBeforeInput ?: ""
        if (newSelStart != newSelEnd || !before.endsWith(composition.composedText)) resetComposition()
        refreshRibbon()
    }

    // ------------------------------------------------------------------ keys

    override fun onKey(key: Key) {
        haptic()
        if (emojiSearchActive) { onSearchKey(key); return }
        when (key) {
            is Key.Character -> typeLetter(key.value)
            is Key.Symbol -> typeSymbol(key)
            Key.Space -> typeSpace()
            Key.Return -> typeReturn()
            Key.Shift -> toggleShift()
            is Key.ModeSwitch -> { commitActiveWord(); keyboardView.mode = key.target }
            Key.Globe -> { commitActiveWord(); showKeyboardPicker() }
            Key.Emoji -> openEmojiPanel()
            Key.Backspace -> Unit // handled via onBackspace
        }
    }

    private fun typeLetter(letter: String) {
        // The next letter starts a new word, so the previous word's emoji stop being offered.
        carriedEmojis = emptyList()
        val cased = if (keyboardView.shiftActive || keyboardView.capsLock) letter.uppercase() else letter
        composer.append(cased)
        render()
        if (keyboardView.shiftActive && !keyboardView.capsLock) keyboardView.shiftActive = false
        requestCorrections()
    }

    private fun render() {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try { composition.setComposition(composer.preview, document) } finally { ic.endBatchEdit() }
        refreshRibbon()
    }

    private fun typeSpace() {
        val now = System.currentTimeMillis()
        if (composer.hasActiveInput) {
            commitActiveWord(trailing = " ")
            lastSpaceAt = now
            return
        }
        // Quick double-space -> "। " (time-gated, after a word, replaces the first space).
        val before = document.contextBeforeInput ?: ""
        if (now - lastSpaceAt <= DOUBLE_SPACE_MS && atEndOfText()) {
            SmartPunctuation.doubleSpaceSubstitution(before)?.let {
                replaceBeforeCaret(it)
                lastSpaceAt = 0
                refreshRibbon()
                return
            }
        }
        composition.insertSpace(document)
        lastSpaceAt = now
        refreshRibbon()
    }

    private fun typeReturn() {
        commitActiveWord()
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        engine.clearAutosuggestSession()
        refreshRibbon()
    }

    private fun typeSymbol(key: Key.Symbol) {
        // Punctuation commits the word first (exact loanwords/auto-insert still apply).
        commitActiveWord()
        val result = SmartPunctuation.literalSubstitution(key.output, document.contextBeforeInput ?: "")
        replaceBeforeCaret(result)
        if (key.terminator) engine.clearAutosuggestSession()
        refreshRibbon()
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        if (keyboardView.capsLock) {
            keyboardView.capsLock = false; keyboardView.shiftActive = false
        } else if (keyboardView.shiftActive && now - lastShiftTapAt < 400) {
            keyboardView.capsLock = true
        } else {
            keyboardView.shiftActive = !keyboardView.shiftActive
        }
        lastShiftTapAt = now
    }

    override fun onBackspace(unit: BackspaceDeletionUnit) {
        if (emojiSearchActive) { searchBackspace(); return }
        if (unit == BackspaceDeletionUnit.CHARACTER) haptic()
        if (composer.hasActiveInput && unit == BackspaceDeletionUnit.CHARACTER) {
            composer.deleteBackward()
            render()
            requestCorrections()
            return
        }
        if (composer.hasActiveInput) {
            // Word-unit hold mid-compose: drop the whole word being typed.
            composition.clearComposition(document)
            composer.clear()
            refreshRibbon()
            return
        }
        val context = document.contextBeforeInput ?: ""
        if (context.isEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        if (unit == BackspaceDeletionUnit.CHARACTER) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            val n = BackspaceDeletionPlanner.deleteCount(context, unit)
            val chars = context.offsetByCodePoints(context.length, -n.coerceAtMost(context.codePointCount(0, context.length)))
            currentInputConnection?.deleteSurroundingText(context.length - chars, 0)
        }
        refreshRibbon()
    }

    // ------------------------------------------------------------- commits

    /** Finish the word: apply any gated correction, then drop tracking. */
    private fun commitActiveWord(trailing: String = "") {
        val ic = currentInputConnection
        if (!composer.hasActiveInput) {
            if (trailing.isNotEmpty()) document.insertText(trailing)
            return
        }
        // Captured BEFORE the commit clears the composer, so a space does not snatch away
        // the one suggestion the user was reaching for.
        carriedEmojis = composer.activeEmojis
        val committed = composer.commitActiveInput().orEmpty()
        ic?.beginBatchEdit()
        try { composition.commit(committed, trailing, document) } finally { ic?.endBatchEdit() }
        if (committed.isNotEmpty()) worker.execute { engine.commitAutosuggestToken(committed) }
        refreshRibbon()
    }

    private fun onSuggestionSelected(item: SuggestionBarView.Item) {
        haptic()
        if (composer.hasActiveInput) {
            // The quoted literal is the user's own spelling: protect it forever.
            if (item.quoted) learned.protect(item.text)
            val text = item.text
            composer.clear()
            composition.commitSuggestion(text, document)
            document.insertText(" ")
            worker.execute { engine.commitAutosuggestToken(text) }
        } else {
            composition.commitNextWordSuggestion(item.text, document)
            worker.execute { engine.commitAutosuggestToken(item.text) }
        }
        refreshRibbon()
    }

    /**
     * Tapping an inline emoji REPLACES the word being composed: the typed text was the
     * emoji's query ("bhalobasha" + tap gives the emoji, not the word plus the emoji), so
     * the discarded query is not committed to autosuggest learning.
     */
    private fun onEmojiSelected(slot: SuggestionBarView.EmojiSlot) {
        haptic()
        if (composer.hasActiveInput) {
            composition.clearComposition(document)
            composer.clear()
        }
        document.insertText(slot.display)
        carriedEmojis = emptyList()
        emojiRecents.record(slot.display)
        refreshRibbon()
    }

    /**
     * The globe never cycles blindly to the next keyboard: it opens the system picker so the
     * user chooses which one they want.
     */
    private fun showKeyboardPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ------------------------------------------------------------ emoji panel

    private val emojiPanelListener = object : EmojiPanelListener {
        override fun onEmojiSelected(emoji: String, base: String) {
            haptic()
            document.insertText(emoji)
            emojiRecents.record(emoji)
            emojiPanel.recordRecent(emoji)
        }

        override fun onReturnToKeyboard() = closeEmojiPanel()

        override fun onBackspace(unit: BackspaceDeletionUnit) = this@ObadhInputMethodService.onBackspace(unit)

        override fun onVariantPicked(base: String, selected: String) {
            emojiVariants.record(base, selected)
            emojiPanel.updatePreference(base, selected)
        }

        override fun onSearchRequested() = enterEmojiSearch()
        override fun onSearchClosed() = exitEmojiSearch()
        override fun onSearchCleared() { emojiSearchQuery = ""; syncEmojiSearch() }
        override fun onSearchLanguageToggled() { emojiSearchBangla = !emojiSearchBangla; syncEmojiSearch() }
    }

    private fun openEmojiPanel() {
        commitActiveWord()
        val height = suggestionBar.height + keyboardView.height
        emojiPanelFullHeight = height
        suggestionBar.visibility = View.GONE
        keyboardView.visibility = View.GONE
        emojiPanel.layoutParams = emojiPanel.layoutParams.also { it.height = height }
        emojiPanel.visibility = View.VISIBLE
        emojiPanelOpen = true
        val cached = emojiCatalog
        if (cached != null) {
            configureEmojiPanel(cached)
        } else {
            // ~1 MB catalog: decoded off the main thread, only when the panel first opens.
            worker.execute {
                val loaded = EmojiModels.dataStore(ModelInstaller.modelsDir(this))
                emojiCatalog = loaded
                main.post { if (emojiPanelOpen) configureEmojiPanel(loaded) }
            }
        }
    }

    private fun configureEmojiPanel(catalog: EmojiDataStore) =
        emojiPanel.configure(catalog, emojiRecents.load(), emojiVariants.load())

    private fun closeEmojiPanel() {
        if (!::emojiPanel.isInitialized || !emojiPanelOpen) return
        emojiPanelOpen = false
        emojiSearchActive = false
        emojiSearchQuery = ""
        emojiPanel.setSearchActive(false)
        emojiPanel.visibility = View.GONE
        suggestionBar.visibility = View.VISIBLE
        keyboardView.visibility = View.VISIBLE
        refreshRibbon()
    }

    // ----------------------------------------------------------- emoji search

    private fun enterEmojiSearch() {
        if (!emojiPanelOpen) return
        emojiSearchActive = true
        emojiSearchQuery = ""
        emojiSearchBangla = prefs.emojiSearchBangla
        // The panel shrinks to a field + one row of results; the letters sit below it.
        emojiPanel.layoutParams = emojiPanel.layoutParams.also { it.height = (EMOJI_SEARCH_PANEL_DP * resources.displayMetrics.density).toInt() }
        emojiPanel.setSearchActive(true)
        keyboardView.mode = KeyboardMode.LETTERS
        keyboardView.shiftActive = false
        keyboardView.visibility = View.VISIBLE
        suggestionBar.visibility = View.GONE
        syncEmojiSearch()
    }

    private fun exitEmojiSearch() {
        emojiSearchActive = false
        emojiSearchQuery = ""
        emojiSearchGeneration++
        emojiPanel.setSearchActive(false)
        keyboardView.visibility = View.GONE
        emojiPanel.layoutParams = emojiPanel.layoutParams.also { it.height = emojiPanelFullHeight }
        if (emojiCatalog != null) configureEmojiPanel(emojiCatalog!!)
        emojiPanel.requestLayout()
    }

    private fun onSearchKey(key: Key) {
        val cased = { v: String -> if (keyboardView.shiftActive || keyboardView.capsLock) v.uppercase() else v }
        when (key) {
            is Key.Character -> {
                emojiSearchQuery += cased(key.value)
                if (keyboardView.shiftActive && !keyboardView.capsLock) keyboardView.shiftActive = false
                syncEmojiSearch()
            }
            is Key.Symbol -> { emojiSearchQuery += key.output; syncEmojiSearch() }
            Key.Space -> if (!emojiSearchQuery.lastOrNull().let { it == null || it.isWhitespace() }) {
                emojiSearchQuery += " "; syncEmojiSearch()
            }
            Key.Shift -> toggleShift()
            is Key.ModeSwitch -> keyboardView.mode = key.target
            Key.Return, Key.Emoji, Key.Globe -> exitEmojiSearch()
            Key.Backspace -> Unit // via onBackspace
        }
    }

    private fun searchBackspace() {
        if (emojiSearchQuery.isEmpty()) { exitEmojiSearch(); return }
        emojiSearchQuery = emojiSearchQuery.dropLast(1)
        syncEmojiSearch()
    }

    /** What the field shows: in Bangla, the typed Roman is transliterated into the query. */
    private fun displayQuery(): String =
        if (emojiSearchBangla) engine.transliterate(KeyboardComposer.engineInput(emojiSearchQuery)) else emojiSearchQuery

    private fun syncEmojiSearch() {
        val shown = displayQuery()
        emojiPanel.setSearchState(shown, emojiSearchBangla)
        val generation = ++emojiSearchGeneration
        val bangla = emojiSearchBangla
        val query = shown
        val catalog = emojiCatalog
        if (query.isBlank()) { emojiPanel.setSearchResults(emptyList()); return }
        worker.execute {
            val results: List<String> = if (bangla) {
                // Loaded only when Bangla search is first used, so English pays nothing.
                val store = banglaEmojiSearch ?: EmojiModels.banglaSearchStore(ModelInstaller.modelsDir(this))
                    .also { banglaEmojiSearch = it }
                store.search(query, EMOJI_SEARCH_LIMIT)
            } else {
                (catalog ?: EmojiModels.dataStore(ModelInstaller.modelsDir(this)).also { emojiCatalog = it })
                    .search(query, EMOJI_SEARCH_LIMIT).map { it.emoji }
            }
            main.post { if (generation == emojiSearchGeneration && emojiSearchActive) emojiPanel.setSearchResults(results) }
        }
    }

    // --------------------------------------------------------------- ribbon

    /** Async autocorrect for the current word, generation-guarded against out-of-order results. */
    private fun requestCorrections() {
        if (!modelsReady || !composer.hasActiveInput) return
        val generation = composer.generation
        val input = composer.engineInput
        val limit = composer.autocorrectFetchLimit
        worker.execute {
            val candidates = engine.compositionSuggestions(input, limit)
            val detailed = engine.detailedCorrections(input, limit)
            val baseline = engine.wordFrequency(composer.preview)
            main.post {
                if (generation != composer.generation) return@post
                composer.mergeAutocorrectCandidates(candidates, generation)
                composer.resolveAutocorrectTarget(
                    autoInsertEnabled = prefs.autoInsertCorrections,
                    baselineFrequency = baseline,
                    detailedCorrections = detailed,
                    isProtectedWord = learned::isProtected,
                )
                refreshRibbon()
            }
        }
    }

    private fun refreshRibbon() {
        if (!::suggestionBar.isInitialized) return
        if (composer.hasActiveInput) {
            val shown = composer.activeSuggestions
            val oov = modelsReady && composer.preview.isNotEmpty() && !engineIsWordCached(composer.preview)
            val quoted = composer.quotedLiteral(isOutOfVocabulary = oov)
            val items = shown.mapIndexed { i, s ->
                val isLiteral = quoted != null && s.text == quoted && s.source == KeyboardSuggestion.Source.DETERMINISTIC
                SuggestionBarView.Item(s.text, quoted = isLiteral, highlighted = s.text == composer.commitText && i == 0)
            }
            suggestionBar.items = items
            suggestionBar.emojis = emojiSlots(composer.activeEmojis)
        } else {
            suggestionBar.emojis = emojiSlots(carriedEmojis)
            val context = (document.contextBeforeInput ?: "").takeLast(NEXT_WORD_CONTEXT_CHARS)
            if (!modelsReady || context.isBlank() || context.last().isWhitespace().not()) {
                suggestionBar.items = emptyList()
                return
            }
            worker.execute {
                val next = engine.autosuggestSessionSuggestions(3).ifEmpty { engine.autosuggestSuggestions(context, 3) }
                main.post { if (!composer.hasActiveInput) suggestionBar.items = next.map { SuggestionBarView.Item(it) } }
            }
        }
    }

    /** Resolve each base emoji to the user's remembered skin tone: a map lookup, no catalog load. */
    private fun emojiSlots(bases: List<String>): List<SuggestionBarView.EmojiSlot> {
        if (bases.isEmpty()) return emptyList()
        val prefs = emojiVariants.load()
        return bases.map { SuggestionBarView.EmojiSlot(it, prefs[it] ?: it) }
    }

    // Presence check on the main thread would cross the JNI per keystroke; cache the last answer.
    private var lastOovWord = ""
    private var lastOovIsWord = true
    private fun engineIsWordCached(word: String): Boolean {
        if (word == lastOovWord) return lastOovIsWord
        lastOovWord = word
        lastOovIsWord = engine.isLexiconWord(word)
        return lastOovIsWord
    }

    // -------------------------------------------------------------- helpers

    private fun resetComposition() {
        carriedEmojis = emptyList()
        composer.clear()
        composition.resetHostState()
    }

    private fun atEndOfText(): Boolean {
        val after = currentInputConnection?.getTextAfterCursor(1, 0)
        return after.isNullOrEmpty()
    }

    private fun replaceBeforeCaret(result: SmartPunctuationResult) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            if (result.deleteBefore > 0) ic.deleteSurroundingText(result.deleteBefore, 0)
            ic.commitText(result.insertion, 1)
        } finally { ic.endBatchEdit() }
    }

    private fun haptic() {
        if (!prefs.hapticsEnabled) return
        keyboardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val CONTEXT_CHARS = 128
        const val NEXT_WORD_CONTEXT_CHARS = 200
        const val DOUBLE_SPACE_MS = 350L
        const val EMOJI_SEARCH_LIMIT = 40
        const val EMOJI_SEARCH_PANEL_DP = 104
    }
}
