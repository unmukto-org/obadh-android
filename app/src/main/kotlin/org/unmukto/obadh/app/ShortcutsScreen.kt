package org.unmukto.obadh.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.unmukto.obadh.R
import org.unmukto.obadh.settings.KeyboardPreferences
import org.unmukto.obadh.settings.TextShortcut
import org.unmukto.obadh.settings.TextShortcuts

/** Native list and full-screen editor; adding a shortcut is a standard toolbar action. */
@Composable
fun ShortcutsScreen(prefs: KeyboardPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TextShortcuts(context) }
    var items by remember { mutableStateOf(store.all()) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val original = editing
    if (original != null) {
        key(original) {
            ShortcutEditor(
                existing = items.firstOrNull { it.trigger == original }, items = items,
                onBack = { editing = null },
                onSave = { shortcut ->
                    val saved = store.put(shortcut, replacing = original.takeIf { it.isNotEmpty() })
                    if (saved) { items = store.all(); editing = null }
                    saved
                },
                onDelete = { store.remove(original); items = store.all(); editing = null },
            )
        }
        return
    }
    SettingsScaffold("Text shortcuts", onBack, actions = {
        IconButton(onClick = { editing = "" }) { Symbol(R.drawable.ic_add, "Add shortcut") }
    }) {
        PrefToggle("Use text shortcuts", "Expand a shortcut when you press space or return", prefs.textShortcutsEnabled) { prefs.textShortcutsEnabled = it }
        PreferenceNote("Shortcuts work in Bangla and English. They are case-sensitive and stay on this device.")
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No shortcuts yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Save a phrase you often type, like your email address.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { editing = "" }) { Text("Add shortcut") }
            }
        } else {
            PreferenceHeading("Your shortcuts")
            items.forEach { item ->
                PreferenceItem(
                    content = { Text(item.trigger) },
                    supportingContent = { Text(item.expansion.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = { editing = item.trigger },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutEditor(
    existing: TextShortcut?, items: List<TextShortcut>, onBack: () -> Unit,
    onSave: (TextShortcut) -> Boolean, onDelete: () -> Unit,
) {
    var trigger by rememberSaveable { mutableStateOf(existing?.trigger ?: "") }
    var expansion by rememberSaveable { mutableStateOf(existing?.expansion ?: "") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val duplicate = items.any { it.trigger == trigger && it.trigger != existing?.trigger }
    val full = existing == null && items.size >= TextShortcuts.MAX_ITEMS
    val valid = trigger.isNotBlank() && expansion.isNotBlank() && !duplicate && !full
    val changed = trigger != (existing?.trigger ?: "") || expansion != (existing?.expansion ?: "")
    val back = { if (changed) discard = true else onBack() }
    BackHandler(onBack = back)
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add shortcut" else "Edit shortcut") },
                navigationIcon = { IconButton(onClick = back) { Symbol(R.drawable.ic_arrow_back, "Back") } },
                actions = {
                    TextButton(enabled = valid, onClick = { saveFailed = !onSave(TextShortcut(trigger, expansion)) }) { Text("Save") }
                },
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                OutlinedTextField(
                    value = expansion, onValueChange = { expansion = it.take(TextShortcuts.MAX_EXPANSION); saveFailed = false },
                    label = { Text("Phrase") }, placeholder = { Text("name@example.com") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = trigger, onValueChange = {
                        trigger = it.filterNot(Char::isWhitespace).take(TextShortcuts.MAX_TRIGGER)
                        saveFailed = false
                    },
                    label = { Text("Shortcut") }, placeholder = { Text("@@") },
                    supportingText = { Text(if (duplicate) "This shortcut already exists" else "Type this, then space or return, to insert the phrase") },
                    isError = duplicate, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
                )
                if (full || saveFailed) {
                    Text("You can save up to ${TextShortcuts.MAX_ITEMS} shortcuts. Delete one before adding another.",
                        Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (existing != null) {
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { deleting = true }) { Text("Delete shortcut", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (deleting) ConfirmDeletion("Delete shortcut?", "The shortcut and its phrase will be removed.", { deleting = false }, onDelete)
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        text = { Text("Your changes haven't been saved.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
    )
}
