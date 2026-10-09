package org.unmukto.obadh.app

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.widget.SeekBar
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import helium314.keyboard.latin.obadh.ObadhColors
import org.unmukto.obadh.settings.KeyboardPhoto
import kotlin.math.max
import kotlin.math.roundToInt

/** Full-screen crop/brightness sequence; Done saves My themes, and Apply activates the photo. */
@Composable
internal fun PhotoThemeEditor(bitmap: Bitmap, saving: Boolean, onCancel: () -> Unit, onSave: (KeyboardPhoto.Crop, Float) -> Unit, initialCrop: KeyboardPhoto.Crop? = null, initialBrightness: Float = .4f) {
    val aspect = 948f / 605f
    val sourceAspect = bitmap.width.toFloat() / bitmap.height
    var brightnessStep by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(initialCrop?.let { (minOf(.8f, aspect / sourceAspect) / it.width).coerceIn(1f, 5f) } ?: 1f) }
    var cx by rememberSaveable { mutableFloatStateOf(initialCrop?.let { it.left + it.width / 2 } ?: .5f) }
    var cy by rememberSaveable { mutableFloatStateOf(initialCrop?.let { it.top + it.height / 2 } ?: .5f) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialBrightness) }
    val cropWidth = minOf(.8f, aspect / sourceAspect) / zoom
    val cropHeight = minOf(.8f * sourceAspect / aspect, 1f) / zoom
    val crop = KeyboardPhoto.Crop((cx - cropWidth / 2).coerceIn(0f, 1f - cropWidth), (cy - cropHeight / 2).coerceIn(0f, 1f - cropHeight), cropWidth, cropHeight)
    val rendered by produceState<Bitmap?>(null, brightnessStep, crop) {
        value = if (brightnessStep) KeyboardPhoto.render(bitmap, crop, 1f) else null
    }
    val context = LocalContext.current
    val buttonColor = themePalette("dynamic", false).accent
    fun back() { if (brightnessStep) brightnessStep = false else onCancel() }
    Dialog(onDismissRequest = { if (!saving) back() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, view).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
            }
        }
        BackHandler(enabled = !saving) { back() }
        BoxWithConstraints(Modifier.fillMaxSize().background(if (brightnessStep) Color(0xff21272b) else Color.Black)) {
            val portrait = maxHeight > maxWidth
            val contentWidth = if (portrait) maxWidth else minOf(maxWidth * .6f, (maxHeight - 220.dp).coerceAtLeast(120.dp) * aspect / .8f)
            val cropW = contentWidth * .8f
            val cropH = cropW / aspect
            if (brightnessStep) {
                Column(Modifier.width(cropW).align(Alignment.Center).offset(y = -cropH / 2 - 43.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Adjust Brightness", color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        AndroidView(factory = { ctx ->
                            SeekBar(ctx).apply {
                                max = 100; progress = (brightness * 100).roundToInt()
                                progressTintList = ColorStateList.valueOf(0xffb5c7fc.toInt())
                                thumbTintList = progressTintList
                                progressBackgroundTintList = ColorStateList.valueOf(0xff5b6063.toInt())
                                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) brightness = value / 100f }
                                    override fun onStartTrackingTouch(bar: SeekBar) {}
                                    override fun onStopTrackingTouch(bar: SeekBar) {}
                                })
                            }
                        }, update = { it.isEnabled = !saving; it.progress = (brightness * 100).roundToInt() }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(16.dp))
                        Text("${(brightness * 100).roundToInt()}%", Modifier.width(48.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }

                }
                rendered?.let { KeyboardThemePreview("photo", true, true, it, Modifier.width(cropW).align(Alignment.Center), brightness = brightness) }
            } else {
                Canvas(Modifier.width(contentWidth).fillMaxHeight().align(Alignment.Center)
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, scale, _ ->
                            val oldZoom = zoom
                            zoom = (zoom * scale).coerceIn(1f, 5f)
                            val baseScale = max(size.width.toFloat() / bitmap.width, size.width * .8f / aspect / bitmap.height)
                            val drawW = bitmap.width * baseScale * oldZoom
                            val drawH = bitmap.height * baseScale * oldZoom
                            val cw = minOf(.8f, aspect / sourceAspect) / zoom
                            val ch = minOf(.8f * sourceAspect / aspect, 1f) / zoom
                            cx = (cx - pan.x / drawW).coerceIn(cw / 2, 1f - cw / 2)
                            cy = (cy - pan.y / drawH).coerceIn(ch / 2, 1f - ch / 2)
                        }
                    }) {
                    val scale = max(size.width / bitmap.width, size.width * .8f / aspect / bitmap.height) * zoom
                    drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset((size.width / 2 - bitmap.width * scale * cx).roundToInt(), (size.height / 2 - bitmap.height * scale * cy).roundToInt()),
                        dstSize = IntSize((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt()))
                    val w = size.width * .8f; val h=w/aspect
                    val x=(size.width-w)/2;val y=(size.height-h)/2
                    drawRect(Color.Black.copy(alpha=.4f),size=Size(size.width,y))
                    drawRect(Color.Black.copy(alpha=.4f),topLeft=Offset(0f,y+h),size=Size(size.width,size.height-y-h))
                    drawRect(Color.Black.copy(alpha=.4f),topLeft=Offset(0f,y),size=Size(x,h))
                    drawRect(Color.Black.copy(alpha=.4f),topLeft=Offset(x+w,y),size=Size(x,h))
                    drawRect(Color.White,Offset(x,y),Size(w,h),style=Stroke(1.dp.toPx()))
                }
                Text("Pinch to Scale, Drag to Move", Modifier.align(Alignment.Center).offset(y = -cropH/2 - 56.dp), color = Color.White, fontSize = 18.sp)
            }
            Button(enabled = !saving, onClick = { if (brightnessStep) onSave(crop, brightness) else brightnessStep = true },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (portrait) 96.dp else 12.dp).width(106.dp).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(buttonColor), contentColor = Color.White)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (brightnessStep) "Done" else "Next", fontSize = 16.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}
