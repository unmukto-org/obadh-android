package org.unmukto.obadh.app

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.obadh.ObadhColors
import org.unmukto.obadh.settings.KeyboardPhoto
import org.unmukto.obadh.settings.KeyboardPreferences
import kotlinx.coroutines.launch

/** The catalog, preview and IME use one palette, including translucent keys and gradient stops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(prefs: KeyboardPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoState = remember(context) { androidx.lifecycle.ViewModelProvider(context as androidx.activity.ComponentActivity)[ThemePhotoState::class.java] }
    val snackbar = remember { SnackbarHostState() }
    var selectedTheme by rememberSaveable { mutableStateOf(prefs.keyboardTheme) }
    var selectedMode by rememberSaveable { mutableIntStateOf(prefs.themeMode) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var previewBorders by rememberSaveable { mutableStateOf(prefs.keyBorders) }
    val saving = photoState.saving
    var photoRevision by remember { mutableIntStateOf(0) }
    var photoExists by remember { mutableStateOf(KeyboardPhoto.exists(context)) }
    val photo by produceState<Bitmap?>(null, photoRevision) { value = KeyboardPhoto.preview(context) }
    val pendingPhoto = photoState.pending
    val loadingPhoto = photoState.loading
    var deletingPhoto by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photoState.load(context, uri)
    }
    LaunchedEffect(Unit) { photoState.catalog(context) }
    LaunchedEffect(photoState.previewRevision) {
        if (photoState.previewRevision > 0 && photoState.staged != null) { previewBorders = prefs.keyBorders; preview = "photo" }
    }
    LaunchedEffect(photoState.revision) {
        if (photoState.revision > 0) {
            photoExists = KeyboardPhoto.exists(context); photoRevision++
            selectedTheme = prefs.keyboardTheme; selectedMode = prefs.themeMode; preview = null
        }
    }
    LaunchedEffect(photoState.error) {
        photoState.error?.let { snackbar.showSnackbar(it); photoState.error = null }
    }
    val systemNight = isSystemInDarkTheme()
    fun openPreview(id: String) { previewBorders = prefs.keyBorders; preview = id }
    val selectedId = when { selectedTheme == "default" && selectedMode == 1 -> "light"; selectedTheme == "default" && selectedMode == 2 -> "dark"; else -> selectedTheme }
    SettingsScaffold("Theme", onBack, snackbar = snackbar) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            ThemeHeading("My themes", height = 60.dp)
            val entries = listOf<String?>(null) + photoState.photos.map { it.id }
            entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { id ->
                        if (id == null) Box(Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp))
                            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            .clickable(enabled = !saving && !loadingPhoto, role = Role.Button) { picker.launch("image/*") }
                            .semantics { contentDescription = "Create keyboard theme with my image" }, contentAlignment = Alignment.Center) {
                            if (loadingPhoto) CircularProgressIndicator(Modifier.size(28.dp))
                            else Icon(androidx.compose.ui.res.painterResource(helium314.keyboard.latin.R.drawable.obadh_ic_add), null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            val item = photoState.photos.first { it.id == id }
                            val thumbnail by produceState<Bitmap?>(null, item, photoState.catalogRevision) { value = org.unmukto.obadh.settings.PhotoThemes.preview(context, item, small = true) }
                            ThemeTile("photo", "Custom photo ${photoState.photos.indexOf(item)+1}", selectedId == "photo" && prefs.photoId == id,
                                systemNight, thumbnail, Modifier.weight(1f)) { photoState.show(context,item) }
                        }
                    }
                    repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                }
                if(entries.size>3) Spacer(Modifier.height(8.dp))
            }
            // A photo from the older release stays available until replaced or removed.
            if (photoExists && prefs.photoId == null) ThemeTile("photo", "Custom photo", selectedId == "photo", systemNight, photo,
                Modifier.fillMaxWidth(.32f)) { photoState.current=null; photoState.staged=null; openPreview("photo") }
            Spacer(Modifier.height(24.dp))
            ThemeSection("Default", listOf("dynamic", "default", "light", "dark").filter { it != "dynamic" || Build.VERSION.SDK_INT >= 31 },
                selectedId, systemNight, labels = true, onSelect = ::openPreview)
            ThemeSection("Colors", ObadhColors.catalog.filter { it.group == "Colors" }.map { it.id }, selectedId, systemNight, collapsedCount = 9, onSelect = ::openPreview)
            ThemeSection("Light gradient", ObadhColors.catalog.filter { it.group == "Light gradient" }.map { it.id }, selectedId, systemNight, collapsedCount = 9, onSelect = ::openPreview)
            ThemeSection("Dark gradient", ObadhColors.catalog.filter { it.group == "Dark gradient" }.map { it.id }, selectedId, systemNight, collapsedCount = 9, onSelect = ::openPreview)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (deletingPhoto) ConfirmDeletion("Delete theme?", "This keyboard photo will be removed from My themes.", onDismiss = { deletingPhoto=false }, onConfirm = {
        deletingPhoto=false
        if (photoState.current != null) photoState.remove(context) else scope.launch {
            if (KeyboardPhoto.remove(context)) { photoExists=false; photoRevision++; selectedTheme=prefs.keyboardTheme; preview=null }
            else snackbar.showSnackbar("Couldn't delete this photo. Try again.")
        }
    })
    val pending = pendingPhoto
    if (pending != null) {
        PhotoThemeEditor(pending, saving,
            onCancel = { if (!saving) photoState.pending = null },
            onSave = { crop, brightness -> photoState.stage(context, pending, crop, brightness) },
            initialCrop = photoState.current?.crop, initialBrightness = photoState.current?.brightness ?: .4f)
    }
    preview?.let { id ->
        val night = when(id) { "dark", "photo" -> true; "light" -> false; else -> systemNight }
        ModalBottomSheet(onDismissRequest = { if (!saving) { preview = null; photoState.staged = null } }, dragHandle = null,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(24.dp)) {
                if (id == "photo") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(enabled = !saving && !loadingPhoto, onClick = {
                        preview = null
                        if(photoState.current != null) photoState.edit(context) else picker.launch("image/*")
                    }) { Symbol(helium314.keyboard.latin.R.drawable.obadh_ic_edit, "Edit theme") }
                    IconButton(enabled = !saving, onClick = { deletingPhoto = true }) { Symbol(helium314.keyboard.latin.R.drawable.obadh_ic_delete, "Delete theme") }
                }
                KeyboardThemePreview(id, night, previewBorders, if (id == "photo") photoState.staged ?: photo else null, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { previewBorders = !previewBorders }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Key borders", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Switch(previewBorders, onCheckedChange = { previewBorders = it })
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(enabled = !saving, onClick = { preview = null; photoState.staged = null }) { Text("Cancel") }
                    Button(enabled = !saving, onClick = {
                        if (id == "photo" && photoState.current != null) { photoState.apply(context, previewBorders); return@Button }
                        val theme = when (id) { "light", "dark" -> "default"; else -> id }
                        val mode = when(id) { "light" -> 1; "dark", "photo" -> 2; else -> 0 }
                        prefs.applyTheme(theme, mode, previewBorders)
                        selectedTheme = theme; selectedMode = mode; preview = null
                    }) { Text("Apply") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ThemeHeading(title: String, expanded: Boolean? = null, height: androidx.compose.ui.unit.Dp = 48.dp, onExpand: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        if (expanded != null) IconButton(onClick = onExpand, Modifier.size(48.dp)) {
            Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.ui.res.painterResource(helium314.keyboard.latin.R.drawable.obadh_ic_expand_more), "${if (expanded) "Show less" else "Show more"} $title", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ThemeSection(title: String, ids: List<String>, selectedId: String, night: Boolean, labels: Boolean = false,
    collapsedCount: Int = Int.MAX_VALUE, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    ThemeHeading(title, if (ids.size > collapsedCount && !expanded) false else null) { expanded = !expanded }
    (if (expanded) ids else ids.take(collapsedCount)).chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { id ->
                val label = when(id) { "dynamic" -> "Dynamic Color"; "default" -> "System Auto"; "light" -> "Default"; "dark" -> "Default Dark"; else -> ObadhColors.catalog.first { it.id == id }.label }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    ThemeTile(id, label, selectedId == id, night, modifier = Modifier.fillMaxWidth()) { onSelect(id) }
                    if (labels) Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ThemeTile(id: String, label: String, selected: Boolean, night: Boolean, photo: Bitmap? = null,
    modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val p = themePalette(id, night)
    val brush = themeBrush(id, night)
    val frame = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .8f).compositeOver(MaterialTheme.colorScheme.surface)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = .05f).compositeOver(MaterialTheme.colorScheme.surface)
    Box(modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)).background(frame)
        .clickable(role = Role.RadioButton, onClick = onSelect).semantics { this.selected = selected; contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().padding(3.dp).clip(RoundedCornerShape(17.dp)).background(brush), contentAlignment = Alignment.Center) {
        if (photo != null) Image(photo.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (id == "default") Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.5f).background(Color(ObadhColors.palette("default", true).background)))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).fillMaxWidth(.4f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(p.space))) {
            if (id == "default") Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.5f).background(Color(ObadhColors.palette("default", true).functional)))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 12.dp).size(width = 14.dp, height = 10.dp).background(Color(if (id == "default") ObadhColors.palette("default", true).accent else p.accent), CircleShape))
        }
        if (selected) Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(androidx.compose.ui.res.painterResource(helium314.keyboard.latin.R.drawable.obadh_ic_check), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
internal fun themePalette(id: String, night: Boolean): ObadhColors.Palette {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(id, night, configuration) {
        if (id == "dynamic" && Build.VERSION.SDK_INT >= 31) ObadhColors.dynamicPalette(context, night) else ObadhColors.palette(id, night)
    }
}

@Composable
internal fun themeBrush(id: String, night: Boolean): Brush {
    val p = themePalette(id, night)
    val gradient = ObadhColors.catalog.firstOrNull { it.id == id }?.gradient
    return if (gradient != null) Brush.verticalGradient(*gradient.mapIndexed { index, color -> ObadhColors.catalog.first { it.id == id }.positions!![index] to Color(color) }.toTypedArray()) else Brush.verticalGradient(listOf(Color(p.background), Color(p.background)))
}
