package org.unmukto.obadh

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.unmukto.obadh.emoji.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Runs against the REAL artifacts (synced by scripts/sync-models.sh); skipped if absent. */
class EmojiStoresTest {
    private fun map(name: String): ByteBuffer {
        val f = File("src/main/assets/ObadhModels/emoji/$name")
        assumeTrue("run scripts/sync-models.sh first", f.exists())
        return RandomAccessFile(f, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
    }

    private fun suggestions() = BanglaEmojiSuggestionStore.decode(map("emoji-bn.bin"))!!
    private fun catalog() = EmojiDataStore.decode(map("emoji.bin"))!!
    private fun banglaSearch() = BanglaEmojiSearchStore.decode(map("emoji-bn-search.bin"))!!

    @Test fun curatedWordsResolveToTheirEmoji() {
        val s = suggestions()
        assertEquals("❤️", s.emojis("ভালোবাসা").first())
        assertEquals(listOf("🏆"), s.emojis("ট্রফি"))
        assertTrue("🎂" in s.emojis("জন্মদিন"))
        assertTrue("⚽" in s.emojis("ফুটবল"))
    }

    @Test fun missAndNormalizationBehaveExactly() {
        val s = suggestions()
        assertTrue(s.emojis("এইটাশব্দনা").isEmpty())
        assertTrue(s.emojis("").isEmpty())
        assertEquals("❤️", s.emojis("ভালো‌বাসা").first())
    }

    @Test fun neverMoreThanThreeAndNoSkinToneVariants() {
        val s = suggestions()
        for (w in listOf("ভালোবাসা", "হাসি", "হাত", "মানুষ", "ফুল")) {
            val e = s.emojis(w)
            assertTrue("$w exceeded 3", e.size <= 3)
            assertTrue(e.none { x -> x.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF } })
            assertEquals(e.size, e.toSet().size)
        }
    }

    @Test fun catalogLoadsAndSearchesEnglish() {
        val c = catalog()
        assertTrue(c.items.size > 3_500)
        assertEquals("❤️", c.search("red heart", 5).first().emoji)
        assertTrue(c.search("smil", 10).isNotEmpty())
        assertTrue(c.search("grinning", 10).any { it.emoji == "😀" })
        assertTrue(c.search("", 10).isEmpty())
        assertTrue(c.search("   ", 10).isEmpty())
    }

    @Test fun skinTonesGroupBehindTheBaseAndStayOutOfBrowsing() {
        val c = catalog()
        val wave = c.item("👋")!!
        val options = c.variantOptions(wave)
        assertEquals("👋", options.first().emoji)
        assertTrue(options.size >= 6)
        assertTrue(c.item("👋🏽")!!.emoji in options.map { it.emoji })
        assertTrue(c.items(EmojiCategory.SMILEYS).none { it.emoji == "👋🏽" })
        assertTrue(c.search("waving hand", 10).none { it.emoji == "👋🏽" })
        assertTrue(c.search("waving hand medium skin tone", 10).any { it.emoji == "👋🏽" })
    }

    @Test fun banglaSearchExactPrefixMissAndFuzzy() {
        val s = banglaSearch()
        assertTrue(s.search("হাসি", 20).isNotEmpty())
        assertTrue(s.search("হাস", 20).isNotEmpty())
        assertTrue(s.search("এইটাশব্দনা", 20).isEmpty())
        assertTrue(s.search("", 20).isEmpty())
        assertEquals(s.search("হাসি", 20), s.search("হা‌সি", 20))
        assertTrue("fuzzy should recover a close typo", s.search("হাশি", 20).isNotEmpty())
        assertTrue(s.search("এইটাখুবইঅদ্ভুতগিবারিশ", 20).isEmpty())
    }

    @Test fun recentsAreMruAndEvictByDecayedScore() {
        val mem = HashMap<String, String>()
        val kv = object : EmojiKeyValueStore {
            override fun getString(key: String) = mem[key]
            override fun putString(key: String, value: String) { mem[key] = value }
        }
        var t = 0L
        val r = EmojiRecentStore(kv, limit = 3, now = { t })
        listOf("a", "b", "b", "b", "c").forEach { t += 1000; r.record(it) }
        assertEquals(listOf("c", "b", "a"), r.load())
        t += 1000; r.record("d") // full: the least valuable (a, used once) leaves, not the oldest favourite
        assertEquals(listOf("d", "c", "b"), r.load())
        assertFalse("a" in r.load())
        t += 1000; r.record("b")
        assertEquals("b", r.load().first())
    }

    @Test fun variantPreferencesRoundTripAndClearOnBase() {
        val mem = HashMap<String, String>()
        val s = EmojiVariantPreferenceStore(object : EmojiKeyValueStore {
            override fun getString(key: String) = mem[key]
            override fun putString(key: String, value: String) { mem[key] = value }
        })
        s.record("👋", "👋🏽")
        assertEquals("👋🏽", s.preferred("👋"))
        s.record("👋", "👋")
        assertNull(s.preferred("👋"))
    }
}
