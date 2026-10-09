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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
    contentMaxWidth: Dp = 720.dp,
    contentBehindNavigationBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title }.imePadding().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = safeInsets,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, Modifier.padding(start = if (scrollBehavior.state.collapsedFraction < .5f) 8.dp else 0.dp).offset(y = 10.dp * (1f - scrollBehavior.state.collapsedFraction))) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Symbol(R.drawable.ic_arrow_back, "Back")
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                expandedHeight = 152.dp,
                windowInsets = safeInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        val direction = LocalLayoutDirection.current
        val bodyInsets = if (contentBehindNavigationBar) PaddingValues(
            start = insets.calculateStartPadding(direction), top = insets.calculateTopPadding(),
            end = insets.calculateEndPadding(direction),
        ) else insets
        Box(Modifier.fillMaxSize().padding(bodyInsets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(bottom = 24.dp + if (contentBehindNavigationBar) insets.calculateBottomPadding() else 0.dp),
                content = content,
            )
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var details by rememberSaveable { mutableStateOf(false) }
    var engineLicenses by rememberSaveable { mutableStateOf(false) }
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
        PreferenceItem(content = { Text("Made by Unmukto") }, supportingContent = { Text("Obadh brings fast Bangla phonetic typing, corrections, suggestions and personal vocabulary to Android.") })
        PreferenceItem(content = { Text("Obadh Engine") }, supportingContent = { Text("Our Bangla language engine powers transliteration, autocorrect, suggestions and Bangla emoji search, on your device. Tap for licenses.") }, onClick = { engineLicenses = true })
        PreferenceHeading("Open-source acknowledgments")
        PreferenceItem(content = { Text("Android keyboard foundation") }, supportingContent = { Text("HeliBoard / AOSP (GPLv3), adapted for Obadh's unified Bangla and English keyboard.") })
        PreferenceNote("Optional English swipe typing uses a separately downloaded Google library. It is not part of the open-source app.")
        PreferenceItem(
            content = { Text("Build details") },
            supportingContent = { Text(if (details) "Tap to hide" else "Technical information for support") },
            onClick = { details = !details },
        )
        PreferenceHeading("Online media")
        PreferenceItem(content={ Text("KLIPY") },supportingContent={ Text("Optional GIFs and stickers are provided by KLIPY. Obadh is independently developed by Unmukto. The provider's artwork and trademarks retain their own rights.") },onClick={
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://klipy.com/support/api-terms")))
        })
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
    if (engineLicenses) {
        val notices = remember(context) {
            "Obadh Engine (MIT)\n\n" + context.assets.open("licenses/obadh-engine.txt").bufferedReader().use { it.readText() } +
                "\n\nEmoticon mappings: wooorm/emoticon 4.1.0 (MIT)\n\n" +
                context.assets.open("licenses/emoticons.txt").bufferedReader().use { it.readText() }
        }
        AlertDialog(onDismissRequest = { engineLicenses = false }, title = { Text("Engine licenses") },
            text = { Text(notices, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { engineLicenses = false }) { Text("Close") } })
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
        PreferenceNote("Transliteration, autocorrect, suggestions and emoji search work on your device. Ordinary typing, surrounding editor text and clipboard data are never sent to KLIPY. Optional online GIF/sticker searches and accepted shares go to KLIPY only after you enable the feature, using a random identifier for this installation. Online media is unavailable in password and incognito fields. Swipe-typing downloads use the network only when requested.")
        PreferenceHeading("Stored data")
        PreferenceNote("Obadh stores learned words, recent emoji, text shortcuts, clipboard history and an optional resized keyboard photo in private storage. GIF/sticker recents retain references and a bounded thumbnail cache; original animations are fetched for each send and are never written to disk. Clear local media recents from GIFs & stickers settings. Clipboard collection excludes password fields, incognito mode and items marked sensitive.")
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
