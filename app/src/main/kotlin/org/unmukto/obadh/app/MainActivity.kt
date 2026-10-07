package org.unmukto.obadh.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import org.unmukto.obadh.settings.KeyboardPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds can jump straight to a screen or onboarding step for review, since
        // leaf screens sit behind taps: `am start ... --es screen about --es step setup`.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val screen = if (debuggable) intent.getStringExtra("screen") else null
        val step = if (debuggable) intent.getStringExtra("step") else null
        setContent { ObadhTheme { RootScreen(startScreen = screen, startStep = step) } }
    }
}

private enum class Route { Settings, Privacy, About, Shortcuts }

@Composable
fun RootScreen(startScreen: String?, startStep: String?) {
    val context = LocalContext.current
    val prefs = remember { KeyboardPreferences(context) }
    val keyboard by rememberKeyboardState()
    var finished by remember { mutableStateOf(prefs.setupCompleted || startScreen != null) }
    var route by rememberSaveable {
        mutableStateOf(Route.entries.firstOrNull { it.name.equals(startScreen, true) } ?: Route.Settings)
    }

    if (!finished) {
        OnboardingScreen(keyboard, prefs, startStep) {
            prefs.onboardingStep = null
            prefs.setupCompleted = true
            finished = true
        }
        return
    }

    BackHandler(enabled = route != Route.Settings) { route = Route.Settings }
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState == Route.Settings) {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            } else {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            }
        },
        label = "route",
    ) { current ->
        when (current) {
            Route.Settings -> SettingsScreen(keyboard, prefs, { route = Route.Privacy }, { route = Route.About }, { route = Route.Shortcuts })
            Route.Privacy -> PrivacyScreen { route = Route.Settings }
            Route.About -> AboutScreen { route = Route.Settings }
            Route.Shortcuts -> ShortcutsScreen { route = Route.Settings }
        }
    }
}
