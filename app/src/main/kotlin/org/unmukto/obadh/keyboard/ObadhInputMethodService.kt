package org.unmukto.obadh.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
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
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private lateinit var learned: LearnedWordStore
    private lateinit var personal: PersonalAutosuggestStore
    private lateinit var composer: KeyboardComposer
    private val composition = TextCompositionController()

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardColumn: LinearLayout
    private lateinit var keyboardRoot: FrameLayout
    private lateinit var suggestionBar: SuggestionBarView
    private lateinit var emojiPanel: EmojiPanelView
    @Volatile private var emojiCatalog: EmojiDataStore? = null
    private var emojiPanelOpen = false
    private lateinit var clipboardPanel: ClipboardPanelView
    private lateinit var clipboardHistory: ClipboardHistory
    private lateinit var shortcuts: TextShortcuts
    private var clipboardPanelOpen = false
    private val clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }
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
        clipboardHistory = ClipboardHistory(this)
        shortcuts = TextShortcuts(this)
        getSystemService(android.content.ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
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

    /** A number or phone field on the pad has nothing to suggest, so the ribbon gives its room back. */
    private fun updateChrome() {
        if (!::suggestionBar.isInitialized) return
        val pad = (fieldKind == FieldKind.NUMBER || fieldKind == FieldKind.PHONE) &&
            (keyboardView.mode == KeyboardMode.NUMPAD_EN || keyboardView.mode == KeyboardMode.NUMPAD_BN)
        suggestionBar.visibility = if (pad) View.GONE else View.VISIBLE
    }

    private fun singleLineText(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        return type and android.text.InputType.TYPE_MASK_CLASS == android.text.InputType.TYPE_CLASS_TEXT &&
            type and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0
    }

    /** The return key's icon from the field's action. An app-named action shows its own text. */
    private fun returnIconFor(info: EditorInfo?): ReturnIcon {
        info ?: return ReturnIcon.ENTER
        if (!prefs.returnActionKey) return ReturnIcon.ENTER
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return ReturnIcon.ENTER
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> ReturnIcon.SEARCH
            EditorInfo.IME_ACTION_GO -> ReturnIcon.GO
            EditorInfo.IME_ACTION_SEND -> ReturnIcon.SEND
            EditorInfo.IME_ACTION_NEXT -> ReturnIcon.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> ReturnIcon.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> ReturnIcon.DONE
            else -> if (singleLineText(info)) ReturnIcon.DONE else ReturnIcon.ENTER
        }
    }

    /** Height of the suggestion strip for this device and orientation. */
    private fun applyChrome() {
        if (!::suggestionBar.isInitialized) return
        val landscape = keyboardView.landscape
        suggestionBar.heightDp = when {
            keyboardView.family != null -> 48f
            landscape -> 36f
            else -> 44f
        }
    }

    /**
     * Rotation changes the geometry, not just the width: the family stays (it is a device
     * property) but heights, margins and the bottom row do not. Any open panel is closed first,
     * because its height was measured against the old layout.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::keyboardView.isInitialized) return
        closeEmojiPanel()
        closeClipboardPanel()
        keyboardRoot.minimumHeight = resources.displayMetrics.heightPixels
        keyboardView.refreshForConfiguration()
        applyChrome()
    }

    /** No extract/fullscreen editing UI: a fullscreen IME window is laid out against the whole display. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Volume up / down move the caret one character forward / back while the keys are showing. */
    private fun volumeCursorActive(keyCode: Int): Boolean =
        prefs.volumeKeyCursor && isInputViewShown && !emojiSearchActive && !clipboardPanelOpen &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!volumeCursorActive(keyCode)) return super.onKeyDown(keyCode, event)
        // Holding the button keeps stepping; the host's arrow handling moves by grapheme cluster.
        if (event.repeatCount == 0) commitActiveWord()
        val ic = currentInputConnection ?: return true
        moveColumns(ic, if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1)
        if (event.repeatCount == 0) haptic()
        refreshRibbon()
        return true
    }

    // The matching release must be swallowed too, or the system still shows its volume panel.
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (volumeCursorActive(keyCode)) true else super.onKeyUp(keyCode, event)

    /**
     * The input view is a full-height transparent surface with the keys anchored to its bottom
     * ([keyboardColumn]). The system composites the keyboard into the recents screenshot of the
     * host app by laying the IME layer out from the top of the capture, so a surface only as tall
     * as the keys is drawn at the TOP of the card; a surface as tall as the display has the keys
     * land at the bottom (KI-001). The window still behaves like a bottom keyboard: the app
     * resizes above [keyboardColumn] and touches above it fall through to the app.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!::keyboardColumn.isInitialized) return
        val top = keyboardColumn.top
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(keyboardColumn.left, top, keyboardColumn.right, keyboardColumn.bottom)
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this).also { it.listener = this }
        emojiPanel = EmojiPanelView(this).also { it.listener = emojiPanelListener; it.visibility = View.GONE }
        clipboardPanel = ClipboardPanelView(this).also { it.listener = clipboardPanelListener; it.visibility = View.GONE }
        suggestionBar = SuggestionBarView(this).also {
            it.onToggleTools = { it.toolsOpen = !it.toolsOpen }
            it.onTool = ::onTool
            it.onSelect = ::onSuggestionSelected
            it.onSelectEmoji = ::onEmojiSelected
        }
        // The system paints the navigation-bar strip under the keyboard; match it to the keys
        // instead of leaving the default black.
        window?.window?.navigationBarColor = keyboardView.theme.background
        keyboardColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(keyboardView.theme.background)
            // The panel sits ABOVE the keys: in search mode it is a short field + results row
            // and the letter keyboard stays below it.
            addView(emojiPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
            addView(clipboardPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
            addView(suggestionBar)
            addView(keyboardView)
            // On Android 15+ the IME window is edge-to-edge and the system draws its
            // hide/switch buttons in the navigation-bar strip: keep keys clear of it. In landscape
            // a camera cutout or a side navigation bar sits left or right instead of below.
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val safe = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(safe.left, 0, safe.right, safe.bottom)
                insets
            }
        }
        applyChrome()
        keyboardRoot = FrameLayout(this).apply {
            minimumHeight = resources.displayMetrics.heightPixels
            addView(
                keyboardColumn,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
            )
            // Above everything, for the held-key preview. It never takes a touch.
            val popup = KeyPopupView(this@ObadhInputMethodService).apply { isClickable = false }
            addView(popup, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            keyboardView.popup = popup
        }
        return keyboardRoot
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        closeEmojiPanel()
        closeClipboardPanel()
        resetComposition()
        Haptics.level = prefs.hapticStrength
        keyboardView.trackpadEnabled = prefs.spaceTrackpad
        keyboardView.swipeDeleteEnabled = prefs.swipeToDelete
        keyboardView.longPressSymbolsEnabled = prefs.longPressSymbols
        keyboardView.clipboardKeysEnabled = prefs.clipboardKeys
        keyboardView.calloutEnabled = prefs.keyCallout
        fieldKind = if (prefs.smartFields) FieldKind.of(info) else FieldKind.TEXT
        fieldLanguageEnglish = null
        keyboardView.mode = when (fieldKind) {
            // Numeric and phone fields want digits first; the pad has its own way back to letters.
            FieldKind.NUMBER, FieldKind.PHONE -> KeyboardMode.NUMPAD_EN
            else -> KeyboardMode.LETTERS
        }
        keyboardView.fieldKind = fieldKind
        keyboardView.returnIcon = returnIconFor(info)
        keyboardView.returnLabel = info?.takeIf { prefs.returnActionKey }?.actionLabel?.toString()?.takeIf { it.isNotBlank() }?.take(8)
        updateChrome()
        // No globe key: language is Bangla/English from the tools row, and the system's own
        // switcher (navigation bar) reaches other keyboards.
        keyboardView.includesGlobeKey = false
        applyLanguage()
        keyboardView.shiftActive = false
        engine.clearAutosuggestSession()
        // A fresh field starts on the tools; the first key moves to suggestions.
        suggestionBar.toolsOpen = true
        refreshRibbon()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        closeEmojiPanel()
        closeClipboardPanel()
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
        suggestionBar.toolsOpen = false
        keySound(
            when (key) {
                Key.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
                Key.Return -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            },
        )
        if (emojiSearchActive) { onSearchKey(key); return }
        when (key) {
            is Key.Character -> typeLetter(key.value)
            is Key.Symbol -> typeSymbol(key)
            Key.Space -> typeSpace()
            Key.Return -> typeReturn()
            Key.Shift -> toggleShift()
            is Key.ModeSwitch -> { commitActiveWord(); keyboardView.mode = key.target; updateChrome() }
            Key.Globe -> { commitActiveWord(); showKeyboardPicker() }
            Key.Emoji -> openEmojiPanel()
            Key.Tab -> { commitActiveWord(); sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB) }
            Key.CapsLock -> toggleCapsLock()
            Key.HideKeyboard -> { commitActiveWord(); requestHideSelf(0) }
            Key.Backspace -> Unit // handled via onBackspace
        }
    }

    private fun typeLetter(letter: String) {
        if (englishMode) {
            val cased = if (keyboardView.shiftActive || keyboardView.capsLock) letter.uppercase() else letter
            document.insertText(cased)
            if (keyboardView.shiftActive && !keyboardView.capsLock) keyboardView.shiftActive = false
            refreshRibbon()
            return
        }
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

    /**
     * If the word before the caret is one of the user's shortcut triggers, replace it with the
     * expansion plus [trailing] and return true. In Bangla mode the trigger is the raw Roman
     * keys being composed; otherwise (English, symbols such as `@@`) it is the text since the
     * last whitespace.
     */
    private fun expandShortcut(trailing: String): Boolean {
        if (!prefs.textShortcutsEnabled || emojiSearchActive || shortcuts.all().isEmpty()) return false
        if (composer.hasActiveInput) {
            val expansion = shortcuts.lookup(composer.romanBuffer) ?: return false
            composition.clearComposition(document)
            composer.clear()
            document.insertText(expansion + trailing)
        } else {
            val context = document.contextBeforeInput ?: return false
            val trigger = context.takeLastWhile { !it.isWhitespace() }
            if (trigger.isEmpty() || trigger.length > TextShortcuts.MAX_TRIGGER || !atEndOfText()) return false
            val expansion = shortcuts.lookup(trigger) ?: return false
            document.deleteBeforeCursor(trigger.length)
            document.insertText(expansion + trailing)
        }
        engine.clearAutosuggestSession()
        refreshRibbon()
        return true
    }

    private fun typeSpace() {
        val now = System.currentTimeMillis()
        if (expandShortcut(" ")) { lastSpaceAt = now; return }
        if (composer.hasActiveInput) {
            commitActiveWord(trailing = " ")
            lastSpaceAt = now
            return
        }
        // Quick double-space -> "। " (". " in English; time-gated, after a word, replaces the first space).
        val before = document.contextBeforeInput ?: ""
        if (prefs.doubleSpacePeriod && !literalField && now - lastSpaceAt <= DOUBLE_SPACE_MS && atEndOfText()) {
            SmartPunctuation.doubleSpaceSubstitution(before)?.let {
                replaceBeforeCaret(if (englishMode) it.copy(insertion = ". ") else it)
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
        expandShortcut("")
        commitActiveWord()
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else if (prefs.returnActionKey && !noEnterAction && singleLineText(currentInputEditorInfo) &&
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE) == true
        ) {
            // A single-line field has no line to add: done, like the label says.
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        engine.clearAutosuggestSession()
        refreshRibbon()
    }

    private fun typeSymbol(key: Key.Symbol) {
        // Punctuation commits the word first (exact loanwords/auto-insert still apply).
        commitActiveWord()
        val result = if (literalField) SmartPunctuationResult.insert(key.output)
        else SmartPunctuation.literalSubstitution(key.output, document.contextBeforeInput ?: "")
        if (prefs.autoPairs && !literalField && result.deleteBefore == 0 && typePair(result.insertion)) {
            if (key.terminator) engine.clearAutosuggestSession()
            refreshRibbon()
            return
        }
        replaceBeforeCaret(result)
        if (key.terminator) engine.clearAutosuggestSession()
        refreshRibbon()
    }

    /**
     * Brackets and opening quotes come as a pair with the caret between, and a closing one typed
     * right before the same closer just steps over it. Only before the end of a line or a space,
     * so it never doubles up inside a word. Returns true when it handled the key.
     */
    private fun typePair(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        val close = PAIRS[text]
        val next = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
        if (close != null) {
            if (next.isNotEmpty() && !next[0].isWhitespace() && next[0].toString() !in PAIR_CLOSERS) return false
            ic.beginBatchEdit()
            try {
                // Some hosts ignore a cursor position of 0, so step back explicitly.
                ic.commitText(text + close, 1)
                arrow(ic, KeyEvent.KEYCODE_DPAD_LEFT, 1)
            } finally { ic.endBatchEdit() }
            return true
        }
        if (text in PAIR_CLOSERS && next == text) {
            arrow(ic, KeyEvent.KEYCODE_DPAD_RIGHT, 1)
            return true
        }
        return false
    }

    private fun toggleCapsLock() {
        keyboardView.capsLock = !keyboardView.capsLock
        keyboardView.shiftActive = false
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

    /**
     * Long press on X, C or V. The word being typed is ordinary text in the field, so cut and copy
     * act on the host's selection like any other text; the composition is dropped first so a
     * paste (or a cut of the word's own selection) never leaves a stale composed string tracked.
     * With nothing selected, cut and copy take the whole field (copy puts the cursor back).
     */
    override fun onCursorDragStart() {
        if (emojiSearchActive) return
        // The word is real text already; committing just teaches the engine and clears state.
        commitActiveWord()
    }

    override fun onCursorMove(columns: Int, rows: Int) {
        if (emojiSearchActive || (columns == 0 && rows == 0)) return
        val ic = currentInputConnection ?: return
        // The host's own arrow handling moves by grapheme cluster and visual line, but an arrow
        // key at the edge of the text would hand focus to the next view. So every key is only sent
        // when the text itself has room in that direction, and the caret never leaves the field.
        ic.beginBatchEdit()
        try {
            if (rows != 0) moveLines(ic, rows)
            if (columns != 0) moveColumns(ic, columns)
        } finally { ic.endBatchEdit() }
    }

    private fun moveColumns(ic: android.view.inputmethod.InputConnection, columns: Int) {
        val left = columns < 0
        repeat(kotlin.math.abs(columns)) {
            // Re-read each step: it is answered after the previous arrow has been applied.
            val room = if (left) ic.getTextBeforeCursor(1, 0) else ic.getTextAfterCursor(1, 0)
            if (room.isNullOrEmpty()) return
            arrow(ic, if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT, 1)
        }
    }

    /**
     * Up and down. While another line exists in that direction the arrow key is safe and follows
     * wrapped lines; at the first or last line the caret goes to the start or end of the text
     * (as on iOS) instead of leaving the field.
     */
    private fun moveLines(ic: android.view.inputmethod.InputConnection, rows: Int) {
        val up = rows < 0
        repeat(kotlin.math.abs(rows)) {
            val before = ic.getTextBeforeCursor(CURSOR_SCAN_CHARS, 0)?.toString().orEmpty()
            val after = ic.getTextAfterCursor(CURSOR_SCAN_CHARS, 0)?.toString().orEmpty()
            val hasLine = if (up) before.contains('\n') else after.contains('\n')
            if (hasLine) {
                arrow(ic, if (up) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN, 1)
            } else if (up) {
                if (before.isNotEmpty()) ic.setSelection(0, 0)
            } else if (after.isNotEmpty() && after.length < CURSOR_SCAN_CHARS) {
                val start = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.selectionStart
                if (start != null) ic.setSelection(start + after.length, start + after.length)
            }
        }
    }

    private fun arrow(ic: android.view.inputmethod.InputConnection, code: Int, times: Int) {
        repeat(times) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    // Swipe-to-delete from backspace: the words are shown selected, removed when the finger lifts.
    private var swipeCaret = -1
    private var swipeLen = 0
    private var swipeText = ""

    override fun onSwipeDeleteStart() {
        if (emojiSearchActive) return
        commitActiveWord()
        resetComposition()
        val ic = currentInputConnection ?: return
        swipeCaret = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            ?.takeIf { it.selectionStart == it.selectionEnd }?.selectionStart ?: -1
        swipeLen = 0
        // Read once: with a selection showing, "before the cursor" would move with it.
        swipeText = ic.getTextBeforeCursor(SWIPE_DELETE_CHARS, 0)?.toString().orEmpty()
    }

    override fun onSwipeDeleteSelect(words: Int) {
        if (emojiSearchActive) return
        val ic = currentInputConnection ?: return
        val before = swipeText
        var i = before.length
        repeat(words) {
            while (i > 0 && before[i - 1].isWhitespace()) i--
            while (i > 0 && !before[i - 1].isWhitespace()) i--
        }
        val len = before.length - i
        if (len == swipeLen) return
        swipeLen = len
        haptic()
        if (swipeCaret >= 0) ic.setSelection(swipeCaret - len, swipeCaret)
    }

    override fun onSwipeDeleteEnd(apply: Boolean) {
        val ic = currentInputConnection
        if (ic != null) {
            if (swipeCaret >= 0) ic.setSelection(swipeCaret, swipeCaret)
            if (apply && swipeLen > 0) ic.deleteSurroundingText(swipeLen, 0)
        }
        swipeCaret = -1
        swipeLen = 0
        swipeText = ""
        refreshRibbon()
    }

    // Cut/copy slide: words either side of the caret are selected, the action runs on release.
    private var clipCaret = -1
    private var clipBefore = ""
    private var clipAfter = ""
    private var clipSelStart = 0
    private var clipSelEnd = 0

    override fun onClipboardSelectStart() {
        if (emojiSearchActive) return
        commitActiveWord()
        resetComposition()
        val ic = currentInputConnection ?: return
        clipCaret = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            ?.takeIf { it.selectionStart == it.selectionEnd }?.selectionStart ?: -1
        clipBefore = ic.getTextBeforeCursor(SWIPE_DELETE_CHARS, 0)?.toString().orEmpty()
        clipAfter = ic.getTextAfterCursor(SWIPE_DELETE_CHARS, 0)?.toString().orEmpty()
        clipSelStart = clipCaret; clipSelEnd = clipCaret
    }

    override fun onClipboardSelect(words: Int) {
        if (emojiSearchActive || clipCaret < 0) return
        val ic = currentInputConnection ?: return
        var start = clipCaret
        var end = clipCaret
        if (words < 0) {
            var i = clipBefore.length
            repeat(-words) {
                while (i > 0 && clipBefore[i - 1].isWhitespace()) i--
                while (i > 0 && !clipBefore[i - 1].isWhitespace()) i--
            }
            start = clipCaret - (clipBefore.length - i)
        } else if (words > 0) {
            var i = 0
            repeat(words) {
                while (i < clipAfter.length && clipAfter[i].isWhitespace()) i++
                while (i < clipAfter.length && !clipAfter[i].isWhitespace()) i++
            }
            end = clipCaret + i
        }
        if (start == clipSelStart && end == clipSelEnd) return
        clipSelStart = start; clipSelEnd = end
        haptic()
        ic.setSelection(start, end)
    }

    override fun onClipboardSelectEnd(action: ClipboardAction, apply: Boolean) {
        val ic = currentInputConnection
        if (ic != null && clipCaret >= 0) {
            val selected = apply && clipSelEnd > clipSelStart
            if (selected) {
                val id = if (action == ClipboardAction.CUT) android.R.id.cut else android.R.id.copy
                if (ic.performContextMenuAction(id)) suggestionBar.flash(action)
            }
            // Copy and cancel leave the caret where it was; cut has already removed the text.
            if (!(selected && action == ClipboardAction.CUT)) ic.setSelection(clipCaret, clipCaret)
            else ic.setSelection(clipSelStart, clipSelStart)
        }
        clipCaret = -1; clipBefore = ""; clipAfter = ""
        refreshRibbon()
    }

    override fun onClipboard(action: ClipboardAction) {
        if (emojiSearchActive) return
        resetComposition()
        val ic = currentInputConnection ?: return
        val done = when (action) {
            ClipboardAction.PASTE -> {
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                if (clipboard?.hasPrimaryClip() == true) ic.performContextMenuAction(android.R.id.paste) else false
            }
            ClipboardAction.CUT, ClipboardAction.COPY -> {
                val id = if (action == ClipboardAction.CUT) android.R.id.cut else android.R.id.copy
                if (ic.getSelectedText(0).isNullOrEmpty()) {
                    val before = ic.getTextBeforeCursor(WHOLE_FIELD_CHARS, 0)?.length ?: 0
                    val after = ic.getTextAfterCursor(WHOLE_FIELD_CHARS, 0)?.length ?: 0
                    if (before + after == 0) return
                    ic.setSelection(0, before + after)
                    val ok = ic.performContextMenuAction(id)
                    if (action == ClipboardAction.COPY) ic.setSelection(before, before)
                    ok
                } else {
                    ic.performContextMenuAction(id)
                }
            }
        }
        if (done) suggestionBar.flash(action)
        refreshRibbon()
    }

    override fun onBackspace(unit: BackspaceDeletionUnit) {
        if (emojiSearchActive) { searchBackspace(); return }
        if (unit == BackspaceDeletionUnit.CHARACTER) {
            haptic()
            keySound(AudioManager.FX_KEYPRESS_DELETE)
        }
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
            // Backspacing the opener of an empty pair takes the closer with it.
            val ic = currentInputConnection
            val opener = context.lastOrNull()?.toString()
            if (prefs.autoPairs && !literalField && ic != null && opener != null && PAIRS[opener] != null &&
                ic.getTextAfterCursor(1, 0)?.toString() == PAIRS[opener]
            ) ic.deleteSurroundingText(1, 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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
        if (committed.isNotEmpty() && !literalField) worker.execute { engine.commitAutosuggestToken(committed) }
        refreshRibbon()
    }

    private fun onSuggestionSelected(item: SuggestionBarView.Item) {
        haptic()
        if (englishMode) { applyEnglishSuggestion(item.text); return }
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

    // ------------------------------------------------------------ tools and clipboard

    /** Pushes the saved language to the views. */
    /** What the focused field wants; e-mail, web and password fields are typed in English. */
    private var fieldKind = FieldKind.TEXT
    /** The language chosen from the tools inside this field; null follows the field's default. */
    private var fieldLanguageEnglish: Boolean? = null
    private val englishMode get() = fieldLanguageEnglish ?: (prefs.englishMode || fieldKind.forcesEnglish)

    /** Addresses and passwords take what is typed literally: no smart quotes, dashes or dari. */
    private val literalField get() = fieldKind.forcesEnglish

    /** English sentence and word capitals, as the field asks for them (and Gboard does). */
    private fun autoShift() {
        if (!prefs.autoCapitalize || !englishMode || keyboardView.capsLock || keyboardView.mode != KeyboardMode.LETTERS) return
        if (literalField) return
        var type = currentInputEditorInfo?.inputType ?: return
        if (type and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_CLASS_TEXT) return
        // Names and addresses are capitalised whether or not the app asked.
        if (fieldKind == FieldKind.NAME) type = type or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        val caps = currentInputConnection?.getCursorCapsMode(type) ?: return
        keyboardView.shiftActive = caps != 0
    }

    private fun applyLanguage() {
        val english = englishMode
        keyboardView.english = english
        suggestionBar.english = english
    }

    private fun onTool(tool: SuggestionBarView.Tool) {
        haptic()
        // Language flips in place so its icon shows the result; everything else leaves the row.
        if (tool != SuggestionBarView.Tool.LANGUAGE) suggestionBar.toolsOpen = false
        when (tool) {
            SuggestionBarView.Tool.LANGUAGE -> {
                commitActiveWord()
                resetComposition()
                // The saved language applies to every field. Addresses and passwords default to
                // English whatever it is; switching inside one is for that field only.
                if (fieldKind.forcesEnglish) fieldLanguageEnglish = !englishMode
                else prefs.englishMode = !prefs.englishMode
                if (!englishMode) closeSpellSession()
                applyLanguage()
                refreshRibbon()
            }
            SuggestionBarView.Tool.CLIPBOARD -> openClipboardPanel()
            SuggestionBarView.Tool.NUMBERS -> { commitActiveWord(); keyboardView.mode = if (englishMode) KeyboardMode.NUMPAD_EN else KeyboardMode.NUMPAD_BN }
            SuggestionBarView.Tool.EMOJI -> openEmojiPanel()
            SuggestionBarView.Tool.SETTINGS -> {
                startActivity(
                    android.content.Intent(this, org.unmukto.obadh.app.MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun isSensitiveField(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        val cls = type and android.text.InputType.TYPE_MASK_CLASS
        val variation = type and android.text.InputType.TYPE_MASK_VARIATION
        return when (cls) {
            android.text.InputType.TYPE_CLASS_TEXT ->
                variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            android.text.InputType.TYPE_CLASS_NUMBER -> variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Records the current clip. Passwords and clips the source marks sensitive are never kept. */
    private fun captureClipboard() {
        if (!prefs.clipboardHistoryEnabled || isSensitiveField()) return
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
        val clip = try { cm.primaryClip } catch (_: SecurityException) { null } ?: return
        val extras = clip.description.extras
        if (extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
        if (clip.itemCount == 0) return
        val text = try { clip.getItemAt(0).coerceToText(this)?.toString() } catch (_: Exception) { null } ?: return
        if (clipboardHistory.add(text) && clipboardPanelOpen) clipboardPanel.items = clipboardHistory.all()
    }

    private val clipboardPanelListener = object : ClipboardPanelListener {
        override fun onPaste(text: String) {
            haptic()
            closeClipboardPanel()
            resetComposition()
            currentInputConnection?.commitText(text, 1)
            suggestionBar.flash(ClipboardAction.PASTE)
            refreshRibbon()
        }

        override fun onDelete(text: String) {
            clipboardHistory.remove(text)
            clipboardPanel.items = clipboardHistory.all()
        }

        override fun onTogglePin(text: String) {
            haptic()
            clipboardHistory.togglePin(text)
            clipboardPanel.items = clipboardHistory.all()
        }

        override fun onClear() {
            clipboardHistory.clearUnpinned()
            clipboardPanel.items = clipboardHistory.all()
        }

        override fun onClose() = closeClipboardPanel()
    }

    private fun openClipboardPanel() {
        commitActiveWord()
        captureClipboard()
        val height = suggestionBar.height + keyboardView.height
        clipboardPanel.bottomInset = keyboardView.bottomPad
        clipboardPanel.collecting = prefs.clipboardHistoryEnabled
        clipboardPanel.items = clipboardHistory.all()
        suggestionBar.visibility = View.GONE
        keyboardView.visibility = View.GONE
        clipboardPanel.layoutParams = clipboardPanel.layoutParams.also { it.height = height }
        clipboardPanel.visibility = View.VISIBLE
        clipboardPanelOpen = true
    }

    private fun closeClipboardPanel() {
        if (!::clipboardPanel.isInitialized || !clipboardPanelOpen) return
        clipboardPanelOpen = false
        clipboardPanel.visibility = View.GONE
        suggestionBar.visibility = View.VISIBLE
        keyboardView.visibility = View.VISIBLE
        refreshRibbon()
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
        // The bar keeps the same safe area under it as the keys do.
        emojiPanel.bottomInset = keyboardView.bottomPad
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
        val searchDp = if (keyboardView.landscape && keyboardView.family == null) EMOJI_SEARCH_PANEL_LANDSCAPE_DP else EMOJI_SEARCH_PANEL_DP
        emojiPanel.layoutParams = emojiPanel.layoutParams.also { it.height = (searchDp * resources.displayMetrics.density).toInt() }
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
            Key.CapsLock -> toggleCapsLock()
            is Key.ModeSwitch -> keyboardView.mode = key.target
            Key.Return, Key.Emoji, Key.Globe, Key.HideKeyboard -> exitEmojiSearch()
            Key.Tab -> Unit
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
        autoShift()
        if (englishMode) {
            suggestionBar.emojis = emptyList()
            requestEnglishSpelling()
            return
        }
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

    // ------------------------------------------------------- English spelling (system checker)

    private var spellSession: android.view.textservice.SpellCheckerSession? = null
    private var spellWord = ""

    private val spellListener = object : android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener {
        override fun onGetSuggestions(results: Array<out android.view.textservice.SuggestionsInfo>?) {
            val info = results?.firstOrNull() ?: return
            val found = (0 until info.suggestionsCount).map { info.getSuggestionAt(it) }.filter { it.isNotBlank() }.take(3)
            main.post {
                // Stale answers (the word moved on) and a language flip are dropped.
                if (englishMode && spellWord == currentEnglishWord()) {
                    suggestionBar.items = found.map { SuggestionBarView.Item(it) }
                }
            }
        }

        override fun onGetSentenceSuggestions(results: Array<out android.view.textservice.SentenceSuggestionsInfo>?) = Unit
    }

    private fun currentEnglishWord(): String =
        (document.contextBeforeInput ?: "").takeLastWhile { it.isLetter() || it == '\'' || it == '\u2019' }

    /**
     * The device's own spell checker, nothing bundled: when no checker is enabled in system
     * settings the session is null and the ribbon stays empty.
     */
    private fun requestEnglishSpelling() {
        val word = currentEnglishWord()
        spellWord = word
        suggestionBar.items = emptyList()
        if (word.length < 2 || fieldKind != FieldKind.TEXT || !prefs.englishSpelling) return
        if (spellSession == null) {
            val tsm = getSystemService(android.view.textservice.TextServicesManager::class.java)
            spellSession = tsm?.newSpellCheckerSession(null, java.util.Locale.US, spellListener, false)
                ?: tsm?.newSpellCheckerSession(null, null, spellListener, true)
        }
        @Suppress("DEPRECATION")
        spellSession?.getSuggestions(android.view.textservice.TextInfo(word), MAX_SPELLING)
    }

    private fun applyEnglishSuggestion(text: String) {
        val word = currentEnglishWord()
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            if (word.isNotEmpty()) ic.deleteSurroundingText(word.length, 0)
            ic.commitText("$text ", 1)
        } finally { ic.endBatchEdit() }
        suggestionBar.items = emptyList()
    }

    private fun closeSpellSession() {
        spellSession?.close()
        spellSession = null
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
        Haptics.play(keyboardView, strength = prefs.hapticStrength)
    }

    /** The system's own key click, so volume and the "touch sounds" setting still apply. */
    private fun keySound(effect: Int) {
        if (!prefs.keySoundEnabled) return
        audio.playSoundEffect(effect, -1f)
    }

    override fun onDestroy() {
        closeSpellSession()
        getSystemService(android.content.ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val CONTEXT_CHARS = 128
        const val NEXT_WORD_CONTEXT_CHARS = 200
        const val MAX_SPELLING = 5
        const val WHOLE_FIELD_CHARS = 100_000
        val PAIRS = mapOf("(" to ")", "[" to "]", "{" to "}", "“" to "”")
        val PAIR_CLOSERS = PAIRS.values.toSet()
        const val SWIPE_DELETE_CHARS = 2_000
        const val CURSOR_SCAN_CHARS = 3_000
        const val DOUBLE_SPACE_MS = 350L
        const val EMOJI_SEARCH_LIMIT = 40
        const val EMOJI_SEARCH_PANEL_DP = 104
        const val EMOJI_SEARCH_PANEL_LANDSCAPE_DP = 84
    }
}
