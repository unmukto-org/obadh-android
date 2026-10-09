"""Small checked integration points, reapplied to every pinned upstream checkout."""
def apply(main, replace):
    base = main / 'java/helium314/keyboard'
    replace(base / 'latin/utils/JniUtils.java',
        'if (!BuildConfig.BUILD_TYPE.equals("nouserlib") && userSuppliedLibrary != null)',
        'if (!BuildConfig.BUILD_TYPE.equals("nouserlib") && userSuppliedLibrary != null\n'
        '                && helium314.keyboard.latin.obadh.ObadhSwipeCompatibility.supportsDownloadedLibrary())')
    replace(base / 'event/CombinerChain.kt',
        '    private val mCombiners = ArrayList<Combiner>()',
        '    private val mCombiners = ArrayList<Combiner>()\n'
        '    val obadhRoman: String get() = (mCombiners.lastOrNull() as? ObadhCombiner)?.romanInput.orEmpty()')
    replace(base / 'keyboard/KeyboardLayoutSet.kt',
        '            params.settingsValues = settingsValues',
        '            params.settingsValues = settingsValues\n'
        '            params.isSplitLayoutEnabled = settingsValues?.mIsSplitKeyboardEnabled == true')
    replace(base / 'keyboard/internal/KeyboardParams.java',
        'final float defaultKeyWidthFactor = context.getResources().getInteger(R.integer.config_screen_metrics) > 2 ? 0.9f : 1f;',
        'final float defaultKeyWidthFactor = 1f;')
    replace(base / 'keyboard/internal/KeyboardBuilder.kt',
        '        val spacerRelativeWidth = Settings.getValues().mSplitKeyboardSpacerRelativeWidth',
        '        val spacerRelativeWidth = (mParams.mBaseWidth / mContext.resources.displayMetrics.density / 545f - 1f).coerceAtLeast(0.15f)')
    replace(base / 'keyboard/internal/KeyboardParams.java',
        '        setTabletExtraKeys = Settings.getInstance().isTablet() && !mId.getSubtype().isCustom();',
        '        setTabletExtraKeys = false; // Obadh uses the same QWERTY rows on phones and tablets.')
    replace(base / 'keyboard/internal/KeyboardBuilder.kt',
        '        for (row in keysInRows) {\n            val y = row.first().yPos',
        '        for (row in keysInRows) {\n'
        '            if (row.any { it.mCode == Constants.CODE_SPACE }) continue // Keep the shared space bar across the split.\n'
        '            val y = row.first().yPos')
    replace(base / 'keyboard/internal/KeyboardBuilder.kt',
        '            val indexOfProperSpace = row.indexOfFirst { key ->',
        '            if (mParams.mId.element.isAlphabet && row.count { it.mCode in 97..122 } % 2 == 1) {\n'
        '                val boundary = row.getOrNull(insertIndex - 1)\n'
        '                if (boundary != null && boundary.mCode in 97..122) row.add(insertIndex, KeyParams(boundary))\n'
        '            }\n'
        '            val indexOfProperSpace = row.indexOfFirst { key ->')
    replace(base / 'keyboard/KeyboardTheme.kt',
        '            val backgroundImage = Settings.readUserBackgroundImage(context, isNight)',
        '            if (themeName.startsWith("obadh_")) return helium314.keyboard.latin.obadh.ObadhColors.create(context, themeName.removePrefix("obadh_"), themeStyle, hasBorders, isNight)\n'
        '            val backgroundImage = Settings.readUserBackgroundImage(context, isNight)')
    replace(base / 'keyboard/KeyboardTheme.kt',
        '            val isNight = SettingsActivity.forceNight',
        '            val isNight = when (prefs.getString("obadh.theme_mode", "system")) {\n'
        '                "light" -> false\n                "dark" -> true\n'
        '                else -> SettingsActivity.forceNight')
    replace(base / 'keyboard/KeyboardTheme.kt',
        '                ?: (ResourceUtils.isNight(context.resources) && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT))',
        '                ?: (ResourceUtils.isNight(context.resources) && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT))\n            }')
    replace(base / 'latin/LatinIME.java',
        'InputMethodPickerKt.createInputMethodPickerDialog(this, mRichImm, mKeyboardSwitcher.getMainKeyboardView().getWindowToken())',
        'helium314.keyboard.latin.obadh.ObadhLanguagePickerKt.createObadhLanguagePicker(this, mRichImm, mKeyboardSwitcher.getMainKeyboardView().getWindowToken())')
    replace(base / 'latin/settings/SettingsValues.java',
        '        mShowsVoiceInputKey = mInputAttributes.mShouldShowVoiceInputKey;',
        '        mShowsVoiceInputKey = false; // Voice typing is outside this product phase.')
    replace(base / 'latin/settings/SettingsValues.java',
        '        mIsSplitKeyboardEnabled = Settings.readSplitKeyboardEnabled(prefs, isLandscape, isFolded);',
        '        final boolean obadhTablet = res.getConfiguration().smallestScreenWidthDp >= 600;\n'
        '        final String obadhTabletLayout = prefs.getString("obadh.tablet_layout", "automatic");\n'
        '        final String splitPref = isLandscape ? Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE : Settings.PREF_ENABLE_SPLIT_KEYBOARD;\n'
        '        mIsSplitKeyboardEnabled = !mIsLocked && !SettingsKt.isFloatingKeyboardEnabled(context) && (obadhTablet && !obadhTabletLayout.equals("automatic")\n'
        '            ? obadhTabletLayout.equals("split")\n'
        '            : prefs.contains(splitPref) ? Settings.readSplitKeyboardEnabled(prefs, isLandscape, isFolded)\n'
        '            : obadhTablet && (isLandscape || res.getConfiguration().smallestScreenWidthDp >= 768));')
    word = base / 'latin/WordComposer.java'
    replace(word, '    private String mCombiningSpec;',
        '    private boolean mObadhEditingFragment;\n    public boolean isObadhEditingFragment() { return mObadhEditingFragment; }\n    public void markObadhEditingFragment() { mObadhEditingFragment = true; }\n    private long mObadhGeneration;\n    public long getObadhGeneration() { return mObadhGeneration; }\n    private String mObadhRoman = "";\n    public String getObadhRoman() { return mObadhRoman; }\n    public boolean isObadh() { return "bn_obadh".equals(mCombiningSpec); }\n    private String mCombiningSpec;')
    replace(word, '    private WordComposer(WordComposer other) {\n        mEvents = null;',
        '    private WordComposer(WordComposer other) {\n        mObadhEditingFragment = other.mObadhEditingFragment;\n        mObadhGeneration = other.mObadhGeneration;\n        mObadhRoman = other.mObadhRoman;\n        mCombiningSpec = other.mCombiningSpec;\n        mEvents = null;')
    replace(word, '    public void reset() {', '    public void reset() {\n        mObadhEditingFragment = false;')
    replace(word, '        int oldSize = mCodePointSize;',
        '        mObadhGeneration++;\n        mObadhRoman = mCombinerChain.getObadhRoman();\n        int oldSize = mCodePointSize;')
    suggest = base / 'latin/Suggest.kt'
    replace(suggest, '                          inputStyle: Int, sequenceNumber: Int): SuggestedWords =\n        if (wordComposer.isBatchMode)',
        '                          inputStyle: Int, sequenceNumber: Int): SuggestedWords {\n'
        '        helium314.keyboard.latin.obadh.ObadhExtensions.current?.suggestions(\n'
        '            wordComposer, ngramContext, Settings.getValues(), inputStyle, sequenceNumber)?.let { return it }\n'
        '        return if (wordComposer.isBatchMode)')
    replace(suggest, '    // Retrieves suggestions for non-batch input',
        '    } // Obadh extension seam\n\n    // Retrieves suggestions for non-batch input')
    logic = base / 'latin/inputlogic/InputLogic.java'
    # Bangla personalization belongs to the engine; never maintain a second native history.
    ime = base / 'latin/LatinIME.java'
    replace(ime, '                mSettings.getCurrent().mUsePersonalizedDicts\n',
        '                mSettings.getCurrent().mUsePersonalizedDicts && !subtypeLocale.getLanguage().equals("bn")\n')
    replace(ime, '                settingsValues.mUsePersonalizedDicts, false, "", this);',
        '                settingsValues.mUsePersonalizedDicts && !locale.getLanguage().equals("bn"), false, "", this);')
    replace(ime, '                settingsValues.mUsePersonalizedDicts, true, "", this);',
        '                settingsValues.mUsePersonalizedDicts && !mDictionaryFacilitator.getMainLocale().getLanguage().equals("bn"), true, "", this);')
    replace(logic, '        return Character.isLetterOrDigit(codePoint)\n',
        '        return Character.isLetterOrDigit(codePoint)\n'
        '                || Character.getType(codePoint) == Character.COMBINING_SPACING_MARK\n'
        '                || Character.getType(codePoint) == Character.NON_SPACING_MARK\n')
    replace(logic, '        mWordComposer.restartCombining(combiningSpec);',
        '        final var obadh = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        if (obadh != null) obadh.startInput();\n'
        '        mWordComposer.restartCombining(obadh == null ? combiningSpec : obadh.combiningSpec(combiningSpec, settingsValues));')
    replace(logic, '        Event processedEvent = mWordComposer.processEvent(event);',
        '        final var obadh = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        final int romanCodePoint = event.getCodePoint();\n'
        '        if (mWordComposer.isObadh() && (mWordComposer.isResumed() || mWordComposer.isCursorFrontOrMiddleOfComposingWord())\n'
        '                && ((romanCodePoint >= 65 && romanCodePoint <= 90) || (romanCodePoint >= 97 && romanCodePoint <= 122))) {\n'
        '            mConnection.finishComposingText();\n'
        '            resetComposingState(false);\n'
        '            mWordComposer.markObadhEditingFragment();\n'
        '        }\n'
        '        final InputTransaction obadhTransaction = handleObadhInput(settingsValues, event, keyboardCapsMode, handler);\n'
        '        if (obadhTransaction != null) return obadhTransaction;\n'
        '        if (obadh != null) {\n'
        '            final int cp = obadh.transformCodePoint(event.getCodePoint(), settingsValues);\n'
        '            if (cp != event.getCodePoint()) event = Event.createSoftwareKeypressEvent(cp, event.getMetaState(), event.getX(), event.getY(), event.isKeyRepeat());\n'
        '        }\n'
        '        Event processedEvent = mWordComposer.processEvent(event);')
    replace(logic, '        // Add the word to the user history dictionary\n        performAdditionToUserHistoryDictionary',
        '        final var obadh = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        if (obadh != null) obadh.committed(mWordComposer.getTypedWord(), chosenWord,\n'
        '            commitType == LastComposedWord.COMMIT_TYPE_MANUAL_PICK, settingsValues, ngramContext);\n'
        '        // Add the word to the user history dictionary\n        performAdditionToUserHistoryDictionary')
    replace(logic, '            if (settingsValues.mAutoCorrectEnabled && ! isInlineEmojiSearchAction()) {',
        '            if ((settingsValues.mAutoCorrectEnabled || !mWordComposer.getObadhRoman().isEmpty()) && ! isInlineEmojiSearchAction()) {')
    replace(logic, '        var suggestions = mEmojiDictionaryFacilitator.getSuggestions(StringUtilsKt.splitOnWhitespace(input));',
        '        final var obadh = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        if (obadh != null) {\n'
        '            final var emojis = obadh.emojiSearch(input);\n'
        '            if (!emojis.isEmpty()) {\n'
        '                final var infos = new ArrayList<SuggestedWordInfo>();\n'
        '                final var typed = new SuggestedWordInfo(input, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE);\n'
        '                infos.add(typed);\n'
        '                for (var emoji : emojis) infos.add(new SuggestedWordInfo(emoji, "", 1, SuggestedWordInfo.KIND_PREDICTION, Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE));\n'
        '                callback.onGetSuggestedWords(new SuggestedWords(infos, null, typed, true, false, false, SuggestedWords.INPUT_STYLE_TYPING, sequenceNumber));\n'
        '                return;\n'
        '            }\n'
        '        }\n'
        '        var suggestions = mEmojiDictionaryFacilitator.getSuggestions(StringUtilsKt.splitOnWhitespace(input));')
    replace(base / 'latin/SuggestedWords.java', '    public static final int INDEX_OF_TYPED_WORD = 0;',
        '    public long mObadhGeneration = -1;\n    public static final int INDEX_OF_TYPED_WORD = 0;')
    replace(logic, '    public void setSuggestedWords(final SuggestedWords suggestedWords) {',
        '    public boolean acceptsObadhSuggestions(final SuggestedWords words) {\n'
        '        return words.mObadhGeneration < 0 || words.mObadhGeneration == mWordComposer.getObadhGeneration();\n    }\n'
        '    public void setSuggestedWords(final SuggestedWords suggestedWords) {\n'
        '        if (!acceptsObadhSuggestions(suggestedWords)) return;')
    replace(base / 'latin/LatinIME.java', '    private void setSuggestedWords(final SuggestedWords suggestedWords) {',
        '    private void setSuggestedWords(final SuggestedWords suggestedWords) {\n'
        '        if (!mInputLogic.acceptsObadhSuggestions(suggestedWords)) return;')
    replace(logic, '                if (suggestedWords.size() > 1 || typedWordString.length() <= 1) {',
        '                if (suggestedWords.mObadhGeneration >= 0 || suggestedWords.size() > 1 || typedWordString.length() <= 1) {')
    replace(base / 'latin/SuggestedWords.java', '        public final String mWord;',
        '        public boolean mObadhLiteral;\n        public final String mWord;')
    replace(base / 'latin/SuggestedWords.java', '    public String getLabel(final int index) {\n        return mSuggestedWordInfoList.get(index).mWord;',
        '    public String getLabel(final int index) {\n'
        '        final var info = mSuggestedWordInfoList.get(index);\n'
        '        return info.mObadhLiteral ? (char)34 + info.mWord + (char)34 : info.mWord;')
    replace(base / 'latin/dictionary/ExpandableBinaryDictionary.java', '    public void clear() {',
        '    public void clearObadhHistory(final java.util.function.Consumer<Boolean> completion) {\n        asyncExecuteTaskWithWriteLock(() -> {\n            boolean success = false;\n            try {\n                removeBinaryDictionaryLocked();\n                createOnMemoryBinaryDictionaryLocked();\n                success = true;\n            } finally { completion.accept(success); }\n        });\n    }\n\n    public void clear() {')
    replace(base / 'latin/personalization/PersonalizationHelper.java', '    private static class DictFilter implements FilenameFilter {',
        '    public static void clearObadhHistory(final Context context,\n            final java.util.function.Consumer<Boolean> completion) {\n        final java.util.ArrayList<UserHistoryDictionary> dictionaries = new java.util.ArrayList<>();\n        synchronized (sLangUserHistoryDictCache) {\n            for (var reference : sLangUserHistoryDictCache.values()) {\n                final var dictionary = reference.get();\n                if (dictionary != null) dictionaries.add(dictionary);\n            }\n            sLangUserHistoryDictCache.clear();\n        }\n        final var remaining = new java.util.concurrent.atomic.AtomicInteger(dictionaries.size() + 1);\n        final var success = new java.util.concurrent.atomic.AtomicBoolean(true);\n        final java.util.function.Consumer<Boolean> finished = ok -> {\n            if (!ok) success.set(false);\n            if (remaining.decrementAndGet() == 0) {\n                final var files = context.getFilesDir();\n                final boolean deleted = files != null && FileUtils.deleteFilteredFiles(files, new DictFilter(UserHistoryDictionary.NAME));\n                completion.accept(success.get() && deleted);\n            }\n        };\n        for (var dictionary : dictionaries) dictionary.clearObadhHistory(finished);\n        finished.accept(true);\n    }\n\n    private static class DictFilter implements FilenameFilter {')
    # Editor operations stay in InputLogic so its composition/cursor caches stay consistent.
    import pathlib
    code = (pathlib.Path(__file__).parent / 'InputLogic-obadh.java.txt').read_text()
    replace(logic, '    public void onStartBatchInput(final SettingsValues settingsValues,',
        code + '\n    public void onStartBatchInput(final SettingsValues settingsValues,')
    clipboard = base / 'latin/ClipboardHistoryManager.kt'
    replace(clipboard,
        '        ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description)?.let { return it }\n        return InputTypeUtils.isPasswordInputType(inputType)',
        '        return InputTypeUtils.isPasswordInputType(inputType) ||\n            ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description) == true')
    replace(clipboard, '        val description = clipData.description ?: return',
        '        val description = clipData.description ?: return\n'
        '        if (latinIME.mSettings.current.mIncognitoModeEnabled || isClipSensitive(latinIME.currentInputEditorInfo?.inputType ?: 0)) return')
    dao = base / 'latin/database/ClipboardDao.kt'
    replace(dao, '        cache.clear()\n        listener?.onClipsRemoved(0, count())\n        db.writableDatabase.delete(TABLE, null, null)',
        '        val removed = cache.size\n        cache.forEach { it.filename?.let { name -> File(clipFilesDir, name).delete() } }\n'
        '        cache.clear()\n        listener?.onClipsRemoved(0, removed)\n        db.writableDatabase.delete(TABLE, null, null)')
    emoji = base / 'keyboard/emoji/EmojiSearchActivity.kt'
    replace(emoji, '        hintLocales = LocaleList(DictionaryInfoUtils.getLocalesWithEmojiDicts(this).map { Locale(it.toLanguageTag()) })',
        '        hintLocales = if (helium314.keyboard.latin.obadh.ObadhExtensions.current?.emojiSearchBangla == true) LocaleList(Locale("bn-BD"))\n'
        '            else LocaleList(Locale("en-US"))')
    replace(emoji, '        if (dictionaryFacilitator == null) {',
        '        if (dictionaryFacilitator == null && helium314.keyboard.latin.obadh.ObadhExtensions.current == null) {')
    replace(emoji, '        dictionaryFacilitator!!.getSuggestions(text.splitOnWhitespace()).filter { it.isEmoji }.forEach {\n            val emoji = getEmojiDefaultVersion(it.word)',
        '        val obadhEmoji = helium314.keyboard.latin.obadh.ObadhExtensions.current?.emojiSearch(text).orEmpty()\n'
        '        val emojiResults = (obadhEmoji + dictionaryFacilitator?.getSuggestions(text.splitOnWhitespace()).orEmpty().filter { it.isEmoji }.map { it.word }).distinct()\n'
        '        emojiResults.forEach {\n            val emoji = getEmojiDefaultVersion(it)')
    replace(base / 'keyboard/KeyboardLayoutSet.kt',
        '                builder.params.emojiSearchAvailable = getLocalesWithEmojiDicts(context).isNotEmpty()',
        '                builder.params.emojiSearchAvailable = helium314.keyboard.latin.obadh.ObadhExtensions.current != null || getLocalesWithEmojiDicts(context).isNotEmpty()')
    replace(base / 'latin/settings/SettingsValues.java',
        '        mSuggestionsEnabled = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS)',
        '        mSuggestionsEnabled = (mLocale.getLanguage().equals("bn") || prefs.getBoolean("obadh.english_suggestions", true))\n'
        '            && prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS)')
    replace(base / 'latin/utils/InputTypeUtils.java',
        '    public static int getImeOptionsActionIdFromEditorInfo(final EditorInfo editorInfo) {',
        '    public static int getImeOptionsActionIdFromEditorInfo(final EditorInfo editorInfo) {\n'
        '        final var obadh = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        if (obadh != null && !obadh.getReturnActions()) return EditorInfo.IME_ACTION_NONE;')
    replace(base / 'keyboard/KeyboardId.kt', '        params.editorInfo.actionLabel?.toString(),',
        '        if (helium314.keyboard.latin.obadh.ObadhExtensions.current?.returnActions != false) params.editorInfo.actionLabel?.toString() else null,')
    replace(base / 'latin/utils/PopupKeysUtils.kt',
        '    if (!popupKeysDelegate.isInitialized() || popupKeys.isEmpty())',
        '    if (params.mId.element.isAlphabet && helium314.keyboard.latin.obadh.ObadhExtensions.current?.clipboardKeys == true) {\n'
        '        when (label.lowercase()) {\n'
        '            "x" -> popupKeys.add("!icon/cut|!code/-32")\n'
        '            "c" -> popupKeys.add("!icon/copy|!code/-31")\n'
        '            "v" -> popupKeys.add("!icon/paste|!code/-33")\n'
        '        }\n'
        '    }\n'
        '    if (!popupKeysDelegate.isInitialized() || popupKeys.isEmpty())')
    replace(base / 'latin/LatinIME.java', '    public ClipboardHistoryManager getClipboardHistoryManager() {',
        '    public void clearObadhNativeHistory(final java.util.function.Consumer<Boolean> completion) {\n'
        '        PersonalizationHelper.clearObadhHistory(this, success -> new android.os.Handler(getMainLooper()).post(() -> {\n'
        '            mInputLogic.mSuggest.clearNextWordSuggestionsCache();\n'
        '            mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING);\n'
        '            completion.accept(success);\n'
        '        }));\n'
        '    }\n'
        '    public ClipboardHistoryManager getClipboardHistoryManager() {')
    replace(base / 'latin/LatinIME.java', '    public void clearObadhNativeHistory(final java.util.function.Consumer<Boolean> completion) {',
        '    public void reloadObadhSettings() {\n'
        '        KeyboardLayoutSet.Companion.onKeyboardThemeChanged();\n'
        '        loadSettings();\n'
        '        if (isInputViewShown()) mKeyboardSwitcher.reloadKeyboard();\n'
        '        mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING);\n'
        '    }\n'
        '    public void requestObadhSuggestions() {\n'
        '        mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING);\n'
        '    }\n'
        '    public void clearObadhNativeHistory(final java.util.function.Consumer<Boolean> completion) {')
    replace(word,
        '        refreshTypedWordCache();\n        mEvents.add(event);',
        '        // A boundary flush carries the same word: retain its cursor until MULTIPLE_CODE_POINTS applies.\n'
        '        if (processedEvent.getKeyCode() != KeyCode.MULTIPLE_CODE_POINTS || mObadhRoman.isEmpty()) refreshTypedWordCache();\n'
        '        mEvents.add(event);')
    replace(base / 'latin/LatinIME.java', '    public void requestObadhSuggestions() {',
        '    public SuggestedWords getObadhSuggestionsForTests() { return mInputLogic.mSuggestedWords; }\n'
        '    public void requestObadhSuggestions() {')

    replace(logic, '        final AsyncResultHolder<SuggestedWords> holder = new AsyncResultHolder<>("Suggest");',
        '        if (!mWordComposer.isBatchMode() && !isInlineEmojiSearchAction() && !mObadhCommitQuery) {\n'
        '            updateObadhSuggestionsAsync(settingsValues, inputStyle);\n'
        '            return;\n'
        '        }\n'
        '        final AsyncResultHolder<SuggestedWords> holder = new AsyncResultHolder<>("Suggest");')
    replace(logic, '        if (handler.hasPendingUpdateSuggestions()) {',
        '        if (handler.hasPendingUpdateSuggestions() || mWordComposer.isObadh()) {')
    replace(logic, '            performUpdateSuggestionStripSync(settingsValues, SuggestedWords.INPUT_STYLE_TYPING);',
        '            mObadhCommitQuery = true;\n'
        '            mObadhSuggestionRequest.incrementAndGet();\n'
        '            try { performUpdateSuggestionStripSync(settingsValues, SuggestedWords.INPUT_STYLE_TYPING); }\n'
        '            finally { mObadhCommitQuery = false; }')
    replace(emoji, 'import kotlin.properties.Delegates',
        'import androidx.lifecycle.lifecycleScope\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\nimport kotlin.properties.Delegates')
    replace(emoji, '    private var firstSearchDone = false',
        '    private var firstSearchDone = false\n    private var obadhSearchGeneration = 0')
    replace(emoji, '        initDictionaryFacilitator(this)',
        '        if (helium314.keyboard.latin.obadh.ObadhExtensions.current == null) initDictionaryFacilitator(this)')
    replace(emoji, '        val keyboard = emojiPageKeyboardView.keyboard as DynamicGridKeyboard',
        '        val generation = ++obadhSearchGeneration\n'
        '        lifecycleScope.launch {\n'
        '        val emojiResults = withContext(kotlinx.coroutines.Dispatchers.Default) {\n'
        '            val obadhEmoji = helium314.keyboard.latin.obadh.ObadhExtensions.current?.emojiSearch(text).orEmpty()\n'
        '            (obadhEmoji + dictionaryFacilitator?.getSuggestions(text.splitOnWhitespace()).orEmpty().filter { it.isEmoji }.map { it.word }).distinct()\n'
        '        }\n'
        '        if (generation != obadhSearchGeneration || isFinishing) return@launch\n'
        '        val keyboard = emojiPageKeyboardView.keyboard as DynamicGridKeyboard')
    replace(emoji,
        '        val obadhEmoji = helium314.keyboard.latin.obadh.ObadhExtensions.current?.emojiSearch(text).orEmpty()\n'
        '        val emojiResults = (obadhEmoji + dictionaryFacilitator?.getSuggestions(text.splitOnWhitespace()).orEmpty().filter { it.isEmoji }.map { it.word }).distinct()\n'
        '        emojiResults.forEach {',
        '        // Results above were computed off the UI thread.\n        emojiResults.forEach {')
    replace(emoji, '    private fun cancel() {', '    } // Obadh asynchronous search\n\n    private fun cancel() {')
    replace(suggest,
        '        return SuggestedWords(suggestionsList, suggestionResults.mRawSuggestions, typedWordInfo,\n'
        '            isTypedWordValid, hasAutoCorrection || correctToCapitalizedWord, false, inputStyle, sequenceNumber)',
        '        val nativeWords = SuggestedWords(suggestionsList, suggestionResults.mRawSuggestions, typedWordInfo,\n'
        '            isTypedWordValid, hasAutoCorrection || correctToCapitalizedWord, false, inputStyle, sequenceNumber)\n'
        '        return helium314.keyboard.latin.obadh.ObadhExtensions.current?.decorate(wordComposer, nativeWords, Settings.getValues()) ?: nativeWords')
    replace(logic, '        if (mEmojiDictionaryFacilitator == null || isInlineEmojiSearchAction()) {',
        '        if ((mEmojiDictionaryFacilitator == null && helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent() == null) || isInlineEmojiSearchAction()) {')
    replace(logic, '        var suggestions = mEmojiDictionaryFacilitator.getSuggestions(StringUtilsKt.splitOnWhitespace(input));',
        '        if (mEmojiDictionaryFacilitator == null) {\n'
        '            callback.onGetSuggestedWords(SuggestedWords.getEmptyInstance());\n'
        '            return;\n'
        '        }\n'
        '        var suggestions = mEmojiDictionaryFacilitator.getSuggestions(StringUtilsKt.splitOnWhitespace(input));')
    parser = base / 'keyboard/internal/keyboard_parser/EmojiParser.kt'
    replace(parser, 'fun getEmojiDefaultVersion(emoji: String): String = emojiDefaultVersions[emoji] ?: emoji',
        'fun getEmojiDefaultVersion(emoji: String): String = helium314.keyboard.latin.obadh.ObadhExtensions.current?.preferredEmoji(emoji) ?: emojiDefaultVersions[emoji] ?: emoji')
    replace(parser, 'fun getEmojiPopupSpec(emoji: String): String? = emojiPopupSpecs[emoji]',
        'fun getEmojiPopupSpec(emoji: String): String? = helium314.keyboard.latin.obadh.ObadhExtensions.current?.emojiVariants(emoji)\n'
        '    ?.filter { it != emoji && !SupportedEmojis.isUnsupported(it) }?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: emojiPopupSpecs[emoji]')
    replace(base / 'latin/SuggestedWords.java', '    public int getWordCountToShow() {',
        '    public int getWordCountToShow() {\n        if (mObadhGeneration >= 0) return size();')
    replace(base / 'latin/suggestions/SuggestionStripLayoutHelper.java',
        '        final boolean shouldOmitTypedWord = shouldOmitTypedWord(suggestedWords.mInputStyle,',
        '        final boolean shouldOmitTypedWord = suggestedWords.mObadhGeneration < 0 && shouldOmitTypedWord(suggestedWords.mInputStyle,')
    replace(logic,
        '            final int imeOptionsActionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);',
        '            final int imeOptionsActionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);\n'
        '            if (imeOptionsActionId != EditorInfo.IME_ACTION_NONE && mWordComposer.isComposingWord()) {\n'
        '                if (mWordComposer.isObadh() || inputTransaction.getSettingsValues().mAutoCorrectEnabled)\n'
        '                    commitCurrentAutoCorrection(inputTransaction.getSettingsValues(), LastComposedWord.NOT_A_SEPARATOR, handler);\n'
        '                else commitTyped(inputTransaction.getSettingsValues(), LastComposedWord.NOT_A_SEPARATOR);\n'
        '            }')
