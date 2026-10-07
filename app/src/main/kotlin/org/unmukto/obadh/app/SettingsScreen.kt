package org.unmukto.obadh.app

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.unmukto.obadh.settings.Haptics
import org.unmukto.obadh.settings.KeyboardPreferences
import org.unmukto.obadh.settings.TextShortcuts
import org.unmukto.obadh.settings.KeyboardState

/**
 * The app after setup: preferences, and nothing else. Setup guidance reappears only when
 * the keyboard is actually missing, and it tracks the live state.
 */
@Composable
fun SettingsScreen(state: KeyboardState, prefs: KeyboardPreferences, onOpenPrivacy: () -> Unit, onOpenAbout: () -> Unit, onOpenShortcuts: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    var haptics by remember { mutableStateOf(prefs.hapticStrength) }
    var keySound by remember { mutableStateOf(prefs.keySoundEnabled) }
    var clipboard by remember { mutableStateOf(prefs.clipboardHistoryEnabled) }
    var autoInsert by remember { mutableStateOf(prefs.autoInsertCorrections) }
    val shortcutCount = remember { TextShortcuts(context).all().size }
    var emojiBangla by remember { mutableStateOf(prefs.emojiSearchBangla) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .widthIn(max = 620.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Obadh",
            Modifier.padding(start = 4.dp, top = 28.dp, bottom = 18.dp),
            style = TextStyle(brush = Brand.wordmark(dark), fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        )

        // The only nag in the app, and it is load-bearing: without it the keyboard is simply
        // gone and nothing else on this screen means anything.
        AnimatedVisibility(visible = !state.ready) {
            Column {
                Card {
                    val enabling = !state.enabled
                    Row(
                        Modifier
                            .clickable { if (enabling) openKeyboardSettings(context) else showKeyboardPicker(context) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Brand.Warning)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (enabling) "Obadh isn't turned on" else "Obadh isn't your current keyboard",
                                fontSize = 16.sp,
                            )
                            Text(
                                if (enabling) "Open keyboard settings to turn it on" else "Tap to choose it",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Chevron()
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }

        Section(header = "Keyboard") {
            SegmentedRow(
                "Haptic Strength", listOf("Off", "Light", "Medium", "Strong"), selected = haptics,
                subtitle = "How firmly the phone vibrates on each key press. Try Light for a soft tick or Strong for a firm one.",
            ) {
                haptics = it
                prefs.hapticStrength = it
                Haptics.play(view, strength = it)
            }
            RowRule()
            ToggleRow(
                "Typing Sound", keySound,
                "Plays your phone's key click as you type. It follows your volume and the \"Touch sounds\" setting.",
            ) {
                keySound = it
                prefs.keySoundEnabled = it
                if (it) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            }
            RowRule()
            ToggleRow(
                "Clipboard History", clipboard,
                "Keeps what you copy so you can paste it again from the clipboard icon. Stays on this device; passwords are never saved.",
            ) { clipboard = it; prefs.clipboardHistoryEnabled = it }
        }

        Section(header = "Gestures") {
            PrefToggle(
                "Space Bar Trackpad", "Hold the space bar, then slide to move the cursor. Slide up or down to change line.",
                prefs.spaceTrackpad,
            ) { prefs.spaceTrackpad = it }
            RowRule()
            PrefToggle(
                "Swipe Space to Switch Language",
                "Swipe left or right on the space bar to switch between Bangla and English. Holding first still moves the cursor.",
                prefs.spaceSwipeLanguage,
            ) { prefs.spaceSwipeLanguage = it }
            RowRule()
            PrefToggle(
                "Volume Keys Move Cursor",
                "While the keyboard is open, volume up moves the cursor right and volume down moves it left, one character at a time. Off by default because it replaces the volume control while typing.",
                prefs.volumeKeyCursor,
            ) { prefs.volumeKeyCursor = it }
            RowRule()
            PrefToggle(
                "Swipe Backspace to Delete",
                "Slide left from the backspace key to select words, then lift to delete them. Slide further to take more words.",
                prefs.swipeToDelete,
            ) { prefs.swipeToDelete = it }
            RowRule()
            PrefToggle(
                "Hold Keys for Symbols",
                "Hold a key to type its small second character, like \u09E7 or 1 on Q, or @ on A. Lift on the key to type it.",
                prefs.longPressSymbols,
            ) { prefs.longPressSymbols = it }
            RowRule()
            PrefToggle(
                "Hold X, C, V to Cut, Copy, Paste",
                "Hold X to cut, C to copy and V to paste. With nothing selected, cut and copy take the whole text. Hold X or C and slide left or right to pick words first.",
                prefs.clipboardKeys,
            ) { prefs.clipboardKeys = it }
            RowRule()
            PrefToggle(
                "Key Preview Bubble", "Shows a large bubble of the letter above your finger as you press a key.",
                prefs.keyCallout,
            ) { prefs.keyCallout = it }
        }

        Section(header = "Smart Typing") {
            PrefToggle(
                "Smart Fields",
                "Adapts to the field: e-mail, web and password fields open in English with @, / and .com keys, and number or phone fields open on the number pad.",
                prefs.smartFields,
            ) { prefs.smartFields = it }
            RowRule()
            PrefToggle(
                "Auto-Capitalize (English)",
                "Starts sentences with a capital letter, like \"Hello. How are you\", and capitalises names in name fields.",
                prefs.autoCapitalize,
            ) { prefs.autoCapitalize = it }
            RowRule()
            PrefToggle(
                "Spelling Suggestions (English)",
                "Suggests fixes from your phone's spell checker, like \"teh\" to \"the\". Needs a spell checker turned on in system settings.",
                prefs.englishSpelling,
            ) { prefs.englishSpelling = it }
            RowRule()
            PrefToggle(
                "Double Space for Full Stop",
                "Tap space twice quickly to end a sentence: it types । in Bangla and a full stop in English, like \"Hello. \".",
                prefs.doubleSpacePeriod,
            ) { prefs.doubleSpacePeriod = it }
            RowRule()
            PrefToggle(
                "Space Returns to Letters",
                "On the numbers and symbols pages, tapping space goes back to the letters, like typing \"(5)\" then space. Off keeps the page open.",
                prefs.spaceLeavesSymbols,
            ) { prefs.spaceLeavesSymbols = it }
            RowRule()
            PrefToggle(
                "Pair Brackets and Quotes",
                "Typing ( also adds ) and puts the cursor between them. Typing the closing one steps over it.",
                prefs.autoPairs,
            ) { prefs.autoPairs = it }
            RowRule()
            PrefToggle(
                "Return Key Actions",
                "The return key shows what it will do, such as a magnifier for Search or an arrow for Next, and finishes single-line fields.",
                prefs.returnActionKey,
            ) { prefs.returnActionKey = it }
        }

        Section(header = "Text Shortcuts") {
            PrefToggle(
                "Expand Shortcuts",
                "Type a shortcut then space, and it becomes the full text. For example @@ can become your e-mail address.",
                prefs.textShortcutsEnabled,
            ) { prefs.textShortcutsEnabled = it }
            RowRule()
            NavRow("Shortcuts", value = shortcutCount.toString(), onClick = onOpenShortcuts)
        }

        Section(header = "Autocorrect") {
            ToggleRow(
                "Auto-Insert Corrections", autoInsert,
                "Space replaces a likely typo with the right word, like \"bondu\" to \u09AC\u09A8\u09CD\u09A7\u09C1. Exact English loanwords always use their Bangla spelling, even when this is off. Tap the quoted spelling to keep what you typed.",
            ) { autoInsert = it; prefs.autoInsertCorrections = it }
        }

        Section(header = "Emoji") {
            SegmentedRow(
                "Search Language", listOf("English", "বাংলা"), selected = if (emojiBangla) 1 else 0,
                subtitle = "The language the emoji search starts in. You can switch it inside the emoji panel.",
            ) {
                emojiBangla = it == 1
                prefs.emojiSearchBangla = emojiBangla
            }
        }

        Section(header = "About") {
            NavRow("Privacy", onClick = onOpenPrivacy)
            RowRule()
            NavRow("Version", value = "${AppBuildInfo.version} (${AppBuildInfo.build})", onClick = onOpenAbout)
        }
    }
}

// ----------------------------------------------------------- grouped-list pieces

@Composable
fun Section(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 22.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                Modifier.padding(start = 16.dp, bottom = 7.dp),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.4.sp,
            )
        }
        Card(content = content)
        if (footer != null) {
            Text(
                footer,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface),
    content = content,
)

@Composable
fun RowRule() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun Chevron() = Icon(
    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
)

/** A switch row that keeps its own state and writes through [onChange]. */
@Composable
fun PrefToggle(title: String, subtitle: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    ToggleRow(title, on, subtitle) { on = it; onChange(it) }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(
                    subtitle, Modifier.padding(top = 2.dp, end = 12.dp),
                    fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (dark) Brand.Teal else Brand.Deep,
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
fun NavRow(title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        if (value != null) {
            Text(value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Chevron()
    }
}

/** A two-or-more way picker in iOS's segmented idiom, in the brand colours. */
@Composable
fun SegmentedRow(title: String, options: List<String>, selected: Int, subtitle: String? = null, onSelect: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(2.dp),
        ) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                val bg by animateColorAsState(
                    if (active) (if (dark) Brand.Teal else Brand.Deep) else Color.Transparent, tween(200), label = "seg",
                )
                Text(
                    label,
                    Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = if (active) (if (dark) Brand.Charcoal else Color.White) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (subtitle != null) {
        Text(
            subtitle, Modifier.padding(top = 6.dp),
            fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}
