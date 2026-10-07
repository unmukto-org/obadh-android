package org.unmukto.obadh.settings

import android.content.Context
import org.json.JSONArray

/**
 * Text the user copied while Obadh was running, newest first. Local only (private
 * SharedPreferences, never sent anywhere), bounded, and clearable. Android offers no way to
 * read the system clipboard's past, so history starts when the keyboard first sees a copy.
 * Pinned items stay at the top, never age out and survive [clearUnpinned].
 */
data class ClipEntry(val text: String, val pinned: Boolean)

class ClipboardHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("obadh_clipboard", Context.MODE_PRIVATE)
    private var items: MutableList<String> = load(KEY)
    private var pins: MutableList<String> = load(KEY_PINNED)

    /** Pinned first (newest pin first), then the history, newest first. */
    @Synchronized
    fun all(): List<ClipEntry> = pins.map { ClipEntry(it, true) } + items.map { ClipEntry(it, false) }

    /** Newest first; an existing identical entry moves to the top instead of duplicating. */
    @Synchronized
    fun add(text: String): Boolean {
        val t = text.trim('\u0000')
        if (t.isBlank() || t.length > MAX_CHARS) return false
        if (t in pins || items.firstOrNull() == t) return false
        items.remove(t)
        items.add(0, t)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save()
        return true
    }

    @Synchronized
    fun remove(text: String) {
        val a = items.remove(text)
        val b = pins.remove(text)
        if (a || b) save()
    }

    /** Pin an item (moves it to the top of the pins) or unpin it (back to the top of the history). */
    @Synchronized
    fun togglePin(text: String) {
        if (pins.remove(text)) {
            items.remove(text)
            items.add(0, text)
            while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        } else if (items.remove(text)) {
            pins.add(0, text)
            while (pins.size > MAX_PINS) pins.removeAt(pins.size - 1)
        } else return
        save()
    }

    /** The panel's Clear: pins are kept. */
    @Synchronized
    fun clearUnpinned() {
        items.clear()
        save()
    }

    /** Everything, pins too (the privacy control). */
    @Synchronized
    fun clear() {
        items.clear()
        pins.clear()
        prefs.edit().remove(KEY).remove(KEY_PINNED).apply()
    }

    private fun load(key: String): MutableList<String> {
        val raw = prefs.getString(key, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { mutableListOf() }
    }

    private fun save() {
        prefs.edit().putString(KEY, JSONArray(items).toString())
            .putString(KEY_PINNED, JSONArray(pins).toString()).apply()
    }

    companion object {
        private const val KEY = "items"
        private const val KEY_PINNED = "pinned"
        const val MAX_ITEMS = 30
        const val MAX_PINS = 20
        const val MAX_CHARS = 5000
    }
}
