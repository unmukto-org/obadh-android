package org.unmukto.obadh.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.unmukto.obadh.R
import org.unmukto.obadh.settings.KeyboardDataCommands
import org.unmukto.obadh.settings.KeyboardPreferences

/** Shared Android toolbar, safe insets, scroll state and readable tablet width. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title }.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Symbol(R.drawable.ic_arrow_back, "Back")
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var details by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    SettingsScaffold("About", onBack, snackbar = snackbar) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.brand_icon), null, Modifier.size(64.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                Text("Obadh", style = MaterialTheme.typography.headlineSmall)
                Text("Bangla keyboard", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PreferenceItem(content = { Text("Version") }, supportingContent = { Text(AppBuildInfo.version) })
        PreferenceItem(content = { Text("Open source") }, supportingContent = { Text("Made by Unmukto. Bangla powered by Obadh Engine. Android keyboard foundation: HeliBoard / AOSP (GPLv3).") })
        PreferenceNote("Optional English swipe typing uses a separately downloaded Google library. It is not part of the open-source app.")
        PreferenceItem(
            content = { Text("Build details") },
            supportingContent = { Text(if (details) "Tap to hide" else "Technical information for support") },
            onClick = { details = !details },
        )
        if (details) {
            ValueRow("Engine", AppBuildInfo.engineVersion)
            ValueRow("Build", AppBuildInfo.build)
            ValueRow("Revision", AppBuildInfo.gitRevision.ifEmpty { "Unavailable" })
            ValueRow("Built", AppBuildInfo.buildTime.ifEmpty { "Unavailable" })
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Obadh build", AppBuildInfo.summary))
                scope.launch { snackbar.showSnackbar("Build details copied") }
            }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Copy build details") }
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String) {
    PreferenceItem(content = { Text(title) }, supportingContent = { Text(value) })
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    SettingsScaffold("Privacy", onBack, snackbar = snackbar) {
        PreferenceHeading("On-device processing")
        PreferenceNote("Transliteration, autocorrect, suggestions and emoji search work on your device. Obadh does not send your typing to a server. Network access is used only for the optional swipe-typing library download you request.")
        PreferenceHeading("Stored data")
        PreferenceNote("Obadh stores learned words, recent emoji, your text shortcuts, clipboard history and an optional resized keyboard photo in its private storage. Clipboard collection excludes password fields, incognito mode and items marked sensitive.")
        PreferenceHeading("Keyboard access")
        PreferenceNote("Android warns that any keyboard can read what you type. Obadh uses this access to enter and correct text, with all processing on your device.")
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        PreferenceItem(
            content = { Text("Delete learned words", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("Reset personal suggestions. Built-in suggestions stay available.") },
            onClick = { confirming = true },
        )
    }
    if (confirming) {
        ConfirmDeletion("Delete learned words?", "Your personal typing history will be removed. This can't be undone.",
            onDismiss = { confirming = false }, onConfirm = {
                confirming = false
                scope.launch {
                    val done = KeyboardDataCommands.clear(context, clipboard = false)
                    snackbar.showSnackbar(if (done) "Learned words deleted" else "Couldn't delete learned words. Try again.")
                }
            })
    }
}

@Composable
fun ClipboardScreen(prefs: KeyboardPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    SettingsScaffold("Clipboard", onBack, snackbar = snackbar) {
        PrefToggle("Clipboard history", "Keep copied text in the keyboard's clipboard panel", prefs.clipboardHistoryEnabled) { prefs.clipboardHistoryEnabled = it }
        PreferenceNote("Saved items stay on this device. Turning history off stops collection; use Delete clipboard history to remove existing items.")
        PreferenceItem(
            content = { Text("Delete clipboard history", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("Remove all items stored by Obadh") },
            onClick = { confirming = true },
        )
    }
    if (confirming) {
        ConfirmDeletion("Delete clipboard history?", "All clipboard items stored by Obadh will be removed. This can't be undone.",
            onDismiss = { confirming = false }, onConfirm = {
                confirming = false
                scope.launch {
                    val done = KeyboardDataCommands.clear(context, clipboard = true)
                    snackbar.showSnackbar(if (done) "Clipboard history deleted" else "Couldn't delete clipboard history. Try again.")
                }
            })
    }
}

@Composable
fun ConfirmDeletion(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
