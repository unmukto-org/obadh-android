package org.unmukto.obadh.settings

import android.content.Context
import android.content.SharedPreferences

/** Small state in plain SharedPreferences: no database, same as iOS UserDefaults. */
class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val nativeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        NativePreferences.send(context.applicationContext)
    }
    init { prefs.registerOnSharedPreferenceChangeListener(nativeListener) }

    /** 0 off, 1 light, 2 medium, 3 strong. Falls back to the old on/off switch when never set. */
    var hapticStrength: Int
        get() = if (prefs.contains(KEY_HAPTIC_STRENGTH)) prefs.getInt(KEY_HAPTIC_STRENGTH, Haptics.DEFAULT)
        else if (prefs.getBoolean(KEY_HAPTICS, true)) Haptics.DEFAULT else Haptics.OFF
        set(v) = prefs.edit().putInt(KEY_HAPTIC_STRENGTH, v.coerceIn(0, 3)).apply()

    /** Ordinary typo auto-insert is opt-in (exact loanwords are not governed by it). */
    var autoInsertCorrections: Boolean
        get() = prefs.getBoolean(KEY_AUTO_INSERT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_INSERT, v).apply()

    private fun flag(key: String) = prefs.getBoolean(key, true)
    private fun setFlag(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    // Gestures and special features. Each is on by default and has its own switch in the app.
    var spaceTrackpad: Boolean get() = flag("f_trackpad"); set(v) = setFlag("f_trackpad", v)
    /** Off by default: it takes the volume buttons while the keyboard is open. */
    var volumeKeyCursor: Boolean
        get() = prefs.getBoolean("f_volume_cursor", false)
        set(v) = prefs.edit().putBoolean("f_volume_cursor", v).apply()
    var doubleSpacePeriod: Boolean get() = flag("f_double_space"); set(v) = setFlag("f_double_space", v)
    var spaceLeavesSymbols: Boolean get() = flag("f_space_leaves_symbols"); set(v) = setFlag("f_space_leaves_symbols", v)
    var spaceSwipeLanguage: Boolean get() = flag("f_space_swipe_lang"); set(v) = setFlag("f_space_swipe_lang", v)
    var swipeToDelete: Boolean get() = flag("f_swipe_delete"); set(v) = setFlag("f_swipe_delete", v)
    var longPressSymbols: Boolean get() = flag("f_long_press"); set(v) = setFlag("f_long_press", v)
    var clipboardKeys: Boolean get() = flag("f_clip_keys"); set(v) = setFlag("f_clip_keys", v)
    var keyCallout: Boolean get() = flag("f_callout"); set(v) = setFlag("f_callout", v)
    var autoCapitalize: Boolean get() = flag("f_auto_caps"); set(v) = setFlag("f_auto_caps", v)
    var autoPairs: Boolean get() = flag("f_pairs"); set(v) = setFlag("f_pairs", v)
    var textShortcutsEnabled: Boolean get() = flag("f_shortcuts"); set(v) = setFlag("f_shortcuts", v)
    var smartFields: Boolean get() = flag("f_smart_fields"); set(v) = setFlag("f_smart_fields", v)
    var returnActionKey: Boolean get() = flag("f_return_action"); set(v) = setFlag("f_return_action", v)
    var englishSpelling: Boolean get() = flag("f_spelling"); set(v) = setFlag("f_spelling", v)
    var englishAutoCorrection: Boolean get() = flag("english_autocorrect"); set(v) = setFlag("english_autocorrect", v)

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
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_AUTO_INSERT = "auto_insert"
        const val KEY_SOUND = "key_sound"
        const val KEY_CLIP = "clipboard_history"
        const val KEY_ENGLISH = "english_mode"
        const val KEY_SETUP = "setup_completed"
        const val KEY_STEP = "onboarding_step"
        const val KEY_EMOJI_BN = "emoji_search_bangla"
    }
}
