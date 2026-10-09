package org.unmukto.obadh.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.unmukto.obadh.R
import org.unmukto.obadh.settings.KeyboardPreferences
import org.unmukto.obadh.settings.KeyboardState

fun openKeyboardSettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun showKeyboardPicker(context: Context) =
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

/** One setup screen with the two Android actions, replacing the decorative welcome flow. */
@Composable
fun OnboardingScreen(state: KeyboardState, prefs: KeyboardPreferences, onFinish: () -> Unit) {
    val context = LocalContext.current
    var sample by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { prefs.onboardingStep = "Setup" }
    SettingsScaffold("Set up Obadh") {
        Column(Modifier.padding(24.dp)) {
            Image(painterResource(R.drawable.brand_icon), null, Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Your Bangla keyboard", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text("Type with English letters to write in Bangla. Everything works on your device.", style = MaterialTheme.typography.bodyLarge)
        }
        PreferenceItem(
            content = { Text("1. Enable Obadh") },
            supportingContent = { Text(if (state.enabled) "Enabled" else "Turn on Obadh in Android keyboard settings") },
            trailingContent = { if (state.enabled) Symbol(R.drawable.ic_check, "Enabled") },
        )
        if (!state.enabled) {
            FilledTonalButton(onClick = { openKeyboardSettings(context) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Open keyboard settings") }
        }
        PreferenceItem(
            content = { Text("2. Choose Obadh") },
            supportingContent = { Text(if (state.selected) "Selected" else "Select Obadh as your current keyboard") },
            trailingContent = { if (state.selected) Symbol(R.drawable.ic_check, "Selected") },
        )
        if (!state.selected) {
            FilledTonalButton(onClick = { showKeyboardPicker(context) }, enabled = state.enabled, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Choose keyboard") }
        }
        PreferenceHeading("Optional")
        SwipeTypingPreference()
        if (state.ready) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(sample, { sample = it }, label = { Text("Try your keyboard") },
                    placeholder = { Text("Type ami to write আমি") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
                Button(onClick = onFinish) { Text("Open settings") }
            }
        } else {
            PreferenceNote("Android shows a warning for every keyboard because keyboards can read typed text. Obadh processes typing on your device. Network access is used only when you request the optional swipe-typing download.")
            TextButton(onClick = onFinish, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Set up later") }
        }
    }
}
