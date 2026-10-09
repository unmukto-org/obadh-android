package org.unmukto.obadh.app

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.obadh.ObadhColors
import org.unmukto.obadh.settings.KeyboardPreferences

/** Previews share the renderer's palette. Typing and dictionary work do not run in settings. */
@Composable
internal fun AppearanceScreen(prefs: KeyboardPreferences, onBack: () -> Unit) {
    var theme by rememberSaveable { mutableStateOf(prefs.keyboardTheme) }
    var mode by rememberSaveable { mutableIntStateOf(prefs.themeMode) }
    var borders by rememberSaveable { mutableStateOf(prefs.keyBorders) }
    val night = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val palette = ObadhColors.palette(theme, night)
    val colors = if (theme == "dynamic" && Build.VERSION.SDK_INT >= 31) {
        val context = LocalContext.current
        if (night) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else null
    val background = colors?.surface ?: Color(palette.background)
    val keys = if (colors != null) colors.surfaceContainerHighest else Color(palette.keys)
    val text = colors?.onSurface ?: Color(palette.text)
    SettingsScaffold("Theme", onBack) {
        Column(Modifier.padding(16.dp).fillMaxWidth().background(background, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    row.forEach { letter ->
                        Box(Modifier.weight(1f).height(36.dp).background(if (borders) keys else Color.Transparent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text(letter.toString(), color = text, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(36.dp).background(keys, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text("বাংলা · English", color = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ChoiceRow("Appearance", listOf("Follow system", "Light", "Dark"), mode) { mode = it; prefs.themeMode = it }
        val names = if (Build.VERSION.SDK_INT >= 31) ObadhColors.names else ObadhColors.names.filterNot { it == "dynamic" }
        val labels = names.map { when (it) { "default" -> "Default"; "dynamic" -> "System colors"; else -> it.replaceFirstChar(Char::uppercase) } }
        ChoiceRow("Color palette", labels, names.indexOf(theme).coerceAtLeast(0)) { theme = names[it]; prefs.keyboardTheme = theme }
        ToggleRow("Key borders", borders, "Filled keys with rounded corners") { borders = it; prefs.keyBorders = it }
        PreferenceNote("One theme applies to Bangla, English, symbols, emoji, clipboard and keyboard tools. System colors follow your wallpaper on Android 12 and later.")
    }
}
