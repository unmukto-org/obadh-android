package org.unmukto.obadh.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.unmukto.obadh.settings.KeyboardState

/** Stars per lesson, kept on the device. Not part of keyboard preferences: nothing reads it outside this screen. */
private class LearnProgress(context: Context) {
    private val prefs = context.getSharedPreferences("obadh_learn", Context.MODE_PRIVATE)
    fun stars(id: String) = prefs.getInt("stars.$id", 0)
    fun record(id: String, stars: Int) {
        if (stars > stars(id)) prefs.edit().putInt("stars.$id", stars).apply()
    }
}

/** Writing guide and a small typing game. The game uses the real keyboard, so it teaches the real thing. */
@Composable
internal fun LearnScreen(state: KeyboardState, onBack: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { LearnProgress(context) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var earned by remember { mutableIntStateOf(0) }
    val lesson = playing?.let(LearnContent::lesson)

    BackHandler(enabled = lesson != null) { playing = null }
    if (lesson != null) {
        LessonPlay(lesson, state, onExit = { playing = null }) { stars ->
            progress.record(lesson.id, stars)
            earned++
        }
        return
    }
    SettingsScaffold("Learn to type", onBack = onBack) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Play") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Writing guide") })
        }
        if (tab == 0) PlayTab(progress, earned) { playing = it } else GuideTab()
    }
}

@Composable
private fun PlayTab(progress: LearnProgress, @Suppress("UNUSED_PARAMETER") refresh: Int, onPlay: (String) -> Unit) {
    PreferenceNote("Type the Bangla shown, using the Obadh keyboard. Stuck? Tap Hint to see the keys.")
    LearnContent.lessons.forEach { lesson ->
        val stars = progress.stars(lesson.id)
        PreferenceItem(
            content = { Text(lesson.title) },
            supportingContent = { Text("${lesson.blurb}\n${lesson.challenges.size} to type") },
            trailingContent = { Text(if (stars == 0) "☆☆☆" else "★".repeat(stars) + "☆".repeat(3 - stars), fontSize = 18.sp) },
            onClick = { onPlay(lesson.id) },
        )
    }
}

@Composable
private fun GuideTab() {
    PreferenceNote("The same rules as obadh.unmukto.org/guide. Try them in the Play tab.")
    LearnContent.guide.forEach { section ->
        PreferenceHeading(section.title)
        if (section.intro.isNotEmpty()) PreferenceNote(section.intro)
        section.rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.roman, Modifier.width(112.dp), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                Text(row.bangla, Modifier.width(96.dp), fontSize = 22.sp)
                if (row.note.isNotEmpty()) {
                    Text(row.note, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LessonPlay(lesson: Lesson, state: KeyboardState, onExit: () -> Unit, onFinished: (Int) -> Unit) {
    val context = LocalContext.current
    var index by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    var hints by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    var skipped by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    var typed by rememberSaveable(lesson.id, index) { mutableStateOf("") }
    var hintShown by rememberSaveable(lesson.id, index) { mutableStateOf(false) }
    var correct by remember(lesson.id, index) { mutableStateOf(false) }
    var reported by rememberSaveable(lesson.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val done = index >= lesson.challenges.size
    val challenge = lesson.challenges.getOrNull(index)

    LaunchedEffect(done, index) { if (!done) runCatching { focus.requestFocus() } }
    LaunchedEffect(typed, index) {
        if (challenge != null && isCorrect(typed, challenge)) {
            correct = true
            delay(450)
            index++
        }
    }
    LaunchedEffect(done) {
        if (done && !reported) {
            reported = true
            onFinished(starsFor(hints, skipped))
        }
    }

    SettingsScaffold(lesson.title, onBack = onExit) {
        if (done) {
            val stars = starsFor(hints, skipped)
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("★".repeat(stars) + "☆".repeat(3 - stars), fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (stars == 3) "Perfect" else "Lesson done", style = MaterialTheme.typography.headlineMedium)
                Text("$hints hints, $skipped skipped", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { index = 0; hints = 0; skipped = 0; reported = false }) { Text("Play again") }
                TextButton(onClick = onExit) { Text("Back to lessons") }
            }
            return@SettingsScaffold
        }
        if (!state.ready) {
            PreferenceItem(
                content = { Text("Switch to the Obadh keyboard") },
                supportingContent = { Text("The game only works when Obadh is the active keyboard. Tap to choose it.") },
                onClick = { if (!state.enabled) openKeyboardSettings(context) else showKeyboardPicker(context) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        LinearProgressIndicator(
            progress = { index / lesson.challenges.size.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text("${index + 1} of ${lesson.challenges.size}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
        Text(
            challenge!!.target, Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center, fontSize = 48.sp,
            color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = typed, onValueChange = { typed = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            textStyle = MaterialTheme.typography.headlineSmall,
            label = { Text(if (correct) "Correct" else "Type it here") },
            isError = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        Text(
            if (hintShown) "Type:  ${challenge.roman}" else " ",
            Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (!hintShown) { hintShown = true; hints++ } }, enabled = !hintShown && !correct) { Text("Hint") }
            TextButton(onClick = { skipped++; index++ }, enabled = !correct) { Text("Skip") }
        }
    }
}
