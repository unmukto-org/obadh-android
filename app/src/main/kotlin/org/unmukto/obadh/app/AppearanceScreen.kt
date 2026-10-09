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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import helium314.keyboard.latin.obadh.ObadhColors
import org.unmukto.obadh.settings.KeyboardPhoto
import org.unmukto.obadh.settings.KeyboardPreferences
import kotlinx.coroutines.launch

/** The catalog, preview and IME use one palette, including translucent keys and gradient stops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(prefs: KeyboardPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val grid = themeGridMetrics()
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
        if (photoState.previewRevision > 0 && photoState.staged != null && photoState.pending == null) { previewBorders = prefs.keyBorders; preview = "photo" }
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
    SettingsScaffold("Theme", onBack, snackbar = snackbar, contentMaxWidth = Dp.Infinity) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            ThemeHeading("My themes", height = if (grid.landscape) 64.dp else 60.dp)
            val entries = listOf<String?>(null) + photoState.photos.map { it.id }
            entries.chunked(grid.columns).forEach { row ->
                ThemeGrid { tileWidth ->
                    row.forEach { id ->
                        if (id == null) Box(Modifier.width(tileWidth).aspectRatio(grid.aspectRatio).clip(RoundedCornerShape(20.dp))
                            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            .clickable(enabled = !saving && !loadingPhoto, role = Role.Button) { picker.launch("image/*") }
                            .semantics { contentDescription = "Create keyboard theme with my image" }, contentAlignment = Alignment.Center) {
                            if (loadingPhoto) CircularProgressIndicator(Modifier.size(28.dp))
                            else Box(Modifier.size(32.dp)) {
                                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary))
                                Box(Modifier.align(Alignment.Center).fillMaxHeight().width(4.dp).background(MaterialTheme.colorScheme.primary))
                            }
                        } else {
                            val item = photoState.photos.first { it.id == id }
                            val thumbnail by produceState<Bitmap?>(null, item, photoState.catalogRevision) { value = org.unmukto.obadh.settings.PhotoThemes.preview(context, item, small = true) }
                            ThemeTile("photo", "Custom photo ${photoState.photos.indexOf(item)+1}", selectedId == "photo" && prefs.photoId == id,
                                systemNight, thumbnail, Modifier.width(tileWidth)) { photoState.show(context,item) }
                        }
                    }
                    repeat(grid.columns-row.size) { Spacer(Modifier.width(tileWidth)) }
                }
                if(entries.size>grid.columns) Spacer(Modifier.height(grid.gap))
            }
            // A photo from the older release stays available until replaced or removed.
            if (photoExists && prefs.photoId == null) ThemeGrid { tileWidth ->
                ThemeTile("photo", "Custom photo", selectedId == "photo", systemNight, photo,
                    Modifier.width(tileWidth)) { photoState.current=null; photoState.staged=null; openPreview("photo") }
                repeat(grid.columns - 1) { Spacer(Modifier.width(tileWidth)) }
            }
            Spacer(Modifier.height(if (grid.landscape) 32.dp else 24.dp))
            ThemeSection("Default", listOf("dynamic", "default", "light", "dark").filter { it != "dynamic" || Build.VERSION.SDK_INT >= 31 },
                selectedId, systemNight, labels = true, onSelect = ::openPreview)
            ThemeSection("Colors", ObadhColors.catalog.filter { it.group == "Colors" }.map { it.id }, selectedId, systemNight, collapsedCount = grid.columns * 3, onSelect = ::openPreview)
            ThemeSection("Light gradient", ObadhColors.catalog.filter { it.group == "Light gradient" }.map { it.id }, selectedId, systemNight, collapsedCount = grid.columns * 3, onSelect = ::openPreview)
            ThemeSection("Dark gradient", ObadhColors.catalog.filter { it.group == "Dark gradient" }.map { it.id }, selectedId, systemNight, collapsedCount = grid.columns * 3, onSelect = ::openPreview)
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
            onCancel = { if (!saving) photoState.cancelPreview() },
            onSave = { crop, brightness -> photoState.stage(pending, crop, brightness) },
            initialCrop = photoState.current?.crop, initialBrightness = photoState.current?.brightness ?: .4f)
    }
    preview?.let { id ->
        val night = when(id) { "dark", "photo" -> true; "light" -> false; else -> systemNight }
        ModalBottomSheet(onDismissRequest = { if (!saving) { preview = null; photoState.cancelPreview() } }, dragHandle = null,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(24.dp)) {
                if (id == "photo") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(enabled = !saving && !loadingPhoto, onClick = {
                        preview = null
                        if(photoState.current != null) photoState.edit(context) else picker.launch("image/*")
                    }) { Symbol(helium314.keyboard.latin.R.drawable.obadh_ic_edit, "Edit theme") }
                    IconButton(enabled = !saving, onClick = { deletingPhoto = true }) { Symbol(helium314.keyboard.latin.R.drawable.obadh_ic_delete, "Delete theme") }
                }
                val previewWidth = (configuration.screenHeightDp.dp - 224.dp - if (id == "photo") 48.dp else 0.dp).coerceAtLeast(100.dp) * (948f / 605f)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (id == "default") SystemThemePreview(previewBorders, Modifier.widthIn(max = previewWidth).fillMaxWidth())
                    else KeyboardThemePreview(id, night, previewBorders, if (id == "photo") photoState.staged ?: photo else null, Modifier.widthIn(max = previewWidth).fillMaxWidth())
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { previewBorders = !previewBorders }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Key borders", Modifier.weight(1f), fontSize = 18.sp)
                    Switch(previewBorders, onCheckedChange = { previewBorders = it })
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(modifier = Modifier.widthIn(min = 91.dp).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 16.dp), enabled = !saving, onClick = { preview = null; photoState.cancelPreview() }) { Text("Cancel", maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.primary) }
                    val actionPalette = themePalette("dynamic", systemNight)
                    Button(modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(actionPalette.accent), contentColor = Color(actionPalette.actionText)), enabled = !saving, onClick = {
                        if (id == "photo" && photoState.current != null) { photoState.apply(context, previewBorders); return@Button }
                        val theme = when (id) { "light", "dark" -> "default"; else -> id }
                        val mode = when(id) { "light" -> 1; "dark", "photo" -> 2; else -> 0 }
                        prefs.applyTheme(theme, mode, previewBorders)
                        selectedTheme = theme; selectedMode = mode; preview = null
                    }) { Text("Apply", maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Normal) }
                }
            }
        }
    }
}

@Composable
private fun ThemeHeading(title: String, expanded: Boolean? = null, height: androidx.compose.ui.unit.Dp = 48.dp, onExpand: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).offset(y = when (title) { "My themes" -> 3.dp; "Default" -> (-3).dp; else -> 0.dp }), fontSize = 14.sp, letterSpacing = .2.sp, color = MaterialTheme.colorScheme.primary)
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
    val grid = themeGridMetrics()
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    ThemeHeading(title, if (ids.size > collapsedCount && !expanded) false else null) { expanded = !expanded }
    (if (expanded) ids else ids.take(collapsedCount)).chunked(grid.columns).forEach { row ->
        ThemeGrid { tileWidth ->
            row.forEach { id ->
                val label = when(id) { "dynamic" -> "Dynamic Color"; "default" -> "System Auto"; "light" -> "Default"; "dark" -> "Default Dark"; else -> ObadhColors.catalog.first { it.id == id }.label }
                Column(Modifier.width(tileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    ThemeTile(id, label, selectedId == id, night, modifier = Modifier.fillMaxWidth()) { onSelect(id) }
                    if (labels) Text(label, Modifier.padding(top = 3.dp).offset(y = (-1).dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = if (grid.landscape) 14.sp else 11.sp, lineHeight = when { grid.landscape -> 30.sp; grid.columns > 3 -> 20.sp; else -> 16.sp }, letterSpacing = 0.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(grid.columns - row.size) { Spacer(Modifier.width(tileWidth)) }
        }
        Spacer(Modifier.height(grid.gap))
    }
    Spacer(Modifier.height(if (grid.landscape) 16.dp else 12.dp))
}

/** System Auto previews both modes and explains the wallpaper-independent night behavior. */
@Composable
private fun SystemThemePreview(borders: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val tileWidth = maxWidth * (648f / 948f)
        Column(Modifier.fillMaxWidth().heightIn(min = maxWidth * (605f / 948f)), verticalArrangement = Arrangement.Bottom) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyboardThemePreview("light", false, borders, null, Modifier.width(tileWidth))
                KeyboardThemePreview("dark", true, borders, null, Modifier.width(tileWidth))
            }
            Spacer(Modifier.height(10.dp))
            Text("Appearance will follow system settings", Modifier.fillMaxWidth().heightIn(min = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp,
                letterSpacing = 0.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
}

private data class ThemeGridMetrics(val columns: Int, val gap: Dp, val aspectRatio: Float, val landscape: Boolean)

/** Three initial rows, with the native gallery's wider tablet and landscape tiles. */
@Composable
private fun themeGridMetrics(): ThemeGridMetrics {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    return ThemeGridMetrics((configuration.screenWidthDp / 140).coerceIn(3, 6),
        if (landscape) 16.dp else 8.dp, if (landscape) 16f / 9f else 4f / 3f, landscape)
}

/** Keep every tile on the same physical pixel grid, including fractional device densities. */
@Composable
private fun ThemeGrid(content: @Composable RowScope.(androidx.compose.ui.unit.Dp) -> Unit) {
    val density = LocalDensity.current
    val grid = themeGridMetrics()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileWidth = with(density) { ((constraints.maxWidth - (grid.columns - 1) * grid.gap.roundToPx()) / grid.columns).toDp() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (grid.columns == 3) Arrangement.spacedBy(grid.gap) else Arrangement.SpaceBetween) { content(tileWidth) }
    }
}

@Composable
private fun ThemeTile(id: String, label: String, selected: Boolean, night: Boolean, photo: Bitmap? = null,
    modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val grid = themeGridMetrics()
    val p = themePalette(id, night)
    val brush = themeBrush(id, night)
    val frame = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .8f).compositeOver(MaterialTheme.colorScheme.surface)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = .05f).compositeOver(MaterialTheme.colorScheme.surface)
    Box(modifier.aspectRatio(grid.aspectRatio).clip(RoundedCornerShape(20.dp)).background(frame)
        .clickable(role = Role.RadioButton, onClick = onSelect).semantics { this.selected = selected; contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().padding(3.dp).clip(RoundedCornerShape(17.dp)).background(brush), contentAlignment = Alignment.Center) {
        if (photo != null) Image(photo.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (id == "default") Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.5f).background(Color(ObadhColors.palette("default", true).background)))
        val thumbnailSpace = when(id) {
            "dark" -> p.functional
            "material_light" -> 0xffc9ced1.toInt()
            "material_dark" -> 0xff3b464c.toInt()
            "classic_light" -> 0xffe1e2e2.toInt()
            else -> p.space
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).fillMaxWidth(.4f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(thumbnailSpace))) {
            if (id == "default") Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.5f).background(Color(ObadhColors.palette("default", true).functional)))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 10.dp).size(width = 14.dp, height = 10.dp).background(Color(if (id == "default") ObadhColors.palette("default", true).accent else p.accent), CircleShape))
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
    val theme = ObadhColors.catalog.firstOrNull { it.id == id }
    return remember(id, p) {
        if (theme?.mesh != null) object : ShaderBrush() {
            override fun createShader(size: androidx.compose.ui.geometry.Size) = ObadhColors.meshShader(theme.mesh!!, size.width, size.height)
        } else if (theme?.gradient != null) Brush.verticalGradient(*theme.gradient!!.mapIndexed { index, color -> theme.positions!![index] to Color(color) }.toTypedArray())
        else Brush.verticalGradient(listOf(Color(p.background), Color(p.background)))
    }
}
