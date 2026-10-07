package org.unmukto.obadh.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.unmukto.obadh.R
import org.unmukto.obadh.settings.ClipboardHistory
import org.unmukto.obadh.settings.LearnedWordStore
import org.unmukto.obadh.settings.PersonalAutosuggestStore

/** Inline-titled leaf screen with a back arrow, like a pushed iOS navigation page. */
@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            IconButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 620.dp)
                .padding(horizontal = 16.dp).padding(bottom = 32.dp),
            content = content,
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }

    DetailScaffold("About", onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val shape = RoundedCornerShape(84.dp * 0.2237f)
            Image(
                painterResource(R.drawable.brand_icon), null,
                Modifier.size(84.dp).shadow(10.dp, shape).clip(shape),
            )
            Spacer(Modifier.height(10.dp))
            Text("Obadh", style = TextStyle(brush = Brand.wordmark(dark), fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text("ভাষা হোক আরও উন্মুক্ত", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section {
            ValueRow("Version", AppBuildInfo.version)
            RowRule()
            ValueRow("Engine Version", AppBuildInfo.engineVersion)
            RowRule()
            ValueRow("Build", AppBuildInfo.build)
            RowRule()
            ValueRow("Commit", AppBuildInfo.gitRevision.ifEmpty { "—" }, mono = true)
            RowRule()
            ValueRow("Built", AppBuildInfo.buildTime.ifEmpty { "—" }, mono = true)
        }
        Section {
            Text(
                if (copied) "✓  Copied" else "Copy Build Details",
                Modifier.fillMaxWidth()
                    .clickable(enabled = !copied) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Obadh build", AppBuildInfo.summary))
                        copied = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        Text(
            value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    var clipCleared by remember { mutableStateOf(false) }

    DetailScaffold("Privacy", onBack) {
        Spacer(Modifier.height(8.dp))
        Section {
            Paragraph(
                "On your device",
                "Transliteration, autocorrect, suggestions and emoji search all run inside the keyboard. Obadh asks for no network permission at all, so nothing you type can leave your device.",
            )
            RowRule()
            Paragraph(
                "What Obadh remembers",
                "The emoji you use most recently, and the words you type, kept so suggestions improve as you write. Both are stored in Obadh's own private storage on this device.",
            )
            RowRule()
            Paragraph(
                "Android's warning",
                "Android shows a generic message when you turn on any keyboard, because every keyboard can see what is typed into it. That is true of Obadh; it simply never sends it anywhere.",
            )
        }
        Section(
            footer = if (cleared) "Learned words cleared. Obadh starts fresh the next time you type."
            else "Removes the words Obadh has learned from your typing. Suggestions from the built-in dictionary are unaffected.",
        ) {
            Text(
                "Clear Learned Words",
                Modifier.fillMaxWidth().clickable { confirming = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                fontSize = 16.sp, color = MaterialTheme.colorScheme.error,
            )
        }
        Section(
            footer = if (clipCleared) "Clipboard history cleared."
            else "Removes the copied items the keyboard's clipboard panel has kept.",
        ) {
            Text(
                "Clear Clipboard History",
                Modifier.fillMaxWidth().clickable { ClipboardHistory(context).clear(); clipCleared = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                fontSize = 16.sp, color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Clear learned words?") },
            text = { Text("Obadh will forget everything it has learned from your typing. This can't be undone.") },
            confirmButton = {
                TextButton({
                    PersonalAutosuggestStore(context).clear()
                    LearnedWordStore(context).clear()
                    cleared = true
                    confirming = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Paragraph(title: String, body: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
