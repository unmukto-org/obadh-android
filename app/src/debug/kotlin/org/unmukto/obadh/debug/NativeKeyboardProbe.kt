package org.unmukto.obadh.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.view.inputmethod.InputMethodManager
import helium314.keyboard.event.Event
import helium314.keyboard.latin.utils.SubtypeSettings
import org.unmukto.obadh.keyboard.NativeObadhFeatures
import org.unmukto.obadh.settings.NativePreferences

/** Real framework editor + native IME commands, excluded from release. No fake InputConnection. */
class NativeKeyboardProbeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val field = object : EditText(this) {
            override fun onCreateInputConnection(info: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
                val connection=super.onCreateInputConnection(info) ?: return null
                val mode=intent.getStringExtra("field")
                if(mode !in listOf("sticker","media","reject_media","gif_only","webp_only")) return connection
                info.contentMimeTypes=when(mode) {
                    "gif_only" -> arrayOf("image/gif")
                    "webp_only" -> arrayOf("image/webp")
                    "sticker" -> arrayOf("image/png")
                    else -> arrayOf("image/gif","image/webp")
                }
                return object : android.view.inputmethod.InputConnectionWrapper(connection,false) {
                    override fun commitContent(content: android.view.inputmethod.InputContentInfo,flags: Int,options: Bundle?): Boolean {
                        if(mode=="reject_media") { android.util.Log.i("ObadhMediaReceipt","rejected");return false }
                        return runCatching {
                            content.requestPermission()
                            val bytes=contentResolver.openInputStream(content.contentUri)!!.use { it.readBytes() }
                            val mime=contentResolver.getType(content.contentUri)!!
                            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                            val file=org.unmukto.obadh.media.MediaFile("https://static.klipy.com/probe",mime,bounds.outWidth,bounds.outHeight,bytes.size)
                            check(content.description.hasMimeType(mime) && org.unmukto.obadh.media.MediaSafety.valid(bytes,file))
                            android.util.Log.i("ObadhMediaReceipt",org.json.JSONObject().put("bytes",bytes.size).put("mime",mime).put("flags",flags).put("uri",content.contentUri.toString()).put("hash",org.unmukto.obadh.media.MediaSafety.digest(bytes)).toString())
                            content.releasePermission();true
                        }.getOrElse { android.util.Log.e("ObadhMediaReceipt","failed",it);false }
                    }
                }
            }
        }.apply {
            id = android.R.id.edit
            inputType = when (intent.getStringExtra("field")) {
                "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "number" -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            }
            setText(intent.getStringExtra("initial").orEmpty())
            setSelection(text.length)
            setSingleLine(intent.getStringExtra("field") in listOf("email", "password", "number", "search"))
            if (intent.getStringExtra("field") == "search") imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ -> android.util.Log.i("ObadhEditorAction", action.toString()); true }
            hint = "Native integration test"
            textSize = 24f
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 96, 24, 24)
            addView(field, LinearLayout.LayoutParams(-1, 420))
        })
        field.requestFocus()
        field.postDelayed({ getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }, 200)
    }
}

class NativeKeyboardProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ime = NativeObadhFeatures.activeIme ?: return
        if (ime.currentInputEditorInfo == null) return
        val handler = Handler(Looper.getMainLooper())
        when (intent.getStringExtra("command")) {
            "engine_release_probe" -> {
                val expectedVersion = intent.getStringExtra("version")
                Thread({
                    val result = runCatching {
                        val version = org.unmukto.obadh.engine.EngineInfo.version()
                        check(version == expectedVersion) { "Expected $expectedVersion, installed $version" }
                        check(org.unmukto.obadh.engine.EngineInfo.abiVersion() == 2) { "Unexpected C ABI" }
                        org.unmukto.obadh.engine.ObadhBridgeClient().use { client ->
                            val models = client.configureModels(org.unmukto.obadh.settings.ModelInstaller.ensureInstalled(context))
                            check(models.autocorrectAvailable && models.autosuggestAvailable) { "Models unavailable" }
                            for ((literal, emoji) in listOf(":)" to "😃", ":-)" to "😃", ":D" to "😄", ":-D" to "😄")) {
                                check(client.compositionSuggestions(literal, 2) == listOf(literal, emoji)) { "Composition alternatives for $literal" }
                                val candidate = client.detailedCorrections(literal, 2).single()
                                check(candidate.text == emoji && candidate.source == org.unmukto.obadh.engine.DetailedCorrection.Source.EMOTICON_EXACT) { "Detailed candidate for $literal" }
                                check(!org.unmukto.obadh.engine.AutoInsertGate.shouldAutoInsert(0, candidate, false)) { "Emoji auto-insert for $literal" }
                            }
                        }
                        org.json.JSONObject().put("version", version).put("abi", 2).put("emoticon_literals_preserved", true)
                    }.getOrElse { org.json.JSONObject().put("error", it.toString()) }
                    android.util.Log.i("ObadhEngineReleaseProbe", result.toString())
                }, "Obadh engine release check").start()
            }
            "toolbar_coordinates" -> {
                val main=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().mainKeyboardView ?: return
                val root=main.rootView
                val strip=root.findViewById<helium314.keyboard.latin.suggestions.SuggestionStripView>(helium314.keyboard.latin.R.id.suggestion_strip_view) ?: return
                android.util.Log.i("ObadhProbeToolbar",strip.obadhToolbarCoordinatesForTests().toString())
            }
            "coordinates" -> {
                val codes = intent.getStringExtra("codes")?.split(',')?.map(String::toInt)?.toIntArray()
                    ?: intent.getStringExtra("text").orEmpty().codePoints().toArray()
                val main=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().mainKeyboardView
                val view=if(intent.getStringExtra("surface")=="emoji") main.rootView.findViewById<helium314.keyboard.keyboard.MainKeyboardView>(helium314.keyboard.latin.R.id.bottom_row_keyboard) else main
                val coordinates = requireNotNull(view.keyboard).getCoordinates(codes)
                val origin = IntArray(2)
                view.getLocationOnScreen(origin)
                android.util.Log.i("ObadhProbeCoordinates", org.json.JSONObject()
                    .put("codes", org.json.JSONArray(codes.toList()))
                    .put("coordinates", org.json.JSONArray(coordinates.toList()))
                    .put("origin", org.json.JSONArray(origin.toList())).toString())
            }
            "personalization_probe" -> {
                Thread({
                    val result = runCatching {
                        org.unmukto.obadh.engine.ObadhBridgeClient().use { client ->
                            client.configureModels(org.unmukto.obadh.settings.ModelInstaller.ensureInstalled(context))
                            val query = "আমার "
                            val name = "ওবাধনামপরীক্ষা"
                            val public = client.autosuggestSuggestions(query, 6, false)
                            repeat(8) {
                                client.clearAutosuggestSession()
                                check(client.commitAutosuggestToken("আমার"))
                                check(client.commitAutosuggestToken(name))
                            }
                            client.clearAutosuggestSession()
                            check(client.commitAutosuggestToken("আমার"))
                            val snapshot = client.exportPersonalAutosuggestSnapshot()!!
                            val personalized = client.autosuggestSuggestions(query, 6, true)
                            check(name in personalized && name !in public)
                            check(public == client.autosuggestSuggestions(query, 6, false))
                            check(snapshot.contentEquals(client.exportPersonalAutosuggestSnapshot()!!))
                            check(personalized == client.autosuggestSuggestions(query, 6, true))
                            val other = client.autosuggestSuggestions("তুমি ", 6, false)
                            check(other == client.autosuggestSuggestions("তুমি ", 6, true))
                            check(client.commitAutosuggestToken("তুমি"))
                            check(client.commitAutosuggestToken("আমার", "অন্য"))
                            check(name in client.autosuggestSuggestions(query, 6, true))
                            client.clearPersonalAutosuggest()
                            check(name !in client.autosuggestSuggestions(query, 6, true))
                            org.json.JSONObject().put("public_model_preserved", true).put("personal_predictions_work", true)
                                .put("editor_context_guard", true).put("learning_context_guard", true)
                                .put("incognito_excludes_personal", true).put("clear_works", true)
                        }
                    }.getOrElse { org.json.JSONObject().put("error", it.toString()) }
                    android.util.Log.i("ObadhPersonalizationProbe", result.toString())
                }, "Obadh isolated ABI test").start()
            }
            "language" -> {
                val locale = intent.getStringExtra("language") ?: "bn"
                SubtypeSettings.getEnabledSubtypes().firstOrNull { it.languageTag.startsWith(locale) || it.locale.startsWith(locale) }
                    ?.let { ime.onCurrentInputMethodSubtypeChanged(it) }
            }
            "configure" -> {
                val values = NativePreferences.snapshot(context)
                intent.extras?.keySet()?.filter { (it.startsWith("obadh.") || it.startsWith("native.")) }?.forEach { key ->
                    val value = intent.extras?.get(key)
                    when (value) { is Boolean -> values.putBoolean(key, value); is String -> values.putString(key, value) }
                }
                intent.extras?.keySet()?.filter { it.startsWith("native.") }?.forEach { key ->
                    val target = key.removePrefix("native.")
                    @Suppress("DEPRECATION")
                    when (val value = intent.extras?.get(key)) {
                        is Boolean -> values.putBoolean(target, value)
                        is String -> values.putString(target, value)
                    }
                }
                NativePreferences.apply(context, values)
            }
            "inspect" -> {
                val words = ime.obadhSuggestionsForTests
                val settings = helium314.keyboard.latin.settings.Settings.getValues()
                val controls = org.json.JSONObject()
                    .put("locale", settings.mLocale.language)
                    .put("emoji_panel", helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes)
                    .put("clipboard_panel", helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingClipboardHistory)
                    .put("night", ime.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    .put("smallest_width", ime.resources.configuration.smallestScreenWidthDp)
                    .put("split", settings.mIsSplitKeyboardEnabled)
                    .put("floating", settings.mIsFloatingKeyboard)
                    .put("one_handed", settings.mOneHandedModeEnabled)
                    .put("theme_background", settings.mColors.get(helium314.keyboard.latin.common.ColorType.MAIN_BACKGROUND))
                    .put("theme_keys", settings.mColors.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND))
                    .put("borders", settings.mColors.hasKeyBorders)
                    .put("incognito", settings.mIncognitoModeEnabled)
                    .put("learning", settings.mUsePersonalizedDicts)
                    .put("suggestions", settings.mSuggestionsEnabled)
                    .put("autocorrect", settings.mAutoCorrectEnabled)
                    .put("preview", settings.mKeyPreviewPopupOn)
                    .put("hints", settings.mShowsHints)
                    .put("sound", settings.mSoundOn)
                    .put("vibration", settings.mVibrateOn)
                    .put("double_space", settings.mUseDoubleSpacePeriod)
                    .put("delete_swipe", settings.mDeleteSwipeEnabled)
                    .put("space_horizontal", settings.mSpaceSwipeHorizontal.name)
                    .put("space_vertical", settings.mSpaceSwipeVertical.name)
                    .put("clipboard", settings.mClipboardHistoryEnabled)
                val json = org.json.JSONObject().put("auto", words.mWillAutoCorrect)
                    .put("models", NativeObadhFeatures.modelsReady)
                    .put("editor", ime.currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString().orEmpty())
                    .put("controls", controls)
                    .put("labels", org.json.JSONArray((0 until words.size()).map { words.getLabel(it) }))
                    .put("clips", helium314.keyboard.latin.database.ClipboardDao.getInstance(context)?.count() ?: -1)
                    .put("clip_hashes", org.json.JSONArray(helium314.keyboard.latin.database.ClipboardDao.getInstance(context)?.getAll().orEmpty().map { clip ->
                        java.security.MessageDigest.getInstance("SHA-256").digest(clip.text.orEmpty().toByteArray()).joinToString("") { "%02x".format(it) }
                    }))
                    .put("words", org.json.JSONArray((0 until words.size()).map { words.getWord(it) }))
                    .put("emojis", org.json.JSONArray((0 until words.size()).filter { words.getInfo(it).isEmoji }.map { words.getWord(it) }))
                android.util.Log.i("ObadhProbeState", json.toString())
            }
            "pick" -> {
                val words = ime.obadhSuggestionsForTests
                val index = intent.getIntExtra("index", 0)
                if (index in 0 until words.size()) ime.pickSuggestionManually(words.getInfo(index))
            }
            "search_panel" -> ime.launchEmojiSearch()
            "clipboard" -> {
                val clip = android.content.ClipData.newPlainText("Integration test", intent.getStringExtra("text").orEmpty())
                if (intent.getBooleanExtra("sensitive", false)) clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
            }
            "selection" -> {
                val start = intent.getIntExtra("position", 0)
                ime.currentInputConnection?.setSelection(start, start)
            }
            "emoji" -> {
                val results = NativeObadhFeatures.emojiSearch(intent.getStringExtra("query").orEmpty())
                android.util.Log.i("ObadhProbeEmoji", org.json.JSONArray(results).toString())
            }
            "key" -> ime.onEvent(Event.createSoftwareKeypressEvent(intent.getIntExtra("code", 0), 0, -1, -1, false))
            "type" -> {
                val text = intent.getStringExtra("text").orEmpty()
                val interval = intent.getLongExtra("interval", 70)
                val pending = goAsync()
                val durations = mutableListOf<Long>()
                fun type(index: Int) {
                    if (index == text.length) {
                        durations.sort()
                        if (durations.isNotEmpty()) android.util.Log.i("ObadhProbe", "${durations.size} editor events p50=${durations[durations.size/2]}us p95=${durations[(durations.size*95/100).coerceAtMost(durations.lastIndex)]}us max=${durations.last()}us models=${NativeObadhFeatures.modelsReady}")
                        pending.finish()
                        return
                    }
                    val started = System.nanoTime()
                    ime.onEvent(Event.createSoftwareKeypressEvent(text[index].code, 0, -1, -1, false))
                    durations += (System.nanoTime() - started) / 1000
                    handler.postDelayed({ type(index + 1) }, interval)
                }
                type(0)
            }
        }
    }
}
