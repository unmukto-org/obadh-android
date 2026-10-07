package org.unmukto.obadh.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.unmukto.obadh.settings.KeyboardInstallState
import org.unmukto.obadh.settings.KeyboardPreferences

/**
 * The containing app: a first-run flow that asks its questions once (enable,
 * then select), then a settings screen that asks nothing. No text input anywhere.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Root() } } }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(KeyboardInstallState.isEnabled(context)) }
    var selected by remember { mutableStateOf(KeyboardInstallState.isSelected(context)) }
    // The user leaves for system settings and comes back; re-read state on resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = KeyboardInstallState.isEnabled(context)
        selected = KeyboardInstallState.isSelected(context)
    }
    if (enabled && selected) SettingsScreen() else SetupWalkthrough(enabled)
}

@Composable
private fun SetupWalkthrough(enabled: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("অবাধ", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("Type Roman, get Bangla live.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        if (!enabled) {
            Text("Step 1 of 2: turn Obadh on", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }) {
                Text("Open keyboard settings")
            }
        } else {
            Text("Step 2 of 2: make it your keyboard", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }) { Text("Choose Obadh") }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { KeyboardPreferences(context) }
    var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
    var autoInsert by remember { mutableStateOf(prefs.autoInsertCorrections) }
    var emojiBangla by remember { mutableStateOf(prefs.emojiSearchBangla) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Obadh", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        ToggleRow("Haptic feedback", "Vibrate on key press.", haptics) { haptics = it; prefs.hapticsEnabled = it }
        ToggleRow(
            "Auto-insert corrections",
            "On space, replace a typo only when a strict confidence gate passes. Exact English loanwords are always converted.",
            autoInsert,
        ) { autoInsert = it; prefs.autoInsertCorrections = it }
        ToggleRow(
            "Search emoji in Bangla",
            "The emoji search opens in Bangla (type Roman, it is converted). You can still switch inside the search bar.",
            emojiBangla,
        ) { emojiBangla = it; prefs.emojiSearchBangla = it }
        Spacer(Modifier.height(24.dp))
        Text(
            "Obadh never uses the network. Everything is computed on your device.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
