// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.unmukto.obadh.app.*

class MediaSettingsActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);enableObadhEdgeToEdge()
        setContent { ObadhTheme { MediaSettings { finish() } } }
    }
}

@Composable
private fun MediaSettings(onBack: ()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val snackbar=remember { SnackbarHostState() }
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure {
            scope.launch { snackbar.showSnackbar("No browser is available to open this link.") }
        }
    }
    LaunchedEffect(Unit) { MediaPreferences.load(context);enabled=MediaPreferences.enabled;loaded=true }
    SettingsScaffold("GIFs & stickers",onBack,snackbar=snackbar) {
        if(loaded && KlipyClient.configured) {
            ToggleRow("Online GIFs & stickers",enabled,"Search KLIPY from the keyboard") { value -> MediaPreferences.setEnabled(value);enabled=value }
        } else if(!KlipyClient.configured) PreferenceNote("Online media isn't available in this build.")
        PreferenceNote("Your media searches and accepted shares go to KLIPY with a random identifier for this installation. Ordinary typing, surrounding text and clipboard content stay on your device. Password and incognito fields cannot use online media. Ads are disabled for Obadh.")
        PreferenceHeading("Recents & storage")
        PreferenceNote("Recents keep references and small preview thumbnails on this device. Original animations download each time you send them; they are never saved to disk. The thumbnail cache is limited to 6 MB and clears automatically as needed. Previews follow Android's reduced-motion setting.")
        PreferenceItem(content={ Text("Clear local recents & thumbnails",color=MaterialTheme.colorScheme.error) },onClick={ if(loaded)confirm=true })
        PreferenceHeading("Provider")
        PreferenceItem(content={ Text("Powered by KLIPY") },supportingContent={ Text("GIF and sticker content is supplied by KLIPY. Obadh is independently developed by Unmukto.") })
        PreferenceItem(content={ Text("KLIPY privacy policy") },onClick={ openLink("https://klipy.com/support/privacy-policy") })
        PreferenceItem(content={ Text("KLIPY API terms") },onClick={ openLink("https://klipy.com/support/api-terms") })
    }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text("Clear local media recents?") },text={ Text("Remove recent GIF/sticker references and cached thumbnails from this device. KLIPY's own records are managed under its privacy policy.") },
        confirmButton={ TextButton(onClick={ confirm=false;scope.launch { MediaPreferences.clear();MediaThumbnails.clear(context);snackbar.showSnackbar("Local recents and thumbnails cleared") } }) { Text("Clear") } },
        dismissButton={ TextButton(onClick={ confirm=false }) { Text("Cancel") } })
}
