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

    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP, false)
        set(v) = prefs.edit().putBoolean(KEY_SETUP, v).apply()

    private companion object {
        const val NAME = "obadh_prefs"
        const val KEY_HAPTICS = "haptics"
        const val KEY_AUTO_INSERT = "auto_insert"
        const val KEY_SOUND = "key_sound"
        const val KEY_SETUP = "setup_completed"
    }
}
