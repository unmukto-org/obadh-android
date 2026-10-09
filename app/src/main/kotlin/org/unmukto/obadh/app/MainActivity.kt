package org.unmukto.obadh.app

import android.content.pm.ApplicationInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import org.unmukto.obadh.settings.KeyboardPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableObadhEdgeToEdge()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val screen = if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) "Preferences"
            else if (debuggable) intent.getStringExtra("screen") else null
        setContent { ObadhTheme { RootScreen(startScreen = screen) } }
    }
}

@Composable
fun RootScreen(startScreen: String?) {
    val context = LocalContext.current
    val prefs = remember { KeyboardPreferences(context) }
    val keyboard by rememberKeyboardState()
    var finished by rememberSaveable {
        mutableStateOf(startScreen != "setup" && (prefs.setupCompleted || startScreen != null))
    }
    var route by rememberSaveable {
        mutableStateOf(AppScreen.entries.firstOrNull { it.name.equals(startScreen, true) } ?: AppScreen.Settings)
    }
    val screenState = rememberSaveableStateHolder()
    if (!finished) {
        OnboardingScreen(keyboard, prefs) {
            prefs.onboardingStep = null
            prefs.setupCompleted = true
            finished = true
        }
        return
    }
    val back = { route = AppScreen.Settings }
    BackHandler(enabled = route != AppScreen.Settings, onBack = back)
    Crossfade(targetState = route, label = "settings page") { current ->
        screenState.SaveableStateProvider(current) {
            when (current) {
                AppScreen.Settings -> SettingsScreen(keyboard) { route = it }
                AppScreen.Appearance -> AppearanceScreen(prefs, back)
                AppScreen.Privacy -> PrivacyScreen(back)
                AppScreen.About -> AboutScreen(back)
                AppScreen.Shortcuts -> ShortcutsScreen(prefs, back)
                AppScreen.Clipboard -> ClipboardScreen(prefs, back)
                else -> KeyboardOptionsScreen(current, prefs, back)
            }
        }
    }
}
