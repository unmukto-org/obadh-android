package org.unmukto.obadh.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.unmukto.obadh.settings.KeyboardPreferences
import org.unmukto.obadh.settings.KeyboardState

private enum class Step { Welcome, Setup, Done }

/** Opens the screen where Obadh is turned on. Android has a public intent for it, unlike iOS. */
fun openKeyboardSettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun showKeyboardPicker(context: Context) =
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

/**
 * First-run setup. Two things Android needs, asked once: is Obadh turned on, and is it the
 * current keyboard. The step survives the process, because walking to Settings can kill the app.
 */
@Composable
fun OnboardingScreen(state: KeyboardState, prefs: KeyboardPreferences, startStep: String? = null, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by remember {
        mutableStateOf(
            (startStep ?: prefs.onboardingStep)?.let { s -> Step.entries.firstOrNull { it.name.equals(s, true) } } ?: Step.Welcome,
        )
    }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    fun advance(next: Step) {
        // Persist before animating: the next thing the user does is leave for Settings.
        prefs.onboardingStep = next.name
        step = next
    }

    // The user just came back having finished. Let the check land before moving on, so the
    // confirmation is seen rather than inferred.
    LaunchedEffect(step, state.ready) {
        if (step == Step.Setup && state.ready) {
            delay(900)
            advance(Step.Done)
        }
    }

    BrandBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(tween(450)) + slideInVertically(tween(450)) { 18 }) togetherWith
                            (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -14 })
                    },
                    label = "step",
                ) { current ->
                    Column(
                        Modifier.widthIn(max = 460.dp).padding(horizontal = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (current) {
                            Step.Welcome -> Welcome(revealed)
                            Step.Setup -> Setup(state)
                            Step.Done -> Done(state)
                        }
                    }
                }
            }
            Column(
                Modifier.widthIn(max = 460.dp).padding(horizontal = 30.dp).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (step) {
                    Step.Welcome -> PrimaryButton("Get Started", { advance(Step.Setup) })
                    Step.Setup -> when {
                        state.ready -> PrimaryButton("Continue", { advance(Step.Done) })
                        !state.enabled -> {
                            PrimaryButton("Open Keyboard Settings", { openKeyboardSettings(context) })
                            SecondaryButton("Not now") { advance(Step.Done) }
                        }
                        else -> {
                            PrimaryButton("Choose Obadh", { showKeyboardPicker(context) })
                            SecondaryButton("Not now") { advance(Step.Done) }
                        }
                    }
                    Step.Done -> PrimaryButton("Done", onFinish)
                }
            }
        }
    }
}

@Composable
private fun Welcome(revealed: Boolean) {
    val dark = isSystemInDarkTheme()
    BrandMark(modifier = Modifier.reveal(0, revealed))
    Text(
        "Obadh",
        Modifier.padding(top = 34.dp).reveal(1, revealed),
        style = TextStyle(brush = Brand.wordmark(dark), fontSize = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    )
    Text(
        "ভাষা হোক আরও উন্মুক্ত",
        Modifier.padding(top = 10.dp).reveal(2, revealed),
        color = (if (dark) Brand.TealLight else Brand.Deep).copy(alpha = 0.9f),
        fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Setup(state: KeyboardState) {
    val phase = if (!state.enabled) SetupPhase.Enable else SetupPhase.Choose
    Title(
        when {
            state.ready -> "Obadh is ready"
            !state.enabled -> "Turn Obadh on"
            else -> "Make Obadh your\nkeyboard"
        },
    )
    Spacer(Modifier.height(26.dp))
    // Recreated per phase so the diagram restarts on the instruction that now applies.
    key(phase) { SetupWalkthrough(phase) }
    Spacer(Modifier.height(20.dp))
    AnimatedContent(targetState = state, label = "setupNote") { s ->
        if (s.ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Brand.Success)
                Spacer(Modifier.width(8.dp))
                Text("Obadh is your keyboard", color = Brand.Success, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                if (!s.enabled) "Android asks you to confirm. Obadh never uses the network."
                else "Tap Obadh in the list that opens.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Done(state: KeyboardState) {
    if (state.ready) {
        Halo(tint = Brand.Success) { Icon(Icons.Default.Check, null, Modifier.size(46.dp), tint = Brand.Success) }
        Title("You're all set", Modifier.padding(top = 28.dp))
        Message("Swipe the space bar to switch between Bangla and English. The keyboard icon in the navigation bar picks another keyboard.")
    } else {
        Halo(tint = Brand.Teal) { Text("⌨", fontSize = 44.sp, color = Brand.Teal) }
        Title("Ready when you are", Modifier.padding(top = 28.dp))
        Message("Turn Obadh on any time in Settings › System › Languages & input › On-screen keyboard.")
    }
}

@Composable
private fun Halo(tint: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .size(112.dp)
            .shadow(24.dp, CircleShape, ambientColor = tint, spotColor = tint.copy(0.5f))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
            .border(1.dp, tint.copy(0.25f), CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) = Text(
    text, modifier,
    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, textAlign = TextAlign.Center),
    color = MaterialTheme.colorScheme.onBackground,
)

@Composable
private fun Message(text: String) = Text(
    text, Modifier.padding(top = 14.dp),
    fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
)
