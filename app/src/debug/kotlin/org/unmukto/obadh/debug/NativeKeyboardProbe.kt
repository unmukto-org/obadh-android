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
        val field = EditText(this).apply {
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
