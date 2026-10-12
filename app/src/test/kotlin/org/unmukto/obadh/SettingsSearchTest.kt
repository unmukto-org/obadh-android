package org.unmukto.obadh

import org.junit.Assert.*
import org.junit.Test
import org.unmukto.obadh.app.AppScreen
import org.unmukto.obadh.app.SettingsIndex
import org.unmukto.obadh.app.searchSettings
import java.io.File

class SettingsSearchTest {
    private fun titles(q: String) = searchSettings(q).map { it.title }

    @Test fun blankQueryFindsNothing() {
        assertTrue(titles("").isEmpty())
        assertTrue(titles("   ").isEmpty())
    }

    @Test fun titleMatchesRankFirst() {
        assertEquals("Vibration strength", titles("vibration").first())
        assertEquals("Clipboard", titles("clipboard").first())
    }

    @Test fun matchIsCaseInsensitiveAndAllWordsMustHit() {
        assertTrue("Number row" in titles("NUMBER ROW"))
        assertTrue(titles("number zebra").isEmpty())
    }

    @Test fun keywordsFindSettingsByOtherNames() {
        assertTrue("Vibration strength" in titles("haptic"))
        assertTrue("Gesture cursor control" in titles("trackpad"))
    }

    @Test fun sameTitleResultsAreTellApartByPath() {
        val hits = searchSettings("auto-correction")
        assertEquals(setOf("Corrections & suggestions › Bangla", "Corrections & suggestions › English"),
            hits.filter { it.title == "Auto-correction" }.map { it.path }.toSet())
    }

    @Test fun mediaSettingsOpenTheirOwnActivity() {
        assertNull(searchSettings("klipy").first { it.title == "Online GIFs & stickers" }.screen)
    }

    /** A toggle or choice row without an index entry would be unfindable; fail loudly instead. */
    @Test fun everyToggleAndChoiceRowIsIndexed() {
        val root = File("src/main/kotlin/org/unmukto/obadh")
        val sources = File(root, "app").listFiles { f -> f.extension == "kt" }.orEmpty().toList() +
            File(root, "media/MediaSettingsActivity.kt")
        val row = Regex("""(?:PrefToggle|ChoiceRow|ToggleRow)\(\s*"([^"]+)"""")
        val indexed = SettingsIndex.all.map { it.title }.toSet()
        val missing = sources.flatMap { f -> row.findAll(f.readText()).map { it.groupValues[1] } }.toSet() - indexed
        assertTrue("Add to SettingsIndex: $missing", missing.isEmpty())
    }

    @Test fun everyScreenIsReachableFromTheIndex() {
        val covered = SettingsIndex.all.mapNotNull { it.screen }.toSet()
        val missing = AppScreen.entries.filter { it != AppScreen.Settings } - covered
        assertTrue("Screens without an index entry: $missing", missing.isEmpty())
    }
}
