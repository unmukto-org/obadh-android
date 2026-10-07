package org.unmukto.obadh.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class TextShortcut(val trigger: String, val expansion: String)

/**
 * Personal text shortcuts: typing the trigger (`@@`, `eml`) then space or return replaces it with
 * the expansion (an email address, a phone number, a sign-off). Private SharedPreferences, never
 * sent anywhere. The app edits the list and the keyboard reads it, in the same process, so a
 * change applies at once.
 */
class TextShortcuts(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("obadh_shortcuts", Context.MODE_PRIVATE)
    private var items: List<TextShortcut> = load()
    // Held in a field: SharedPreferences keeps listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> items = load() }

    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    @Synchronized
    fun all(): List<TextShortcut> = items

    @Synchronized
    fun lookup(trigger: String): String? = items.firstOrNull { it.trigger == trigger }?.expansion

    /** Add, or replace the shortcut with the same trigger (or [replacing], when editing). */
    @Synchronized
    fun put(shortcut: TextShortcut, replacing: String? = null): Boolean {
        val trigger = shortcut.trigger
        if (trigger.isEmpty() || trigger.any { it.isWhitespace() } || trigger.length > MAX_TRIGGER) return false
        if (shortcut.expansion.isEmpty() || shortcut.expansion.length > MAX_EXPANSION) return false
        val next = items.filterNot { it.trigger == trigger || it.trigger == replacing }.toMutableList()
        next.add(0, shortcut)
        if (next.size > MAX_ITEMS) return false
        save(next)
        return true
    }

    @Synchronized
    fun remove(trigger: String) = save(items.filterNot { it.trigger == trigger })

    private fun save(list: List<TextShortcut>) {
        items = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.trigger).put("e", it.expansion)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<TextShortcut> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getJSONObject(it).let { o -> TextShortcut(o.getString("t"), o.getString("e")) } }
        } catch (_: Exception) { emptyList() }
    }

    companion object {
        private const val KEY = "items"
        const val MAX_ITEMS = 100
        const val MAX_TRIGGER = 32
        const val MAX_EXPANSION = 500
    }
}
