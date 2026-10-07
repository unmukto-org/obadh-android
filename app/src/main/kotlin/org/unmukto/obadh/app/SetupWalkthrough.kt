package org.unmukto.obadh.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** What the user is about to do in system settings. */
enum class SetupPhase { Enable, Choose }

/**
 * An animated diagram of the two things Android needs: turn Obadh on in the keyboard list,
 * then choose it as the current keyboard. Drawn in Obadh's own idiom rather than as a pixel
 * copy of Settings: it is a diagram, not a forgery, and a fake that drifts from the real UI
 * is worse than none. Holds its final frame when animations are off, which is the useful one.
 */
@Composable
fun SetupWalkthrough(phase: SetupPhase, modifier: Modifier = Modifier) {
    val still = rememberReduceMotion()
    // (stage, how long to hold it). Stage drives every derived flag, so the diagram is one number.
    var stage by remember(phase) { mutableIntStateOf(if (still) 3 else 0) }
    LaunchedEffect(phase, still) {
        if (still) { stage = 3; return@LaunchedEffect }
        val script = listOf(0 to 1100, 1 to 850, 2 to 1300, 3 to 1900)
        while (true) {
            for ((s, hold) in script) { stage = s; delay(hold.toLong()) }
            stage = 0
            delay(500)
        }
    }

    val shape = RoundedCornerShape(20.dp)
    val description = when (phase) {
        SetupPhase.Enable -> "In keyboard settings, turn on Obadh."
        SetupPhase.Choose -> "In the keyboard picker, choose Obadh."
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(1.dp, Brand.Teal.copy(0.18f), shape)
            .semantics { contentDescription = description },
    ) {
        Text(
            if (phase == SetupPhase.Enable) "On-screen keyboard" else "Choose input method",
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(14.dp).clearAndSetSemantics {}) {
            val tapping = stage == 1
            val done = stage >= 2
            when (phase) {
                SetupPhase.Enable -> {
                    DiagramRow("Gboard", "G", Color(0xFF4285F4)) { MiniToggle(on = true) }
                    DiagramRule()
                    DiagramRow("Obadh", "অ", Brand.Deep, highlighted = tapping, ripple = tapping) { MiniToggle(on = done) }
                    DiagramRule()
                    DiagramRow("Samsung Keyboard", "S", Color(0xFF6E7B86)) { MiniToggle(on = false) }
                }
                SetupPhase.Choose -> {
                    DiagramRow("Gboard", "G", Color(0xFF4285F4)) { MiniRadio(selected = !done) }
                    DiagramRule()
                    DiagramRow("Obadh", "অ", Brand.Deep, highlighted = tapping, ripple = tapping) { MiniRadio(selected = done) }
                    DiagramRule()
                    DiagramRow("Set up input methods", "⚙", Color(0xFF6E7B86)) { }
                }
            }
        }
    }
}

@Composable
private fun DiagramRow(
    title: String,
    glyph: String,
    tint: Color,
    highlighted: Boolean = false,
    ripple: Boolean = false,
    accessory: @Composable () -> Unit,
) {
    val glow by animateColorAsState(if (highlighted) Brand.Teal.copy(0.20f) else Color.Transparent, tween(250), label = "row")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(glow)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(tint),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
        Box(contentAlignment = Alignment.Center) {
            if (ripple) TapRipple()
            accessory()
        }
    }
}

@Composable
private fun DiagramRule() =
    HorizontalDivider(Modifier.padding(start = 40.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun MiniToggle(on: Boolean) {
    val track by animateColorAsState(
        if (on) Brand.Success else Color.Gray.copy(0.35f), tween(300), label = "track",
    )
    val x by animateDpAsState(if (on) 14.dp else 0.dp, tween(300), label = "thumb")
    Box(Modifier.size(30.dp, 18.dp).clip(CircleShape).background(track).padding(2.dp)) {
        Box(Modifier.offset(x = x).size(14.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun MiniRadio(selected: Boolean) {
    val ring by animateColorAsState(if (selected) Brand.Teal else Color.Gray.copy(0.5f), tween(300), label = "ring")
    Box(Modifier.size(18.dp).border(2.dp, ring, CircleShape).padding(4.dp)) {
        if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(Brand.Teal))
    }
}

/** The tap itself: a fingertip-sized disc that swells on the control and fades. */
@Composable
private fun TapRipple() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Box(
        Modifier
            .size(30.dp)
            .graphicsLayer { val s = 0.5f + 0.6f * t.value; scaleX = s; scaleY = s; alpha = 1f - 0.2f * t.value }
            .background(Brand.Teal.copy(0.22f), CircleShape)
            .border(1.5.dp, Brand.Teal.copy(0.75f), CircleShape),
    )
}
