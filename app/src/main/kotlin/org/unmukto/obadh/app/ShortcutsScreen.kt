package org.unmukto.obadh.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.unmukto.obadh.settings.TextShortcut
import org.unmukto.obadh.settings.TextShortcuts

/** Add, edit and remove personal text shortcuts (trigger → expansion). */
@Composable
fun ShortcutsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TextShortcuts(context) }
    var items by remember { mutableStateOf(store.all()) }
    // null = closed, "" = adding, otherwise the trigger being edited.
    var editing by remember { mutableStateOf<String?>(null) }

    DetailScaffold("Text Shortcuts", onBack) {
        Spacer(Modifier.height(8.dp))
        Section(footer = "Type the shortcut, then space or return. It works in Bangla and English mode, and for symbols such as @@. Shortcuts are case-sensitive and stay on this device.") {
            Text(
                "Add Shortcut",
                Modifier.fillMaxWidth().clickable { editing = "" }.padding(horizontal = 16.dp, vertical = 14.dp),
                fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
            )
        }
        if (items.isNotEmpty()) {
            Section {
                items.forEachIndexed { i, item ->
                    if (i > 0) RowRule()
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = item.trigger }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.trigger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            item.expansion.replace('\n', ' '), Modifier.weight(1f), fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    editing?.let { original ->
        val existing = items.firstOrNull { it.trigger == original }
        var trigger by remember(original) { mutableStateOf(existing?.trigger ?: "") }
        var expansion by remember(original) { mutableStateOf(existing?.expansion ?: "") }
        val valid = trigger.isNotEmpty() && trigger.none { it.isWhitespace() } && expansion.isNotEmpty()
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (existing == null) "New Shortcut" else "Edit Shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        trigger, { trigger = it.filterNot { c -> c.isWhitespace() }.take(TextShortcuts.MAX_TRIGGER) },
                        label = { Text("Shortcut") }, placeholder = { Text("@@") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                    OutlinedTextField(
                        expansion, { expansion = it.take(TextShortcuts.MAX_EXPANSION) },
                        label = { Text("Phrase") }, placeholder = { Text("name@example.com") }, maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    if (store.put(TextShortcut(trigger, expansion), replacing = existing?.trigger)) items = store.all()
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (existing != null) {
                        TextButton(onClick = { store.remove(existing.trigger); items = store.all(); editing = null }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
