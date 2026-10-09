package org.unmukto.obadh.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unmukto.obadh.swipe.*

/** Shared setup/settings flow. Switching on requests the optional dependency once. */
@Composable
fun SwipeTypingPreference() {
    if (Binaries.forDevice() == null) {
        PreferenceItem(content = { Text("Swipe typing") },
            supportingContent = { Text("Swipe typing isn't available on this device yet.") })
        return
    }
    val app = LocalContext.current.applicationContext
    val downloads = remember(app) { SwipeDownloads(app) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(SwipeStatus(SwipePhase.NotInstalled)) }
    var enabled by remember { mutableStateOf(downloads.enabled) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(downloads, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                status = withContext(Dispatchers.IO) { downloads.status() }
                enabled = downloads.enabled
                delay(if (status.busy) 500L else 1500L)
            }
        }
    }
    ToggleRow("Swipe typing", enabled || status.busy, "Slide across letters to write English words") { checked ->
        if (!checked) {
            enabled = false
            scope.launch(Dispatchers.IO) { downloads.cancel(); downloads.setEnabled(false) }
        } else if (status.phase == SwipePhase.Ready) {
            enabled = true
            scope.launch(Dispatchers.IO) { downloads.setEnabled(true) }
        } else confirm = true
    }
    if (status.busy) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val progress = if (status.total > 0) (status.downloaded.toFloat() / status.total).coerceIn(0f, 1f) else null
            if (progress != null && status.phase == SwipePhase.Downloading)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(when (status.phase) {
                SwipePhase.Downloading -> if (progress != null) "Downloading swipe typing · ${(progress * 100).toInt()}%" else "Downloading swipe typing"
                else -> status.message
            }, style = MaterialTheme.typography.bodyMedium)
            Text("You can leave this screen. Android will finish the download in the background.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { scope.launch(Dispatchers.IO) { downloads.cancel(); downloads.setEnabled(false) } }) { Text("Cancel download") }
        }
    } else if (status.phase == SwipePhase.Failed) {
        PreferenceItem(content = { Text("Couldn't set up swipe typing", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(status.message) }, onClick = { confirm = true })
        TextButton(onClick = { scope.launch(Dispatchers.IO) { downloads.start() } }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Retry download") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Download swipe typing?") },
            text = { Text("English swipe typing needs Google's optional library (about 1 MB). Android will download it in the background and Obadh will verify it before use.") },
            confirmButton = { TextButton(onClick = {
                confirm = false
                scope.launch(Dispatchers.IO) { downloads.start() }
            }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
