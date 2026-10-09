package org.unmukto.obadh.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/** The IME owns upstream preferences. Send values, never rely on cross-process caches. */
object NativePreferences {
    fun snapshot(context: Context): Bundle {
        val p = context.getSharedPreferences("obadh_prefs", Context.MODE_PRIVATE)
        fun flag(key: String, default: Boolean = true) = p.getBoolean(key, default)
        val strength = p.getInt("haptic_strength", if (flag("haptics")) Haptics.DEFAULT else 0).coerceIn(0, 3)
        return Bundle().apply {
            putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
            putBoolean("obadh.auto_insert", flag("auto_insert", false))
            putBoolean("obadh.pairs", flag("f_pairs"))
            putBoolean("obadh.shortcuts_enabled", flag("f_shortcuts"))
            putBoolean("obadh.smart_fields", flag("f_smart_fields"))
            putBoolean("obadh.volume_cursor", flag("f_volume_cursor", false))
            putBoolean("obadh.emoji_bangla", flag("emoji_search_bangla", false))
            putBoolean("obadh.clipboard_keys", flag("f_clip_keys"))
            putBoolean("obadh.return_actions", flag("f_return_action"))
            putString("obadh.shortcuts", context.getSharedPreferences("obadh_shortcuts", Context.MODE_PRIVATE).getString("items", "[]"))
            putBoolean(Settings.PREF_VIBRATE_ON, strength > 0)
            putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, intArrayOf(0, 12, 18, 26)[strength])
            putBoolean(Settings.PREF_SOUND_ON, flag("key_sound", false))
            putBoolean(Settings.PREF_POPUP_ON, flag("f_callout"))
            putBoolean(Settings.PREF_SHOW_HINTS, flag("f_long_press"))
            putBoolean(Settings.PREF_AUTO_CAP, flag("f_auto_caps"))
            putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
            putBoolean("obadh.english_suggestions", flag("f_spelling"))
            putBoolean(Settings.PREF_AUTO_CORRECTION, flag("english_autocorrect"))
            putBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, flag("f_double_space"))
            putBoolean(Settings.PREF_DELETE_SWIPE, flag("f_swipe_delete"))
            putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, if (flag("f_space_swipe_lang")) "SWITCH_LANGUAGE" else "NONE")
            putString(Settings.PREF_SPACE_VERTICAL_SWIPE, if (flag("f_trackpad")) "TOUCHPAD_MODE" else "NONE")
            putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, flag("clipboard_history"))
            putBoolean(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, flag("f_space_leaves_symbols"))
            putBoolean(Settings.PREF_ABC_AFTER_NUMPAD_SPACE, flag("f_space_leaves_symbols"))
        }
    }

    fun send(context: Context) {
        context.sendBroadcast(Intent(context, NativePreferenceReceiver::class.java).putExtra("values", snapshot(context)))
    }

    fun apply(context: Context, values: Bundle) {
        org.unmukto.obadh.keyboard.NativeObadhFeatures.configure(values)
        val editor = context.prefs().edit()
        for (key in values.keySet()) {
            @Suppress("DEPRECATION")
            when (val value = values.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
        org.unmukto.obadh.keyboard.NativeObadhFeatures.refreshSettings()
    }
}

class NativePreferenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getBundleExtra("values")?.let { NativePreferences.apply(context, it) }
    }
}
