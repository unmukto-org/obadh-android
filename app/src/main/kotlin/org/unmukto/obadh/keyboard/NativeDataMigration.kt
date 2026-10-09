package org.unmukto.obadh.keyboard

import android.content.Context
import helium314.keyboard.keyboard.emoji.RecentEmojis
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.utils.prefs
import org.unmukto.obadh.emoji.EmojiRecentStore
import org.unmukto.obadh.emoji.PrefsEmojiKeyValueStore
import org.unmukto.obadh.settings.ClipboardHistory

/** One-time migration keeps existing private clipboard pins and emoji recents in the native UI. */
object NativeDataMigration {
    fun run(context: Context) {
        val prefs = context.prefs()
        if (prefs.getBoolean("obadh.native_data_migrated", false)) return
        val dao = ClipboardDao.getInstance(context) ?: return
        val clipboard = ClipboardHistory(context)
        clipboard.all().asReversed().forEachIndexed { index, item ->
            dao.addClip(System.currentTimeMillis() + index, item.pinned, item.text)
        }
        val oldRecents = EmojiRecentStore(PrefsEmojiKeyValueStore(context)).load()
        RecentEmojis.set((RecentEmojis.get() + oldRecents).distinct().take(39))
        prefs.edit().putBoolean("obadh.native_data_migrated", true).apply()
        clipboard.clear()
    }
}
