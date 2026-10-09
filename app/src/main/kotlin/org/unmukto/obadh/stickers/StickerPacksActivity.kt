// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.unmukto.obadh.app.*

class StickerPacksActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);enableObadhEdgeToEdge()
        setContent { ObadhTheme { StickerPacksScreen(intent.getStringExtra("pack")) { finish() } } }
    }
}

@Composable
fun StickerPacksScreen(selected: String?=null,onBack: () -> Unit) {
    val context=LocalContext.current
    var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { StickerCatalog.load(context) } }.onSuccess { packs=it }.onFailure { error=true }
    }
    SettingsScaffold("Stickers",onBack) {
        PreferenceNote("Two packs are included. Download more from Obadh's GitHub collection. Search works on your device; your queries are never uploaded.")
        if(error) PreferenceNote("Couldn't load the sticker catalog. Try reopening Obadh.")
        packs.sortedBy { if(it.id==selected) 0 else if(it.bundled) 1 else 2 }.forEach { pack -> key(pack.id) { PackRow(pack) } }
        PreferenceItem(content={ Text("Artwork credits & source") },supportingContent={ Text("Volpeon · Apache-2.0\nOpenMoji · CC BY-SA 4.0") },onClick={
            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(StickerCatalog.REPOSITORY)))
        })
        PreferenceNote("Stickers can be sent only in apps that accept keyboard images. Some apps show them as image attachments. Long-press a sticker to save a favorite.")
    }
}

@Composable
private fun PackRow(pack: StickerPack) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val work by remember(pack.id) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(StickerDownloads.name(pack.id)) }.collectAsState(emptyList())
    val task=work.firstOrNull { !it.state.isFinished } ?: work.firstOrNull()
    var installed by remember(pack.id) { mutableStateOf(pack.bundled) }
    var removing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(task?.state,removing) { installed=withContext(Dispatchers.IO) { pack.available(context) } }
    val busy=task?.state in listOf(WorkInfo.State.ENQUEUED,WorkInfo.State.RUNNING,WorkInfo.State.BLOCKED)
    val description=when {
        pack.bundled -> "Included · ${pack.items.size} stickers"
        installed -> "Downloaded · ${pack.items.size} stickers"
        task?.state==WorkInfo.State.RUNNING -> if(task.progress.getString("phase")=="Verifying") "Checking download" else "Downloading"
        busy -> "Waiting for a connection or Android's download scheduler"
        task?.state==WorkInfo.State.FAILED -> task.outputData.getString("error") ?: "Download failed. Please retry."
        task?.state==WorkInfo.State.CANCELLED -> "Download canceled"
        else -> "${pack.items.size} stickers · %.1f MB".format(pack.archiveBytes/1_000_000.0)
    }
    PreferenceItem(content={ Text(pack.title) },supportingContent={ Text("$description\n${pack.artist} · ${pack.license}") },trailingContent={
        if(!pack.bundled) TextButton(enabled=!removing,onClick={
            when {
                busy -> scope.launch { StickerDownloads.cancel(context,pack.id) }
                installed -> confirm=true
                else -> StickerDownloads.start(context,pack)
            }
        }) { Text(if(busy) "Cancel" else if(installed) "Remove" else if(task?.state==WorkInfo.State.FAILED) "Retry" else "Download") }
    })
    if(busy) {
        val bytes=task?.progress?.getInt("bytes",0) ?: 0
        val total=task?.progress?.getInt("total",0) ?: 0
        if(total>0) LinearProgressIndicator(progress={ (bytes.toFloat()/total).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp))
        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=24.dp))
    }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text("Remove ${pack.title}?") },text={ Text("Free up storage on this device. You can download this pack again.") },
        confirmButton={ TextButton(onClick={ confirm=false;removing=true;scope.launch { StickerDownloads.remove(context,pack);installed=false;removing=false } }) { Text("Remove") } },
        dismissButton={ TextButton(onClick={ confirm=false }) { Text("Cancel") } })
}

@Composable
fun StickerCredits() {
    val context=LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(license) { text=license?.let { withContext(Dispatchers.IO) { context.assets.open("stickers/licenses/$it.txt").bufferedReader().use { it.readText() } } }.orEmpty() }
    PreferenceHeading("Artwork acknowledgments")
    PreferenceItem(content={ Text("Volpeon") },supportingContent={ Text("Blobfox, BunHD and vlpn · Apache-2.0\nBy Volpeon (formerly Feuerfuchs). Converted into static PNG stickers and thumbnails by Unmukto.") },onClick={ license="Apache-2.0" })
    PreferenceItem(content={ Text("OpenMoji") },supportingContent={ Text("By Benedikt Groß, Daniel Utz and the OpenMoji contributors · CC BY-SA 4.0. Converted and resized artwork retains this license.") },onClick={ license="CC-BY-SA-4.0" })
    PreferenceItem(content={ Text("Sticker collection & source credits") },supportingContent={ Text("Source links, original notices, authors and reproducible pack builds") },onClick={ context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(StickerCatalog.REPOSITORY))) })
    if(license!=null) AlertDialog(onDismissRequest={ license=null },title={ Text(license!!) },text={
        androidx.compose.foundation.layout.Box(Modifier.heightIn(max=400.dp)) {
            Text(text,Modifier.then(Modifier).verticalScrollCompat(),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={ TextButton(onClick={ license=null }) { Text("Close") } })
}
@Composable
private fun Modifier.verticalScrollCompat() = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
