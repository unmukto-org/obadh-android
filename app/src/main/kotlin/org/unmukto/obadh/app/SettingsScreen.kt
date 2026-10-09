package org.unmukto.obadh.app

import android.view.SoundEffectConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.unmukto.obadh.R
import org.unmukto.obadh.settings.Haptics
import org.unmukto.obadh.settings.KeyboardPreferences
import org.unmukto.obadh.settings.KeyboardState

internal enum class AppScreen(val title: String) {
    Settings("Obadh"), Preferences("Preferences"), Correction("Text correction"),
    Gestures("Gestures"), Clipboard("Clipboard"), Emoji("Emoji"),
    Shortcuts("Text shortcuts"), Privacy("Privacy"), About("About"), Advanced("Advanced"),
}

/** A Gboard-style category index. Power-user controls are in the overflow menu. */
@Composable
internal fun SettingsScreen(state: KeyboardState, onOpen: (AppScreen) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    SettingsScaffold("Obadh", actions = {
        Box {
            IconButton(onClick = { menu = true }) { Symbol(R.drawable.ic_more_vert, "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Advanced settings") }, onClick = {
                    menu = false
                    onOpen(AppScreen.Advanced)
                })
            }
        }
    }) {
        if (!state.ready) {
            PreferenceItem(
                content = { Text(if (!state.enabled) "Enable Obadh" else "Choose Obadh") },
                supportingContent = { Text(if (!state.enabled) "Turn on the keyboard in Android settings" else "Set Obadh as your current keyboard") },
                leadingContent = { Symbol(R.drawable.ic_keyboard) },
                onClick = {
                    if (!state.enabled) openKeyboardSettings(context) else showKeyboardPicker(context)
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
        }
        CategoryRow("Preferences", "Sound, vibration and key previews", R.drawable.ic_settings) { onOpen(AppScreen.Preferences) }
        CategoryRow("Text correction", "Autocorrection, spelling and punctuation", R.drawable.ic_spellcheck) { onOpen(AppScreen.Correction) }
        CategoryRow("Gestures", "Switch languages, move the cursor and delete", R.drawable.ic_swipe) { onOpen(AppScreen.Gestures) }
        CategoryRow("Clipboard", "Clipboard history and stored items", R.drawable.ic_content_paste) { onOpen(AppScreen.Clipboard) }
        CategoryRow("Text shortcuts", "Your shortcuts and phrases", R.drawable.ic_short_text) { onOpen(AppScreen.Shortcuts) }
        CategoryRow("Emoji", "Default search language", R.drawable.ic_sentiment_satisfied) { onOpen(AppScreen.Emoji) }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        CategoryRow("Privacy", "How your typing data is stored", R.drawable.ic_shield) { onOpen(AppScreen.Privacy) }
        CategoryRow("About", "Obadh ${AppBuildInfo.version}", R.drawable.ic_info) { onOpen(AppScreen.About) }
    }
}

@Composable
fun Symbol(@DrawableRes resource: Int, description: String? = null) {
    Icon(painterResource(resource), description, Modifier.size(24.dp))
}

@Composable
private fun CategoryRow(title: String, summary: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    PreferenceItem(
        content = { Text(title) }, supportingContent = { Text(summary) },
        leadingContent = { Symbol(icon) }, onClick = onClick,
    )
}

@Composable
fun PreferenceHeading(title: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** One accessible switch target, including the label, summary and full-width touch area. */
@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    PreferenceItem(
        content = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
fun PrefToggle(title: String, subtitle: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    ToggleRow(title, value, subtitle) { value = it; onChange(it) }
}

/** Standard single-choice preference dialog, rather than a custom segmented control. */
@Composable
fun ChoiceRow(title: String, options: List<String>, initial: Int, onSelect: (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(initial.coerceIn(options.indices)) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    PreferenceItem(
        content = { Text(title) }, supportingContent = { Text(options[selected]) },
        onClick = { choosing = true },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false }, title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup()) {
                    options.forEachIndexed { index, option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = selected == index, role = Role.RadioButton, onClick = {
                                    selected = index
                                    onSelect(index)
                                    choosing = false
                                }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == index, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun KeyboardOptionsScreen(screen: AppScreen, prefs: KeyboardPreferences, onBack: () -> Unit) {
    val view = LocalView.current
    SettingsScaffold(screen.title, onBack) {
        when (screen) {
            AppScreen.Preferences -> {
                PreferenceHeading("Key press")
                ChoiceRow("Vibration strength", listOf("Off", "Light", "Medium", "Strong"), prefs.hapticStrength) {
                    prefs.hapticStrength = it
                    Haptics.play(view, strength = it)
                }
                PrefToggle("Sound on key press", "Uses Android touch sounds", prefs.keySoundEnabled) {
                    prefs.keySoundEnabled = it
                    if (it) view.playSoundEffect(SoundEffectConstants.CLICK)
                }
                PrefToggle("Pop-up on key press", "Show the letter above your finger", prefs.keyCallout) { prefs.keyCallout = it }
                PrefToggle("Secondary symbols", "Show symbol hints on letter keys", prefs.longPressSymbols) { prefs.longPressSymbols = it }
            }
            AppScreen.Correction -> {
                PreferenceHeading("Bangla")
                PrefToggle("Auto-correction", "Replace likely typos when you press space", prefs.autoInsertCorrections) { prefs.autoInsertCorrections = it }
                PreferenceNote("Tap the quoted spelling to keep your original word. English loanwords still use their Bangla spelling when auto-correction is off.")
                PreferenceHeading("English")
                PrefToggle("Auto-correction", "Correct English spelling when you press space", prefs.englishAutoCorrection) { prefs.englishAutoCorrection = it }
                PrefToggle("Spelling suggestions", "Show suggestions from the built-in English dictionary", prefs.englishSpelling) { prefs.englishSpelling = it }
                PrefToggle("Auto-capitalization", "Capitalize the first word of a sentence", prefs.autoCapitalize) { prefs.autoCapitalize = it }
                PreferenceHeading("Punctuation")
                PrefToggle("Double-space full stop", "Insert । in Bangla or a period in English", prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
            }
            AppScreen.Gestures -> {
                SwipeTypingPreference()
                PrefToggle("Swipe to switch language", "Swipe the space bar for Bangla or English", prefs.spaceSwipeLanguage) { prefs.spaceSwipeLanguage = it }
                PrefToggle("Gesture cursor control", "Swipe up on the space bar to open the touchpad", prefs.spaceTrackpad) { prefs.spaceTrackpad = it }
                PrefToggle("Gesture delete", "Slide left from backspace to select and delete words", prefs.swipeToDelete) { prefs.swipeToDelete = it }
            }
            AppScreen.Emoji -> {
                ChoiceRow("Search language", listOf("English", "বাংলা"), if (prefs.emojiSearchBangla) 1 else 0) { prefs.emojiSearchBangla = it == 1 }
                PreferenceNote("Both languages work in emoji search. This chooses the starting keyboard language; swipe the space bar to switch.")
            }
            AppScreen.Advanced -> {
                PreferenceHeading("Additional controls")
                PrefToggle("Volume key cursor control", "Volume buttons move the cursor instead of changing volume while typing", prefs.volumeKeyCursor) { prefs.volumeKeyCursor = it }
                PrefToggle("Cut, copy and paste shortcuts", "Hold X, C or V to choose Cut, Copy or Paste. Use Android selection or the keyboard toolbar to select text.", prefs.clipboardKeys) { prefs.clipboardKeys = it }
                PrefToggle("Pair brackets and quotes", "Insert both characters and place the cursor between them", prefs.autoPairs) { prefs.autoPairs = it }
                PreferenceHeading("Input behavior")
                PrefToggle("Adapt to input fields", "Type English in email and web address fields. Passwords always stay literal; Android chooses number and phone layouts.", prefs.smartFields) { prefs.smartFields = it }
                PrefToggle("Return key actions", "Show Search, Next or Done when the app requests it", prefs.returnActionKey) { prefs.returnActionKey = it }
                PrefToggle("Space returns to letters", "Leave the symbols page when you press space", prefs.spaceLeavesSymbols) { prefs.spaceLeavesSymbols = it }
            }
            else -> Unit
        }
    }
}

@Composable
fun PreferenceNote(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Material preference row with natural text height, including at accessibility font sizes. */
@Composable
fun PreferenceItem(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = containerColor, contentColor = contentColor) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge, content)
                if (supportingContent != null) {
                    Spacer(Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides supportingColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, supportingContent)
                    }
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(16.dp))
                trailingContent()
            }
        }
    }
}
