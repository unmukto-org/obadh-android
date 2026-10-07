package org.unmukto.obadh.settings

import android.content.Context
import org.json.JSONArray

/**
 * Text the user copied while Obadh was running, newest first. Local only (private
 * SharedPreferences, never sent anywhere), bounded, and clearable. Android offers no way to
 * read the system clipboard's past, so history starts when the keyboard first sees a copy.
 */
class ClipboardHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("obadh_clipboard", Context.MODE_PRIVATE)
    private var items: MutableList<String> = load()

    @Synchronized
    fun all(): List<String> = items.toList()

    /** Newest first; an existing identical entry moves to the top instead of duplicating. */
    @Synchronized
    fun add(text: String): Boolean {
        val t = text.trim('\u0000')
        if (t.isBlank() || t.length > MAX_CHARS) return false
        if (items.firstOrNull() == t) return false
        items.remove(t)
        items.add(0, t)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save()
        return true
    }

    @Synchronized
    fun remove(text: String) {
        if (items.remove(text)) save()
    }

    @Synchronized
    fun clear() {
        items.clear()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): MutableList<String> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { mutableListOf() }
    }

    private fun save() {
        prefs.edit().putString(KEY, JSONArray(items).toString()).apply()
    }

    companion object {
        private const val KEY = "items"
        const val MAX_ITEMS = 30
        const val MAX_CHARS = 5000
    }
}
