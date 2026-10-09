package org.unmukto.obadh.keyboard

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo as Info
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.obadh.ObadhExtension
import helium314.keyboard.latin.obadh.ObadhExtensions
import helium314.keyboard.latin.settings.SettingsValues
import org.json.JSONArray
import org.unmukto.obadh.emoji.*
import org.unmukto.obadh.engine.*
import org.unmukto.obadh.settings.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Model work and personal writes never run on the input thread. One runtime per IME process. */
object NativeObadhFeatures : ObadhExtension {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Obadh models and learning").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val engine = ObadhBridgeClient()
    private var initialized = false
    @Volatile var modelsReady = false
        private set
    @Volatile private var options = Options()
    @Volatile private var emoji = BanglaEmojiSuggestionStore.EMPTY
    @Volatile private var search = BanglaEmojiSearchStore.EMPTY
    @Volatile private var catalog = EmojiDataStore.EMPTY
    private lateinit var learned: LearnedWordStore
    private lateinit var variants: EmojiVariantPreferenceStore
    @Volatile private var variantPreferences: Map<String, String> = emptyMap()
    private lateinit var personal: PersonalAutosuggestStore
    private val corrections = object : LinkedHashMap<String, List<DetailedCorrection>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<DetailedCorrection>>?) = size > 128
    }
    private var savePending = false
    private var pendingSave: java.util.concurrent.ScheduledFuture<*>? = null
    private var imeReference = java.lang.ref.WeakReference<ObadhInputMethodService>(null)
    val activeIme get() = imeReference.get()
    fun attach(ime: ObadhInputMethodService) { imeReference = java.lang.ref.WeakReference(ime) }
    fun refreshSettings(appearanceChanged: Boolean = false) {
        activeIme?.let {
            if (appearanceChanged) helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().setThemeNeedsReload()
            it.reloadObadhSettings()
        }
    }

    private data class Options(
        val autoInsert: Boolean = false, val pairs: Boolean = true,
        val shortcutsEnabled: Boolean = true, val smartFields: Boolean = true,
        val clipboardKeys: Boolean = true, val returnActions: Boolean = true,
        val volumeCursor: Boolean = false, val emojiBangla: Boolean = false,
        val shortcuts: Map<String, String> = emptyMap(),
    )

    fun configure(values: Bundle) {
        val shortcuts = runCatching {
            val json = JSONArray(values.getString("obadh.shortcuts", "[]"))
            buildMap { for (i in 0 until json.length()) json.getJSONObject(i).let { put(it.getString("t"), it.getString("e")) } }
        }.getOrDefault(emptyMap())
        options = Options(values.getBoolean("obadh.auto_insert"), values.getBoolean("obadh.pairs", true),
            values.getBoolean("obadh.shortcuts_enabled", true), values.getBoolean("obadh.smart_fields", true),
            values.getBoolean("obadh.clipboard_keys", true), values.getBoolean("obadh.return_actions", true),
            values.getBoolean("obadh.volume_cursor"), values.getBoolean("obadh.emoji_bangla"), shortcuts)
    }

    @Synchronized fun initialize(context: Context) {
        ObadhExtensions.current = this
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        learned = LearnedWordStore(app)
        personal = PersonalAutosuggestStore(app)
        engine.initializeTransliteration()
        worker.execute {
            runCatching {
                val directory = ModelInstaller.ensureInstalled(app)
                val config = engine.configureModels(directory)
                emoji = EmojiModels.suggestionStore(directory)
                search = EmojiModels.banglaSearchStore(directory)
                catalog = EmojiModels.dataStore(directory)
                variants = EmojiVariantPreferenceStore(PrefsEmojiKeyValueStore(app))
                variantPreferences = variants.load()
                personal.restore(engine)
                modelsReady = config.autocorrectAvailable && config.autosuggestAvailable
                Handler(Looper.getMainLooper()).post { activeIme?.requestObadhSuggestions() }
            }.onFailure { android.util.Log.e("ObadhModels", "Models unavailable; deterministic typing remains available", it) }
        }
    }

    private fun literal(settings: SettingsValues): Boolean {
        val field = FieldKind.ofInputType(settings.mInputAttributes.mInputType)
        return field == FieldKind.PASSWORD || field == FieldKind.NUMBER || field == FieldKind.PHONE ||
            (options.smartFields && field.forcesEnglish)
    }

    override fun combiningSpec(spec: String?, settings: SettingsValues) = if (literal(settings)) "" else spec.orEmpty()
    override fun pairsEnabled(settings: SettingsValues) = options.pairs && !literal(settings)
    override val clipboardKeys get() = options.clipboardKeys
    override val returnActions get() = options.returnActions
    override val emojiSearchBangla get() = options.emojiBangla
    val volumeCursor get() = options.volumeCursor

    override fun transformCodePoint(codePoint: Int, settings: SettingsValues): Int {
        if (settings.mLocale.language != "bn" || literal(settings)) return codePoint
        return when (codePoint) {
            in '0'.code..'9'.code -> codePoint + ('০'.code - '0'.code)
            '.'.code -> '।'.code
            '$'.code -> '৳'.code
            else -> codePoint
        }
    }

    override fun shortcut(trigger: String, settings: SettingsValues): String? =
        if (options.shortcutsEnabled && !literal(settings)) options.shortcuts[trigger] else null

    override val stickersSupported get() = org.unmukto.obadh.stickers.StickerController.supported()
    override fun showStickers(anchor: android.view.View, onDismiss: () -> Unit) = org.unmukto.obadh.stickers.StickerController.show(anchor, onDismiss)

    override fun inputViewStarted() { activeIme?.let(org.unmukto.obadh.stickers.StickerController::returned) }
    override fun startInput() { worker.execute { engine.clearAutosuggestSession() } }

    override fun suggestions(composer: WordComposer, context: NgramContext, settings: SettingsValues,
                             inputStyle: Int, sequence: Int): SuggestedWords? {
        if (settings.mLocale.language != "bn" || literal(settings) || composer.isBatchMode) return null
        val typed = composer.typedWord
        val roman = composer.obadhRoman
        val typedInfo = typed.takeIf { it.isNotEmpty() }?.let { info(it, Info.KIND_TYPED) }
        val entries = ArrayList<Info>()
        var target: String? = null
        if (typedInfo != null) {
            entries += typedInfo
            if (modelsReady) {
                if (composer.isObadhEditingFragment) {
                    // Mid-word phonetic edits own only their inserted fragment; never replace the surrounding word.
                } else if (roman.isNotEmpty() && !composer.isResumed) {
                    val input = RomanInputRules.engineInput(roman)
                    val detailed = synchronized(corrections) { corrections[input] } ?: engine.detailedCorrections(input, 8).also {
                        synchronized(corrections) { corrections[input] = it }
                    }
                    val composition = KeyboardComposer(engine, compositionSuggestionLimit = 6)
                    composition.append(roman)
                    composition.mergeAutocorrectCandidates(detailed.map { it.text }, composition.generation)
                    composition.resolveAutocorrectTarget(options.autoInsert, engine.wordFrequency(typed), detailed, learned::isProtected)
                    target = composition.commitText.takeIf { it != typed && !composer.isResumed && !composer.isCursorFrontOrMiddleOfComposingWord }
                    typedInfo.mObadhLiteral = target != null || composition.exactLoanwordTarget != null || !engine.isLexiconWord(typed)
                    target?.let { entries += info(it, Info.KIND_CORRECTION) }
                    composition.activeSuggestions.forEach { suggestion ->
                        if (entries.none { it.mWord == suggestion.text }) entries += info(suggestion.text, Info.KIND_CORRECTION)
                    }
                } else {
                    // Touched committed Bangla uses the engine's word-side re-correction API.
                    engine.wordAlternatives(typed, 6).filter { it != typed }.forEach { entries += info(it, Info.KIND_CORRECTION) }
                }
                val emojis = emoji.emojis(typed).map { info(it, Info.KIND_PREDICTION) }
                entries.addAll(minOf(2, entries.size), emojis)
            }
        } else if (modelsReady) {
            val previous = (context.prevWordCount downTo 1).mapNotNull { context.getNthPrevWord(it)?.toString()?.takeIf(String::isNotBlank) }
            if (previous.isNotEmpty()) engine.autosuggestSuggestions(previous.joinToString(" ") + " ", 6, settings.mUsePersonalizedDicts && !settings.mIncognitoModeEnabled)
                .forEach { entries += info(it, Info.KIND_PREDICTION) }
            previous.lastOrNull()?.let { word -> emoji.emojis(word).forEach { entries += info(it, Info.KIND_PREDICTION) } }
        }
        return SuggestedWords(entries, null, typedInfo, target == null, target != null, false,
            if (typed.isEmpty()) SuggestedWords.INPUT_STYLE_PREDICTION else inputStyle, sequence).apply {
                mObadhGeneration = composer.obadhGeneration
            }
    }

    override fun committed(typed: String, chosen: String, manual: Boolean, settings: SettingsValues, context: NgramContext) {
        recordEmoji(chosen)
        if (settings.mLocale.language != "bn" || literal(settings) || settings.mIncognitoModeEnabled || !settings.mUsePersonalizedDicts) return
        if (chosen.isEmpty() || chosen.none { it in '\u0980'..'\u09ff' }) return // emoji queries never enter learning
        val previous = (context.prevWordCount downTo 1).mapNotNull { context.getNthPrevWord(it)?.toString()?.takeIf(String::isNotBlank) }.joinToString(" ")
        worker.execute {
            if (!modelsReady) return@execute
            if (manual && typed == chosen) learned.protect(chosen)
            engine.commitAutosuggestToken(chosen, previous)
            Handler(Looper.getMainLooper()).post { activeIme?.takeIf { it.isInputViewShown }?.requestObadhSuggestions() }
            if (!savePending) {
                savePending = true
                pendingSave = worker.schedule({ savePending = false; personal.save(engine) }, 1, TimeUnit.SECONDS)
            }
        }
    }

    override fun emojiSearch(query: String): List<String> {
        if (!modelsReady || query.isBlank()) return emptyList()
        if (query.any { it in '\u0980'..'\u09ff' }) return search.search(query, 32)
        val english = catalog.search(query, 32).map { it.emoji }
        if (english.isNotEmpty()) return english
        // Roman Bangla also works without forcing a search-language setting.
        val romanBangla = query.split(' ').joinToString(" ") { engine.transliterate(RomanInputRules.engineInput(it)) }
        return search.search(romanBangla, 32)
    }

    fun clearLearned(onComplete: (Boolean) -> Unit) {
        worker.execute {
            val success = runCatching {
                pendingSave?.cancel(false)
                savePending = false
                engine.clearAutosuggestSession()
                engine.clearPersonalAutosuggest()
                if (::learned.isInitialized) learned.clear()
                if (::personal.isInitialized) personal.clear()
            }.isSuccess
            Handler(Looper.getMainLooper()).post { onComplete(success) }
        }
    }

    override fun decorate(composer: WordComposer, words: SuggestedWords, settings: SettingsValues): SuggestedWords {
        if (!modelsReady || settings.mLocale.language != "en" || literal(settings) || composer.isBatchMode || !settings.mSuggestEmojis) return words
        val query = composer.typedWord.lowercase(java.util.Locale.ROOT)
        if (query.length < 3) return words
        val emojis = catalog.search(query, 12).filter { it.normalizedName == query || query in it.normalizedKeywords }
            .map { it.emoji }.distinct().take(3)
        if (emojis.isEmpty()) return words
        val entries = ArrayList((0 until words.size()).map { words.getInfo(it) })
        entries.addAll(minOf(2, entries.size), emojis.filter { e -> entries.none { it.mWord == e } }.map { info(it, Info.KIND_PREDICTION) })
        return SuggestedWords(entries, words.mRawSuggestions, words.mTypedWordInfo, words.mTypedWordValid,
            words.mWillAutoCorrect, words.mIsObsoleteSuggestions, words.mInputStyle, words.mSequenceNumber)
    }

    override fun preferredEmoji(emoji: String): String? = variantPreferences[emoji]
    override fun emojiVariants(emoji: String): List<String> = catalog.item(emoji)?.let { catalog.variantOptions(it).map { it.emoji } }.orEmpty()

    fun recordEmoji(emoji: String) {
        if (!modelsReady) return
        val item = catalog.item(emoji) ?: return
        val choices = catalog.variantOptions(item)
        val base = choices.firstOrNull()?.emoji ?: return
        variantPreferences = if (base == emoji) variantPreferences - base else variantPreferences + (base to emoji)
        worker.execute { variants.record(base, emoji) }
    }

    private fun info(text: String, kind: Int): Info {
        val entry = Info(preferredEmoji(text) ?: text, "", Info.MAX_SCORE, kind,
            Dictionary.DICTIONARY_USER_TYPED, Info.NOT_AN_INDEX, Info.NOT_A_CONFIDENCE)
        return helium314.keyboard.latin.Suggest.useDefaultEmojiSkinTone(entry)
    }
}
