"""Small checked integration points, reapplied to every pinned upstream checkout."""
def apply(main, replace):
    base = main / 'java/helium314/keyboard'
    replace(base / 'latin/common/StringUtils.java',
        '        if (label == null || !ScriptUtils.scriptSupportsUppercase(locale)) {\n'
        '            return label;\n'
        '        }',
        '        if (label == null) return null;\n'
        '        // Obadh uses Latin QWERTY keys for Bengali phonetics. Manual Shift must\n'
        '        // uppercase their ASCII labels and codes even though Bengali has no case.\n'
        '        // Keep automatic sentence capitalization disabled for Bengali.\n'
        '        if (!ScriptUtils.scriptSupportsUppercase(locale)) {\n'
        '            if ("bn".equals(locale.getLanguage())\n'
        '                    && helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent() != null\n'
        '                    && label.length() == 1 && label.charAt(0) >= \'a\' && label.charAt(0) <= \'z\') {\n'
        '                return label.toUpperCase(Locale.ROOT);\n'
        '            }\n'
        '            return label;\n'
        '        }')
    replace(base / 'latin/dictionary/DictionaryFactory.kt',
        '        val dictList = LinkedList<Dictionary>()',
        '        helium314.keyboard.latin.obadh.ObadhExtensions.current?.mainDictionary(context, locale)?.let { return listOf(it) }\n'
        '        val dictList = LinkedList<Dictionary>()')
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
        '        // First, so holding the key and lifting picks the clipboard action, not an apostrophe or c-cedilla.\n'
        '        val clipboardAction = when (label.lowercase()) {\n'
        '            "x" -> "!icon/cut|!code/-32"\n'
        '            "c" -> "!icon/copy|!code/-31"\n'
        '            "v" -> "!icon/paste|!code/-33"\n'
        '            else -> null\n'
        '        }\n'
        '        if (clipboardAction != null) {\n'
        '            val others = popupKeys.toList()\n'
        '            popupKeys.clear()\n'
        '            popupKeys.add(clipboardAction)\n'
        '            popupKeys.addAll(others)\n'
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
        '        // The open field keeps the combining rules it started with; restart so a changed field policy applies now.\n'
        '        if (isInputViewShown()) mInputLogic.onSubtypeChanged(mRichImm.getCombiningRulesExtraValueOfCurrentSubtype(), mSettings.getCurrent());\n'
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

    # Subtype changes stay in the same editor; retain its pending gesture word boundary.
    replace(logic,
        '        finishInput();\n        startInput(combiningSpec, settingsValues);',
        '        final boolean preserveGestureSpace = mSpaceState == SpaceState.PHANTOM;\n'
        '        finishInput();\n        startInput(combiningSpec, settingsValues);\n'
        '        if (preserveGestureSpace) mSpaceState = SpaceState.PHANTOM;')
    # Dependent vowel signs end Bangla words but are Unicode marks, not letters.
    replace(logic,
        '        if (Character.isLetterOrDigit(codePointBeforeCursor)\n'
        '                || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)) {',
        '        if (Character.isLetterOrDigit(codePointBeforeCursor)\n'
        '                || Character.getType(codePointBeforeCursor) == Character.COMBINING_SPACING_MARK\n'
        '                || Character.getType(codePointBeforeCursor) == Character.NON_SPACING_MARK\n'
        '                || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)) {')
    # A single host transaction prepares rich content, preserving literal Bangla composition.
    replace(logic, '    public void finishInput() {',
        '    public void prepareObadhContentInput() {\n'
        '        mObadhSuggestionRequest.incrementAndGet();\n'
        '        finishInput();\n'
        '        resetEntireInputState(mConnection.getExpectedSelectionStart(), mConnection.getExpectedSelectionEnd(), true);\n'
        '    }\n'
        '    public void finishInput() {')
    replace(base / 'latin/LatinIME.java', '    public void reloadObadhSettings() {',
        '    public void prepareObadhContentInput() {\n'
        '        mHandler.cancelUpdateSuggestionStrip();\n'
        '        mInputLogic.prepareObadhContentInput();\n'
        '    }\n'
        '    public void reloadObadhSettings() {')

    replace(base / 'latin/LatinIME.java', '        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");',
        '        final var obadh = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        if (obadh != null) obadh.inputViewStarted();\n'
        '        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");')

    # Cut/copy/paste: cut acts on the whole field when nothing is selected, as copy already does,
    # and each successful action pops a badge at the right edge of the strip.
    rich = base / 'latin/RichInputConnection.java'
    replace(rich, '    public void copyText(final boolean getSelection) {',
        '    public boolean copyText(final boolean getSelection) {')
    replace(rich, '            if (et == null) return;\n', '            if (et == null) return false;\n')
    replace(rich, '        if (text == null || text.length() == 0) return;\n        final ClipboardManager cm',
        '        if (text == null || text.length() == 0) return false;\n        final ClipboardManager cm')
    replace(rich,
        '            KeyboardSwitcher.getInstance().showToast(mParent.getString(R.string.toast_msg_clipboard_copy), true);\n        }\n    }',
        '            KeyboardSwitcher.getInstance().showToast(mParent.getString(R.string.toast_msg_clipboard_copy), true);\n        }\n        return true;\n    }')
    badge = 'helium314.keyboard.latin.obadh.ClipboardBadgeView'
    replace(logic, '            case KeyCode.CLIPBOARD_COPY:\n                mConnection.copyText(true);\n                break;',
        '            case KeyCode.CLIPBOARD_COPY:\n'
        '                if (mConnection.copyText(true)) ' + badge + '.flash(' + badge + '.Action.COPY);\n'
        '                break;')
    replace(logic,
        '            case KeyCode.CLIPBOARD_CUT:\n                if (mConnection.hasSelection()) {\n                    mConnection.copyText(true);\n',
        '            case KeyCode.CLIPBOARD_CUT:\n'
        '                // A composing word is part of the text being cut: commit it first.\n'
        '                if (mWordComposer.isComposingWord()) {\n'
        '                    mConnection.finishComposingText();\n'
        '                    resetComposingState(true);\n'
        '                }\n'
        '                if (!mConnection.hasSelection()) {\n'
        '                    // Nothing selected: cut the whole field, matching copy.\n'
        '                    final CharSequence beforeCursor = mConnection.getTextBeforeCursor(100000, 0);\n'
        '                    final CharSequence afterCursor = mConnection.getTextAfterCursor(100000, 0);\n'
        '                    final int cursor = mConnection.getExpectedSelectionStart();\n'
        '                    final int before = beforeCursor == null ? 0 : beforeCursor.length();\n'
        '                    final int after = afterCursor == null ? 0 : afterCursor.length();\n'
        '                    if (before + after > 0 && cursor >= before) mConnection.setSelection(cursor - before, cursor + after);\n'
        '                }\n'
        '                if (mConnection.hasSelection()) {\n'
        '                    if (mConnection.copyText(true)) ' + badge + '.flash(' + badge + '.Action.CUT);\n')
    # Multi-line text goes through the app's own paste so list editors (Google Keep) split it into items;
    # committed text arrives as one line.
    replace(logic, '        if (primaryClip != null) {\n            mConnection.commitText(primaryClip, 1);',
        '        final boolean appHandlesPaste = primaryClip != null && primaryClip.indexOf(\'\\n\') >= 0\n'
        '                && !AppWorkarounds.INSTANCE.doesntCareAboutKeycodePaste(packageName)\n'
        '                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;\n'
        '        if (primaryClip != null && !appHandlesPaste) {\n            mConnection.commitText(primaryClip, 1);')
    replace(logic,
        '            mConnection.commitText(primaryClip, 1);\n            return;\n        }\n        Log.d(TAG, "pasting non-text content");',
        '            mConnection.commitText(primaryClip, 1);\n'
        '            ' + badge + '.flash(' + badge + '.Action.PASTE);\n'
        '            return;\n        }\n        Log.d(TAG, "pasting non-text content");')
    replace(logic, '        else sendDownUpKeyEvent(KeyEvent.KEYCODE_PASTE);\n    }',
        '        else sendDownUpKeyEvent(KeyEvent.KEYCODE_PASTE);\n'
        '        ' + badge + '.flash(' + badge + '.Action.PASTE);\n    }')

    # Number pads: a smaller vertical gap gives taller keys and a near-symmetric grid, as Gboard's pad.
    replace(base / 'keyboard/internal/KeyboardParams.java',
        '            mRelativeVerticalGap = keyboardAttr.getFraction(\n'
        '                    R.styleable.Keyboard_verticalGap, 1, 1, 0) * sv.mKeyGapScale;\n',
        '            mRelativeVerticalGap = keyboardAttr.getFraction(\n'
        '                    R.styleable.Keyboard_verticalGap, 1, 1, 0) * sv.mKeyGapScale;\n'
        '            if (mId.getElement().isNumberLayout()) mRelativeVerticalGap *= 0.67f;\n')

    # Hold X or C a little, then slide: left/right select words, up/down whole lines; lifting
    # cuts (X) or copies (C). A tap or a plain hold keeps the key and its popup as they were.
    listener = base / 'keyboard/KeyboardActionListener.java'
    replace(listener, '    void onMoveDeletePointer(int steps);',
        '    void onMoveDeletePointer(int steps);\n'
        '    /** phase 0 start, 1 select (words xor lines), 2 lift and apply, 3 cancel and restore the caret. */\n'
        '    default void onObadhClipboardSelect(int phase, boolean cut, int words, int lines) {}')
    impl = base / 'keyboard/KeyboardActionListenerImpl.kt'
    replace(impl, '    override fun onMoveDeletePointer(steps: Int) {',
        '    private var obadhCaret = -1\n'
        '    private var obadhBefore = ""\n'
        '    private var obadhAfter = ""\n'
        '    private var obadhStart = 0\n'
        '    private var obadhEnd = 0\n'
        '\n'
        '    override fun onObadhClipboardSelect(phase: Int, cut: Boolean, words: Int, lines: Int) {\n'
        '        when (phase) {\n'
        '            0 -> {\n'
        '                inputLogic.finishInput()\n'
        '                // An existing selection is acted on as it is when the finger lifts.\n'
        '                obadhCaret = if (connection.hasSelection()) -1 else connection.expectedSelectionStart\n'
        '                obadhBefore = connection.getTextBeforeCursor(4000, 0)?.toString().orEmpty()\n'
        '                obadhAfter = connection.getTextAfterCursor(4000, 0)?.toString().orEmpty()\n'
        '                obadhStart = obadhCaret; obadhEnd = obadhCaret\n'
        '            }\n'
        '            1 -> {\n'
        '                if (obadhCaret < 0) return\n'
        '                var start = obadhCaret\n'
        '                var end = obadhCaret\n'
        '                if (words < 0) {\n'
        '                    var i = obadhBefore.length\n'
        '                    repeat(-words) {\n'
        '                        while (i > 0 && obadhBefore[i - 1].isWhitespace()) i--\n'
        '                        while (i > 0 && !obadhBefore[i - 1].isWhitespace()) i--\n'
        '                    }\n'
        '                    start = obadhCaret - (obadhBefore.length - i)\n'
        '                } else if (words > 0) {\n'
        '                    var i = 0\n'
        '                    repeat(words) {\n'
        '                        while (i < obadhAfter.length && obadhAfter[i].isWhitespace()) i++\n'
        '                        while (i < obadhAfter.length && !obadhAfter[i].isWhitespace()) i++\n'
        '                    }\n'
        '                    end = obadhCaret + i\n'
        '                } else if (lines < 0) {\n'
        '                    // From the start of the line n above, through the caret.\n'
        '                    var i = obadhBefore.lastIndexOf(\'\\n\') + 1\n'
        '                    repeat(-lines) { if (i > 0) i = obadhBefore.lastIndexOf(\'\\n\', i - 2) + 1 }\n'
        '                    start = obadhCaret - (obadhBefore.length - i)\n'
        '                } else if (lines > 0) {\n'
        '                    // From the caret, to the end of the line n below.\n'
        '                    var j = obadhAfter.indexOf(\'\\n\').let { if (it < 0) obadhAfter.length else it }\n'
        '                    repeat(lines) {\n'
        '                        if (j < obadhAfter.length) {\n'
        '                            val next = obadhAfter.indexOf(\'\\n\', j + 1)\n'
        '                            j = if (next < 0) obadhAfter.length else next\n'
        '                        }\n'
        '                    }\n'
        '                    end = obadhCaret + j\n'
        '                }\n'
        '                if (start == obadhStart && end == obadhEnd) return\n'
        '                obadhStart = start; obadhEnd = end\n'
        '                gestureMoveBackHaptics()\n'
        '                connection.setSelection(start, end)\n'
        '            }\n'
        '            else -> {\n'
        '                val caret = obadhCaret\n'
        '                obadhCaret = -1\n'
        '                if (phase == 2 && connection.hasSelection() && connection.copyText(true)) {\n'
        '                    helium314.keyboard.latin.obadh.ClipboardBadgeView.flash(\n'
        '                        if (cut) helium314.keyboard.latin.obadh.ClipboardBadgeView.Action.CUT\n'
        '                        else helium314.keyboard.latin.obadh.ClipboardBadgeView.Action.COPY)\n'
        '                    if (cut) {\n'
        '                        connection.commitText("", 1)\n'
        '                        latinIME.requestObadhSuggestions()\n'
        '                    }\n'
        '                } else if (caret >= 0) connection.setSelection(caret, caret)\n'
        '            }\n'
        '        }\n'
        '    }\n'
        '\n'
        '    override fun onMoveDeletePointer(steps: Int) {')

    tracker = base / 'keyboard/PointerTracker.java'
    replace(tracker, '    private boolean mKeySwipeAllowed = false;\n',
        '    private boolean mKeySwipeAllowed = false;\n'
        '    // Held X or C, then slid: select text, act on lift.\n'
        '    private boolean mObadhClipArmed = false;\n'
        '    private boolean mObadhClipSelecting = false;\n'
        '    private boolean mObadhClipCut = false;\n'
        '    private int mObadhClipX, mObadhClipY, mObadhClipWords, mObadhClipLines;\n'
        '    private long mObadhClipTime;\n'
        '    private static final long OBADH_CLIP_HOLD_MS = 150;\n'
        '    private static final int OBADH_CLIP_START = KtxKt.dpToPx(14, Resources.getSystem());\n'
        '    private static final int OBADH_CLIP_WORD_STEP = KtxKt.dpToPx(30, Resources.getSystem());\n'
        '    private static final int OBADH_CLIP_LINE_STEP = KtxKt.dpToPx(26, Resources.getSystem());\n')
    # Space slide: a deliberate sideways slide counts even right after typing, and vertical drift
    # cannot lock out the horizontal slide when no vertical action is set.
    replace(tracker, '        if (code != KeyCode.DELETE && SystemClock.elapsedRealtime() < mStartTime + fastTypingTimeout && sTypingTimeRecorder.isInFastTyping(eventTime))\n            return;',
        '        final boolean deliberateSlide = code == Constants.CODE_SPACE && abs(x - mStartX) >= 3 * sPointerStep;\n'
        '        if (code != KeyCode.DELETE && !deliberateSlide && SystemClock.elapsedRealtime() < mStartTime + fastTypingTimeout && sTypingTimeRecorder.isInFastTyping(eventTime))\n            return;')
    replace(tracker, '            if (stepsY != 0 && abs(dX) < abs(dY) && !mInHorizontalSwipe) {',
        '            if (stepsY != 0 && abs(dX) < abs(dY) && !mInHorizontalSwipe\n'
        '                    && sv.mSpaceSwipeVertical != KeyboardActionListener.SwipeAction.NONE) {')
    replace(tracker, '            startRepeatKey(key);\n            startLongPressTimer(key);\n',
        '            startRepeatKey(key);\n            startLongPressTimer(key);\n'
        '            mObadhClipSelecting = false;\n'
        '            final int obadhCode = Character.toLowerCase(key.getCode());\n'
        '            mObadhClipArmed = !sInGesture && (obadhCode == \'x\' || obadhCode == \'c\')\n'
        '                    && mKeyboard.mId.getElement().isAlphabet()\n'
        '                    && helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent() != null\n'
        '                    && helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent().getClipboardKeys();\n'
        '            if (mObadhClipArmed) {\n'
        '                mObadhClipCut = obadhCode == \'x\';\n'
        '                mObadhClipX = x; mObadhClipY = y; mObadhClipTime = eventTime;\n'
        '            }\n')
    replace(tracker,
        '    private void onMoveEventInternal(final int x, final int y, final long eventTime) {\n        final Key oldKey = mCurrentKey;\n',
        '    private boolean onObadhClipboardMove(final int x, final int y, final long eventTime) {\n'
        '        final int dx = x - mObadhClipX;\n'
        '        final int dy = y - mObadhClipY;\n'
        '        if (!mObadhClipSelecting) {\n'
        '            if (isShowingPopupKeysPanel()) { mObadhClipArmed = false; return false; }\n'
        '            if (eventTime - mObadhClipTime < OBADH_CLIP_HOLD_MS) return false;\n'
        '            if (abs(dx) <= OBADH_CLIP_START && abs(dy) <= OBADH_CLIP_START) return false;\n'
        '            mObadhClipSelecting = true;\n'
        '            mObadhClipWords = 0; mObadhClipLines = 0;\n'
        '            sTimerProxy.cancelKeyTimersOf(this); // no popup, no key repeat\n'
        '            setReleasedKeyGraphics(mCurrentKey, true);\n'
        '            sListener.onObadhClipboardSelect(0, mObadhClipCut, 0, 0);\n'
        '        }\n'
        '        int words = 0, lines = 0;\n'
        '        if (abs(dx) >= abs(dy)) {\n'
        '            if (abs(dx) > OBADH_CLIP_START) words = (dx < 0 ? -1 : 1) * ((abs(dx) - OBADH_CLIP_START) / OBADH_CLIP_WORD_STEP + 1);\n'
        '        } else if (abs(dy) > OBADH_CLIP_START) {\n'
        '            lines = (dy < 0 ? -1 : 1) * ((abs(dy) - OBADH_CLIP_START) / OBADH_CLIP_LINE_STEP + 1);\n'
        '        }\n'
        '        if (words != mObadhClipWords || lines != mObadhClipLines) {\n'
        '            mObadhClipWords = words; mObadhClipLines = lines;\n'
        '            sListener.onObadhClipboardSelect(1, mObadhClipCut, words, lines);\n'
        '        }\n'
        '        return true;\n'
        '    }\n'
        '\n'
        '    private void onMoveEventInternal(final int x, final int y, final long eventTime) {\n'
        '        if (mObadhClipArmed && mCurrentKey != null && !sInGesture && onObadhClipboardMove(x, y, eventTime)) return;\n'
        '        final Key oldKey = mCurrentKey;\n')
    replace(tracker,
        '    private void onUpEventInternal(final int x, final int y, final long eventTime) {\n        sTimerProxy.cancelKeyTimersOf(this);\n',
        '    private void onUpEventInternal(final int x, final int y, final long eventTime) {\n'
        '        sTimerProxy.cancelKeyTimersOf(this);\n'
        '        mObadhClipArmed = false;\n'
        '        if (mObadhClipSelecting) {\n'
        '            mObadhClipSelecting = false;\n'
        '            sListener.onObadhClipboardSelect(2, mObadhClipCut, 0, 0);\n'
        '            if (mCurrentKey != null) callListenerOnRelease(mCurrentKey, mCurrentKey.getCode(), false);\n'
        '            setReleasedKeyGraphics(mCurrentKey, true);\n'
        '            mCurrentKey = null;\n'
        '            resetKeySelectionByDraggingFinger();\n'
        '            return;\n'
        '        }\n')
    replace(tracker,
        '    private void onCancelEventInternal() {\n        sTimerProxy.cancelKeyTimersOf(this);\n',
        '    private void onCancelEventInternal() {\n'
        '        sTimerProxy.cancelKeyTimersOf(this);\n'
        '        mObadhClipArmed = false;\n'
        '        if (mObadhClipSelecting) {\n'
        '            mObadhClipSelecting = false;\n'
        '            sListener.onObadhClipboardSelect(3, mObadhClipCut, 0, 0);\n'
        '        }\n')

    # Holding the space bar turns the keys into a cursor trackpad: one character per step across,
    # one line per step down, no acceleration. The language picker lives on the globe key only.
    replace(tracker, '    private boolean mKeySwipeAllowed = false;\n',
        '    private boolean mKeySwipeAllowed = false;\n'
        '    // Space held: the finger moves the cursor, 1:1.\n'
        '    private boolean mObadhCursorActive = false;\n'
        '    private int mObadhCursorX, mObadhCursorY, mObadhCursorAccX, mObadhCursorAccY;\n'
        '    private static final int OBADH_CURSOR_CHAR_STEP = KtxKt.dpToPx(10, Resources.getSystem());\n'
        '    private static final int OBADH_CURSOR_LINE_STEP = KtxKt.dpToPx(24, Resources.getSystem());\n')
    replace(tracker,
        '    private boolean onObadhClipboardMove(final int x, final int y, final long eventTime) {\n',
        '    private void onObadhCursorMove(final int x, final int y) {\n'
        '        mObadhCursorAccX += x - mObadhCursorX;\n'
        '        mObadhCursorAccY += y - mObadhCursorY;\n'
        '        mObadhCursorX = x;\n'
        '        mObadhCursorY = y;\n'
        '        boolean movedX = false, movedY = false;\n'
        '        while (abs(mObadhCursorAccX) >= OBADH_CURSOR_CHAR_STEP) {\n'
        '            final boolean right = mObadhCursorAccX > 0;\n'
        '            sListener.onCodeInput(right ? KeyCode.ARROW_RIGHT : KeyCode.ARROW_LEFT,\n'
        '                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false);\n'
        '            mObadhCursorAccX -= right ? OBADH_CURSOR_CHAR_STEP : -OBADH_CURSOR_CHAR_STEP;\n'
        '            movedX = true;\n'
        '        }\n'
        '        while (abs(mObadhCursorAccY) >= OBADH_CURSOR_LINE_STEP) {\n'
        '            final boolean down = mObadhCursorAccY > 0;\n'
        '            sListener.onCodeInput(down ? KeyCode.ARROW_DOWN : KeyCode.ARROW_UP,\n'
        '                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false);\n'
        '            mObadhCursorAccY -= down ? OBADH_CURSOR_LINE_STEP : -OBADH_CURSOR_LINE_STEP;\n'
        '            movedY = true;\n'
        '        }\n'
        '        // A mostly horizontal drag must not leak into lines, nor the other way round.\n'
        '        if (movedX && abs(mObadhCursorAccY) < OBADH_CURSOR_LINE_STEP / 2) mObadhCursorAccY = 0;\n'
        '        if (movedY && abs(mObadhCursorAccX) < OBADH_CURSOR_CHAR_STEP / 2) mObadhCursorAccX = 0;\n'
        '    }\n'
        '\n'
        '    private boolean onObadhClipboardMove(final int x, final int y, final long eventTime) {\n')
    replace(tracker,
        '        if (mObadhClipArmed && mCurrentKey != null && !sInGesture && onObadhClipboardMove(x, y, eventTime)) return;\n',
        '        if (mObadhCursorActive) { onObadhCursorMove(x, y); return; }\n'
        '        if (mObadhClipArmed && mCurrentKey != null && !sInGesture && onObadhClipboardMove(x, y, eventTime)) return;\n')
    replace(tracker,
        '        mObadhClipArmed = false;\n        if (mObadhClipSelecting) {\n            mObadhClipSelecting = false;\n            sListener.onObadhClipboardSelect(2,',
        '        mObadhClipArmed = false;\n'
        '        if (mObadhCursorActive) {\n'
        '            mObadhCursorActive = false;\n'
        '            sListener.onCustomRequest(KeyboardActionListener.CustomAction.TOUCHPAD_OFF);\n'
        '            setReleasedKeyGraphics(mCurrentKey, true);\n'
        '            mCurrentKey = null;\n'
        '            resetKeySelectionByDraggingFinger();\n'
        '            return;\n'
        '        }\n'
        '        if (mObadhClipSelecting) {\n            mObadhClipSelecting = false;\n            sListener.onObadhClipboardSelect(2,')
    replace(tracker,
        '        mObadhClipArmed = false;\n        if (mObadhClipSelecting) {\n            mObadhClipSelecting = false;\n            sListener.onObadhClipboardSelect(3,',
        '        mObadhClipArmed = false;\n'
        '        if (mObadhCursorActive) {\n'
        '            mObadhCursorActive = false;\n'
        '            sListener.onCustomRequest(KeyboardActionListener.CustomAction.TOUCHPAD_OFF);\n'
        '        }\n'
        '        if (mObadhClipSelecting) {\n            mObadhClipSelecting = false;\n            sListener.onObadhClipboardSelect(3,')
    replace(tracker,
        '        final int code = key.getCode();\n        sListener.onLongPressKey(code);\n',
        '        final int code = key.getCode();\n'
        '        final var obadhCursor = helium314.keyboard.latin.obadh.ObadhExtensions.getCurrent();\n'
        '        if (code == Constants.CODE_SPACE && !sInGesture && obadhCursor != null && obadhCursor.getSpaceCursor()) {\n'
        '            mObadhCursorActive = true;\n'
        '            mObadhCursorX = mLastX;\n'
        '            mObadhCursorY = mLastY;\n'
        '            mObadhCursorAccX = 0;\n'
        '            mObadhCursorAccY = 0;\n'
        '            mKeySwipeAllowed = false;\n'
        '            sInKeySwipe = false;\n'
        '            sTimerProxy.cancelKeyTimersOf(this);\n'
        '            sListener.onCustomRequest(KeyboardActionListener.CustomAction.TOUCHPAD_ON);\n'
        '            sListener.onCustomRequest(KeyboardActionListener.CustomAction.PERFORM_HAPTIC);\n'
        '            return;\n'
        '        }\n'
        '        sListener.onLongPressKey(code);\n')
    replace(tracker,
        '        if (code == KeyCode.LANGUAGE_SWITCH\n'
        '                || (code == Constants.CODE_SPACE && key.getPopupKeys() == null && Settings.getValues().mSpaceForLangChange)\n'
        '        ) {',
        '        if (code == KeyCode.LANGUAGE_SWITCH) {')
    replace(base / 'keyboard/MainKeyboardView.java',
        '            if (key.isLongPressEnabled() && mHasMultipleEnabledIMEsOrSubtypes && Settings.getValues().mSpaceForLangChange) {',
        '            if (false) { // Obadh: no hint, the globe key opens the language picker')

    # Swiping the space bar to change language slides the language name out and the new one in,
    # in the direction of the swipe.
    main_view = base / 'keyboard/MainKeyboardView.java'
    replace(main_view, '    private void drawLanguageOnSpacebar(final Key key, final Canvas canvas, final Paint paint) {',
        '    // Obadh: set by a language swipe on the space bar; consumed when the label next changes.\n'
        '    public static int sObadhLanguageSlide = 0;\n'
        '    public static long sObadhLanguageSlideTime = 0;\n'
        '    private String mObadhLabel;\n'
        '    private String mObadhPrevLabel;\n'
        '    private float mObadhSlide = 1f;\n'
        '    private int mObadhSlideDirection = 1;\n'
        '    private android.animation.ValueAnimator mObadhSlideAnimator;\n'
        '\n'
        '    private void obadhDrawSpaceLabel(final Canvas canvas, final String text, final int width,\n'
        '            final int height, final float y, final Paint paint) {\n'
        '        final boolean fresh = sObadhLanguageSlide != 0\n'
        '                && android.os.SystemClock.uptimeMillis() - sObadhLanguageSlideTime < 1000;\n'
        '        if (mObadhLabel != null && !text.equals(mObadhLabel) && fresh && isAttachedToWindow()) {\n'
        '            mObadhPrevLabel = mObadhLabel;\n'
        '            mObadhSlideDirection = sObadhLanguageSlide;\n'
        '            sObadhLanguageSlide = 0;\n'
        '            if (mObadhSlideAnimator != null) mObadhSlideAnimator.cancel();\n'
        '            mObadhSlide = 0f;\n'
        '            final var animator = android.animation.ValueAnimator.ofFloat(0f, 1f);\n'
        '            mObadhSlideAnimator = animator;\n'
        '            animator.setDuration(220);\n'
        '            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());\n'
        '            animator.addUpdateListener(a -> {\n'
        '                mObadhSlide = (Float) a.getAnimatedValue();\n'
        '                if (mSpaceKey != null) invalidateKey(mSpaceKey);\n'
        '            });\n'
        '            animator.addListener(new android.animation.AnimatorListenerAdapter() {\n'
        '                @Override public void onAnimationEnd(final android.animation.Animator a) {\n'
        '                    if (mObadhSlideAnimator == animator) mObadhPrevLabel = null;\n'
        '                    if (mSpaceKey != null) invalidateKey(mSpaceKey);\n'
        '                }\n'
        '            });\n'
        '            animator.start();\n'
        '        }\n'
        '        mObadhLabel = text;\n'
        '        final float p = mObadhSlide;\n'
        '        if (mObadhPrevLabel == null || p >= 1f) {\n'
        '            canvas.drawText(text, width / 2f, y, paint);\n'
        '            return;\n'
        '        }\n'
        '        final int alpha = paint.getAlpha();\n'
        '        final float shift = width * 0.3f;\n'
        '        canvas.save();\n'
        '        canvas.clipRect(0, 0, width, height);\n'
        '        paint.setAlpha(Math.round(alpha * (1f - p)));\n'
        '        canvas.drawText(mObadhPrevLabel, width / 2f + mObadhSlideDirection * p * shift, y, paint);\n'
        '        paint.setAlpha(Math.round(alpha * p));\n'
        '        canvas.drawText(text, width / 2f - mObadhSlideDirection * (1f - p) * shift, y, paint);\n'
        '        paint.setAlpha(alpha);\n'
        '        canvas.restore();\n'
        '    }\n'
        '\n'
        '    private void drawLanguageOnSpacebar(final Key key, final Canvas canvas, final Paint paint) {')
    replace(main_view, '        canvas.drawText(spaceText, width / 2f, baseline - descent, paint);\n',
        '        obadhDrawSpaceLabel(canvas, spaceText, width, height, baseline - descent, paint);\n')
    replace(impl,
        '        if (abs(steps) < settings.current.mLanguageSwipeDistance) return false\n'
        '        val subtypes = SubtypeSettings.getEnabledSubtypes(true)',
        '        if (abs(steps) < settings.current.mLanguageSwipeDistance) return false\n'
        '        helium314.keyboard.keyboard.MainKeyboardView.sObadhLanguageSlide = if (steps > 0) 1 else -1\n'
        '        helium314.keyboard.keyboard.MainKeyboardView.sObadhLanguageSlideTime = android.os.SystemClock.uptimeMillis()\n'
        '        val subtypes = SubtypeSettings.getEnabledSubtypes(true)')

    # With Bangla selected the QWERTY number row, and the hints on the top letter row, show Bangla
    # digits. The latin digit stays on a long press (the number row keeps it as a popup).
    replace(main / 'assets/locale_key_texts/bn-BD.txt', '[popup_keys]\n',
        '[number_row]\n\u09e7 \u09e8 \u09e9 \u09ea \u09eb \u09ec \u09ed \u09ee \u09ef \u09e6\n\n[popup_keys]\n')

