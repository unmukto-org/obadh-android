package org.unmukto.obadh.app

import java.util.Locale

/**
 * One searchable setting. [screen] null means the GIFs & stickers settings activity, which is
 * not part of the in-app navigation. [path] is the breadcrumb shown under the result.
 */
internal data class SettingEntry(
    val title: String,
    val summary: String,
    val path: String,
    val screen: AppScreen?,
    val keywords: String = "",
)

private fun entry(title: String, summary: String, screen: AppScreen?, section: String? = null, keywords: String = "") =
    SettingEntry(title, summary, listOfNotNull(screen?.let { if (it == AppScreen.Advanced) "Advanced settings" else it.title } ?: "GIFs & stickers", section).joinToString(" › "), screen, keywords)

/**
 * Hand-maintained because the screens are composed by hand. SettingsSearchTest fails when a
 * toggle or choice row is added to a screen without being listed here.
 */
internal object SettingsIndex {
    val all: List<SettingEntry> = listOf(
        // Top-level categories.
        entry("Languages", "Bangla and English keyboards", AppScreen.Languages, keywords = "bangla bengali english language"),
        entry("Learn to type", "Writing guide and a typing game", AppScreen.Learn, keywords = "guide tutorial lesson practice game phonetic how to write"),
        entry("Preferences", "Layout and key press", AppScreen.Preferences, keywords = "layout keys"),
        entry("Theme", "Colors, gradients and keyboard photo", AppScreen.Appearance, keywords = "appearance color colour dark light mode gradient photo background wallpaper"),
        entry("Corrections & suggestions", "Auto-correction, spelling and learning", AppScreen.Correction, keywords = "autocorrect typo spell"),
        entry("Glide typing", "Swipe and gesture controls", AppScreen.Gestures, keywords = "swipe gesture"),
        entry("Clipboard", "Clipboard history", AppScreen.Clipboard, keywords = "copy paste"),
        entry("Text shortcuts", "Expand short phrases as you type", AppScreen.Shortcuts, keywords = "snippet expansion abbreviation macro"),
        entry("Emoji", "Emoji search language", AppScreen.Emoji),
        entry("Privacy", "On-device processing and stored data", AppScreen.Privacy, keywords = "data delete security"),
        entry("About", "Version, licenses and acknowledgments", AppScreen.About, keywords = "version build license open source klipy engine"),
        entry("Advanced settings", "Additional controls and input behavior", AppScreen.Advanced, keywords = "more"),

        // Preferences
        entry("Number row", "Show a number row in both languages", AppScreen.Preferences, "Layout", "digits numbers"),
        entry("Language switch key", "Tap the globe to switch between Bangla and English", AppScreen.Preferences, "Layout", "globe"),
        entry("Emoji switch key", "Show a separate emoji key beside the space bar", AppScreen.Preferences, "Layout", "smiley"),
        entry("Keyboard height", "Short, default or tall", AppScreen.Preferences, "Layout", "size resize"),
        entry("Tablet layout", "Automatic, full width or split", AppScreen.Preferences, "Layout", "split thumb"),
        entry("Vibration strength", "Off, light, medium or strong", AppScreen.Preferences, "Key press", "haptic vibrate buzz feedback"),
        entry("Sound on key press", "Uses Android touch sounds", AppScreen.Preferences, "Key press", "click audio"),
        entry("Pop-up on key press", "Show the letter above your finger", AppScreen.Preferences, "Key press", "preview callout"),
        entry("Secondary symbols", "Show symbol hints on letter keys", AppScreen.Preferences, "Key press", "hint long press"),

        // Languages
        entry("Swipe to switch language", "Swipe the space bar for Bangla or English", AppScreen.Languages, keywords = "space bar"),

        // Corrections & suggestions
        entry("Auto-correction", "Replace likely typos when you press space", AppScreen.Correction, "Bangla", "autocorrect bangla"),
        entry("Auto-correction", "Correct English spelling when you press space", AppScreen.Correction, "English", "autocorrect english"),
        entry("Spelling suggestions", "Show suggestions from the built-in English dictionary", AppScreen.Correction, "English", "dictionary"),
        entry("Auto-capitalization", "Capitalize the first word of a sentence", AppScreen.Correction, "English", "capital uppercase"),
        entry("Learn from typing", "Keep learned words on this device in both languages", AppScreen.Correction, "Personalization", "personal dictionary words incognito"),
        entry("Double-space full stop", "Insert । in Bangla or a period in English", AppScreen.Correction, "Punctuation", "period dari"),

        // Glide typing
        entry("Swipe typing", "Slide across letters to write words", AppScreen.Gestures, keywords = "glide download decoder"),
        entry("Gesture cursor control", "Hold the space bar, then slide to move the cursor", AppScreen.Gestures, keywords = "trackpad touchpad move cursor hold"),
        entry("Gesture delete", "Slide left from backspace to select and delete words", AppScreen.Gestures, keywords = "backspace"),

        // Emoji
        entry("Search language", "Starting language for emoji search", AppScreen.Emoji, keywords = "bangla english"),

        // Advanced
        entry("Volume key cursor control", "Volume buttons move the cursor while typing", AppScreen.Advanced, "Additional controls"),
        entry("Cut, copy and paste shortcuts", "Hold X, C or V to choose Cut, Copy or Paste", AppScreen.Advanced, "Additional controls", "clipboard"),
        entry("Pair brackets and quotes", "Insert both characters and place the cursor between them", AppScreen.Advanced, "Additional controls", "parentheses auto close"),
        entry("Adapt to input fields", "Type English in email and web address fields", AppScreen.Advanced, "Input behavior", "smart fields password url email"),
        entry("Return key actions", "Show Search, Next or Done when the app requests it", AppScreen.Advanced, "Input behavior", "enter"),
        entry("Space returns to letters", "Leave the symbols page when you press space", AppScreen.Advanced, "Input behavior", "symbols"),

        // Clipboard
        entry("Clipboard history", "Keep copied text in the keyboard's clipboard panel", AppScreen.Clipboard),
        entry("Delete clipboard history", "Remove all items stored by Obadh", AppScreen.Clipboard, keywords = "clear erase"),

        // Text shortcuts
        entry("Use text shortcuts", "Expand a shortcut when you press space or return", AppScreen.Shortcuts),

        // Theme
        entry("Key borders", "Show borders around keys", AppScreen.Appearance, keywords = "outline"),
        entry("Keyboard photo", "Use your own photo as the keyboard background", AppScreen.Appearance, keywords = "image picture custom"),

        // Privacy
        entry("Delete learned words", "Reset personal suggestions", AppScreen.Privacy, keywords = "clear erase personal dictionary"),

        // GIFs & stickers
        entry("Online GIFs & stickers", "Search KLIPY from the keyboard", null, keywords = "gif sticker klipy media"),
        entry("Clear local recents & thumbnails", "Remove recent GIF and sticker references", null, keywords = "delete cache"),

        // About
        entry("Build details", "Version and build information", AppScreen.About, keywords = "version copy"),
    )
}

/**
 * Every whitespace-separated word of [query] must appear (case-insensitively) in the entry.
 * Title matches rank above path, summary and keyword matches; ties keep index order.
 */
internal fun searchSettings(query: String, entries: List<SettingEntry> = SettingsIndex.all): List<SettingEntry> {
    val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.mapNotNull { e ->
        val title = e.title.lowercase(Locale.ROOT)
        val rest = "${e.path} ${e.summary} ${e.keywords}".lowercase(Locale.ROOT)
        if (words.any { it !in title && it !in rest }) return@mapNotNull null
        val titleWords = title.split(Regex("[^\\p{L}\\p{N}]+"))
        val score = words.sumOf { w ->
            when {
                title.startsWith(w) -> 4
                titleWords.any { it.startsWith(w) } -> 3
                w in title -> 2
                else -> 0
            }
        }
        e to score
    }.sortedByDescending { it.second }.map { it.first }
}
