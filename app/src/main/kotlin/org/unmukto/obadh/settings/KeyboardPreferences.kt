package org.unmukto.obadh.settings

import android.content.Context
import android.content.SharedPreferences

/** Small state in plain SharedPreferences: no database, same as iOS UserDefaults. */
class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(v) = prefs.edit().putBoolean(KEY_HAPTICS, v).apply()

    /** Ordinary typo auto-insert is opt-in (exact loanwords are not governed by it). */
    var autoInsertCorrections: Boolean
        get() = prefs.getBoolean(KEY_AUTO_INSERT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_INSERT, v).apply()

    var keySoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    /** Type plain English instead of transliterating to Bangla. Flipped from the ribbon's tools. */
    var englishMode: Boolean
        get() = prefs.getBoolean(KEY_ENGLISH, false)
        set(v) = prefs.edit().putBoolean(KEY_ENGLISH, v).apply()

    /** Remember what is copied, for the clipboard panel. Local only; off stops collecting. */
    var clipboardHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIP, true)
        set(v) = prefs.edit().putBoolean(KEY_CLIP, v).apply()

    /** Emoji search language when the panel's search opens. The in-bar chip toggles it per session. */
    var emojiSearchBangla: Boolean
        get() = prefs.getBoolean(KEY_EMOJI_BN, false)
        set(v) = prefs.edit().putBoolean(KEY_EMOJI_BN, v).apply()

    /** Which onboarding step the user reached; the app can be killed while they are in Settings. */
    var onboardingStep: String?
        get() = prefs.getString(KEY_STEP, null)
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_STEP) else putString(KEY_STEP, v) }.apply()

    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP, false)
        set(v) = prefs.edit().putBoolean(KEY_SETUP, v).apply()

    private companion object {
        const val NAME = "obadh_prefs"
        const val KEY_HAPTICS = "haptics"
        const val KEY_AUTO_INSERT = "auto_insert"
        const val KEY_SOUND = "key_sound"
        const val KEY_CLIP = "clipboard_history"
        const val KEY_ENGLISH = "english_mode"
        const val KEY_SETUP = "setup_completed"
        const val KEY_STEP = "onboarding_step"
        const val KEY_EMOJI_BN = "emoji_search_bangla"
    }
}
