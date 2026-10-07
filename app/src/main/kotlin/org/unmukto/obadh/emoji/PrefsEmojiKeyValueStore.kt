package org.unmukto.obadh.emoji

import android.content.Context

class PrefsEmojiKeyValueStore(context: Context) : EmojiKeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("obadh_emoji", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
