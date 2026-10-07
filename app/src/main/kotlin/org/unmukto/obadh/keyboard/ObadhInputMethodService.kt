package org.unmukto.obadh.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private var modelsReady = false
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
        // Models are copied to app storage then opened off the main thread; until
        // ready the keyboard still types, using the deterministic engine only.
        worker.execute {
            val dir = ModelInstaller.ensureInstalled(this)
            engine.configureModels(dir)
            personal.restore(engine)
            modelsReady = true
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).also { it.listener = this }
        suggestionBar = SuggestionBarView(this).also { it.onSelect = ::onSuggestionSelected }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(keyboardView.theme.background)
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
        resetComposition()
        keyboardView.mode = KeyboardMode.LETTERS
        keyboardView.includesGlobeKey = true
        keyboardView.shiftActive = false
        engine.clearAutosuggestSession()
        refreshRibbon()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
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
        when (key) {
            is Key.Character -> typeLetter(key.value)
            is Key.Symbol -> typeSymbol(key)
            Key.Space -> typeSpace()
            Key.Return -> typeReturn()
            Key.Shift -> toggleShift()
            is Key.ModeSwitch -> { commitActiveWord(); keyboardView.mode = key.target }
            Key.Globe -> { commitActiveWord(); switchToNextInputMethod(false) }
            Key.Backspace -> Unit // handled via onBackspace
        }
    }

    private fun typeLetter(letter: String) {
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
        } else {
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
    }
}
