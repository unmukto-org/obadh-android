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
import org.unmukto.obadh.settings.KeyboardPreferences
import org.unmukto.obadh.settings.KeyboardState

/**
 * The app after setup: preferences, and nothing else. Setup guidance reappears only when
 * the keyboard is actually missing, and it tracks the live state.
 */
@Composable
fun SettingsScreen(state: KeyboardState, prefs: KeyboardPreferences, onOpenPrivacy: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
    var keySound by remember { mutableStateOf(prefs.keySoundEnabled) }
    var autoInsert by remember { mutableStateOf(prefs.autoInsertCorrections) }
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

        Section(
            header = "Keyboard",
            footer = "Typing sound uses the system key click, so it follows your phone's volume and \"Touch sounds\" setting.",
        ) {
            ToggleRow("Haptic Feedback", haptics) {
                haptics = it
                prefs.hapticsEnabled = it
                if (it) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            RowRule()
            ToggleRow("Typing Sound", keySound) {
                keySound = it
                prefs.keySoundEnabled = it
                if (it) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            }
        }

        Section(
            header = "Autocorrect",
            footer = "Space inserts likely corrections. Exact English loanwords always use their Bangla spelling, even when this is off. Tap the quoted spelling to keep the literal.",
        ) {
            ToggleRow("Auto-Insert Corrections", autoInsert) { autoInsert = it; prefs.autoInsertCorrections = it }
        }

        Section(header = "Emoji") {
            SegmentedRow("Search Language", listOf("English", "বাংলা"), selected = if (emojiBangla) 1 else 0) {
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

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
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
fun SegmentedRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
}
