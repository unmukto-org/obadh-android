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
            putString(Settings.PREF_THEME_STYLE, helium314.keyboard.keyboard.KeyboardTheme.STYLE_ROUNDED)
            val theme = p.getString("keyboard_theme", "default").takeIf { it in helium314.keyboard.latin.obadh.ObadhColors.names } ?: "default"
            putLong("obadh.photo_revision", p.getLong("keyboard_photo_revision", 0))
            putString(Settings.PREF_THEME_COLORS, "obadh_$theme")
            putString(Settings.PREF_THEME_COLORS_NIGHT, "obadh_$theme")
            putString("obadh.theme_mode", arrayOf("system", "light", "dark")[p.getInt("keyboard_theme_mode", 0).coerceIn(0, 2)])
            putBoolean(Settings.PREF_THEME_KEY_BORDERS, flag("keyboard_key_borders"))
            putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, flag("keyboard_language_key"))
            putString(Settings.PREF_LANGUAGE_SWITCH_KEY, "internal")
            putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, true)
            putBoolean(Settings.PREF_SHOW_EMOJI_KEY, flag("keyboard_emoji_key", false))
            putBoolean(Settings.PREF_SHOW_NUMBER_ROW, flag("keyboard_number_row", false))
            putBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, true)
            putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, flag("learn_words"))
            putString("obadh.tablet_layout", arrayOf("automatic", "full", "split")[p.getInt("tablet_layout", 0).coerceIn(0, 2)])
            for (index in 0..3) {
                putFloat(helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, index, 2),
                    floatArrayOf(.85f, 1f, 1.15f)[p.getInt("keyboard_height", 1).coerceIn(0, 2)])
            }
            for (index in 0..3) putFloat(helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, index, 2), 1f)
            for (index in 0..7) putFloat(helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, index, 3), 1f)
            putString(Settings.PREF_TOOLBAR_KEYS, "EMOJI:true|CLIPBOARD:true|SETTINGS:true|ONE_HANDED:true|FLOATING:true|SPLIT:true|DPAD:true|UNDO:true|REDO:true|SELECT_ALL:true|COPY:true|CUT:true|PASTE:true|INCOGNITO:true|VOICE:false|BACKGROUND_GATHERING:false")
            putString(Settings.PREF_PINNED_TOOLBAR_KEYS, "EMOJI:true|CLIPBOARD:true")
            putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, true)
            putBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, true)
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
        val native = context.prefs()
        if (values.getLong("obadh.photo_revision") != native.getLong("obadh.photo_revision", 0)) Settings.clearCachedBackgroundImages()
        val editor = native.edit()
        for (key in values.keySet()) {
            @Suppress("DEPRECATION")
            when (val value = values.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
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
