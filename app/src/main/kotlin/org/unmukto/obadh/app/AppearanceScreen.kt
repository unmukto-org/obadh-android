package org.unmukto.obadh.app

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import org.unmukto.obadh.settings.KeyboardPhoto
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(KeyboardPhoto.exists(context)) }
    var photoRevision by remember { mutableIntStateOf(0) }
    val photoPreview by produceState<android.graphics.Bitmap?>(null, theme, photoRevision) {
        value = if (theme == "photo") KeyboardPhoto.preview(context) else null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            saving = true
            scope.launch {
                val saved = KeyboardPhoto.save(context, uri)
                saving = false
                if (saved) { theme = "photo"; photo = true; photoRevision++ }
                snackbar.showSnackbar(if (saved) "Keyboard photo saved" else "Couldn't read this image. Choose another photo.")
            }
        }
    }
    val night = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val palette = ObadhColors.palette(theme, night)
    val nativeColors = remember(theme, night, borders) {
        if (theme == "dynamic" && Build.VERSION.SDK_INT >= 31)
            ObadhColors.create(context, theme, helium314.keyboard.keyboard.KeyboardTheme.STYLE_ROUNDED, borders, night)
        else null
    }
    val background = nativeColors?.get(helium314.keyboard.latin.common.ColorType.MAIN_BACKGROUND)?.let(::Color) ?: Color(palette.background)
    val keys = nativeColors?.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)?.let(::Color) ?: Color(palette.keys)
    val text = nativeColors?.get(helium314.keyboard.latin.common.ColorType.KEY_TEXT)?.let(::Color) ?: Color(palette.text)
    SettingsScaffold("Theme", onBack, snackbar = snackbar) {
        Box(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(background)) {
        photoPreview?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxWidth().padding(16.dp),
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
        }
        ChoiceRow("Appearance", listOf("Follow system", "Light", "Dark"), mode) { mode = it; prefs.themeMode = it }
        val names = ObadhColors.names.filter { (it != "dynamic" || Build.VERSION.SDK_INT >= 31) && (it != "photo" || photo) }
        val labels = names.map { when (it) { "default" -> "Default"; "dynamic" -> "System colors"; else -> it.replaceFirstChar(Char::uppercase) } }
        ChoiceRow("Color palette", labels, names.indexOf(theme).coerceAtLeast(0), enabled = !saving) { theme = names[it]; prefs.keyboardTheme = theme }
        ToggleRow("Key borders", borders, "Filled keys with rounded corners") { borders = it; prefs.keyBorders = it }
        PreferenceHeading("Your photo")
        PreferenceItem(content = { Text(if (saving) "Saving photo…" else if (photo) "Change keyboard photo" else "Choose keyboard photo") },
            supportingContent = { Text("One resized image stays on this device. Your original photo is unchanged.") },
            onClick = if (saving) null else ({ picker.launch("image/*") }))
        if (photo) PreferenceItem(content = { Text("Remove keyboard photo") }, onClick = if (saving) null else ({
            saving = true
            scope.launch {
                val removed = KeyboardPhoto.remove(context)
                saving = false
                if (removed) { photo = false; if (theme == "photo") theme = "default" }
                snackbar.showSnackbar(if (removed) "Keyboard photo removed" else "Couldn't remove photo. Try again.")
            }
        }))
        PreferenceNote("One theme applies to Bangla, English, symbols, emoji, clipboard and keyboard tools. System colors follow your wallpaper on Android 12 and later.")
    }
}
