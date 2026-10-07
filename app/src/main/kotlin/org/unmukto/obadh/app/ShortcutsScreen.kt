package org.unmukto.obadh.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
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

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, Modifier.padding(start = 4.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ShortcutField(
    value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardOptions,
    monospace: Boolean, singleLine: Boolean,
) {
    TextField(
        value, onChange, Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default) },
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default),
        singleLine = singleLine, maxLines = if (singleLine) 1 else 4, keyboardOptions = keyboard,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Brand.Teal,
        ),
    )
}

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
        Dialog(onDismissRequest = { editing = null }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(24.dp),
            ) {
                Text(if (existing == null) "New Shortcut" else "Edit Shortcut", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Type the shortcut, then space, and it becomes the phrase.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                FieldLabel("Shortcut")
                ShortcutField(
                    trigger, { trigger = it.filterNot { c -> c.isWhitespace() }.take(TextShortcuts.MAX_TRIGGER) },
                    "@@", KeyboardOptions(keyboardType = KeyboardType.Ascii), monospace = true, singleLine = true,
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel("Phrase")
                ShortcutField(
                    expansion, { expansion = it.take(TextShortcuts.MAX_EXPANSION) },
                    "name@example.com", KeyboardOptions.Default, monospace = false, singleLine = false,
                )
                if (valid) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Brand.Teal.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(trigger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand.Teal)
                        Text("  \u2192  ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            expansion.replace('\n', ' '), Modifier.weight(1f), fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (existing != null) {
                        Text(
                            "Delete",
                            Modifier.clip(CircleShape)
                                .clickable { store.remove(existing.trigger); items = store.all(); editing = null }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Cancel",
                        Modifier.clip(CircleShape).clickable { editing = null }.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(CircleShape)
                            .then(if (valid) Modifier.background(Brand.action) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant))
                            .clickable(enabled = valid) {
                                if (store.put(TextShortcut(trigger, expansion), replacing = existing?.trigger)) items = store.all()
                                editing = null
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = if (valid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
