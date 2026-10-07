package org.unmukto.obadh.settings

import android.content.Context

/**
 * Words the user explicitly kept by tapping their own quoted spelling. These are
 * protected from ordinary auto-insert forever. Bounded and local.
 */
class LearnedWordStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("obadh_learned", Context.MODE_PRIVATE)
    private var cache: MutableSet<String> = prefs.getStringSet(KEY, emptySet())!!.toMutableSet()

    @Synchronized
    fun isProtected(word: String): Boolean = word in cache

    @Synchronized
    fun protect(word: String) {
        if (word.isEmpty() || word in cache) return
        if (cache.size >= MAX) cache.remove(cache.first())
        cache.add(word)
        prefs.edit().putStringSet(KEY, cache.toSet()).apply()
    }

    @Synchronized
    fun clear() {
        cache.clear()
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "protected"
        const val MAX = 2000
    }
}
