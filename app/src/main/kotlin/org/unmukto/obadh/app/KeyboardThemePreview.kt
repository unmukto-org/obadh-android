package org.unmukto.obadh.app

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R

/** Scaled reference geometry; this settings-only drawing never initializes the typing engine. */
@Composable
internal fun KeyboardThemePreview(id: String, night: Boolean, borders: Boolean, photo: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier, brightness: Float = 1f) {
    val p = themePalette(id, night)
    val brush = themeBrush(id, night)
    val context = LocalContext.current
    val icons = remember(context) {
        listOf(R.drawable.obadh_ic_tools, R.drawable.obadh_ic_mic, R.drawable.obadh_ic_language,
            R.drawable.obadh_ic_backspace, R.drawable.obadh_ic_keyboard_return, R.drawable.obadh_ic_sentiment_satisfied)
            .map { ContextCompat.getDrawable(context, it)!!.mutate() }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL); textAlign = Paint.Align.CENTER } }
    Box(modifier.aspectRatio(948f / 605f).clip(RoundedCornerShape(12.dp)).background(brush)
        .semantics { contentDescription = "Keyboard theme preview" }) {
        if (photo != null) Image(photo.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            colorFilter = if (brightness == 1f) null else ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(brightness,0f,0f,0f,0f, 0f,brightness,0f,0f,0f, 0f,0f,brightness,0f,0f, 0f,0f,0f,1f,0f))))
        Canvas(Modifier.matchParentSize()) {
            val scale = size.width / 393f
            val canvas = drawContext.canvas.nativeCanvas
            val saved = canvas.save()
            canvas.scale(scale, scale)
            fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 6f) {
                paint.color = color
                canvas.drawRoundRect(x, y, x+w, y+h, radius, radius, paint)
            }
            fun text(value: String, x: Float, y: Float, size: Float, color: Int) {
                paint.color = color; paint.textSize = size
                canvas.drawText(value, x, y, paint)
            }
            fun icon(index: Int, x: Float, y: Float, side: Float, color: Int) {
                val drawable = icons[index]; drawable.setTint(color)
                drawable.setBounds(x.toInt(), y.toInt(), (x+side).toInt(), (y+side).toInt()); drawable.draw(canvas)
            }
            if (id == "photo") rect(0f,0f,393f,44f,0x4d000000,0f)
            icon(0,14f,12f,20f,p.text)
            rect(353f,5f,34f,34f,p.keys,17f); icon(1,358f,10f,24f,p.text)
            val rowTop = 52f; val pitch = 50.4f; val h = 40.4f
            "qwertyuiop".forEachIndexed { i,c ->
                val x = 4.4f+i*39.3f
                if (borders) rect(x,rowTop,35f,h,p.keys)
                text(c.toString(),x+17.5f,rowTop+27f,24f,p.text)
                text("1234567890"[i].toString(),x+29f,rowTop+11f,10f,p.text)
            }
            "asdfghjkl".forEachIndexed { i,c ->
                val x = 24f+i*38.6f; val y=rowTop+pitch
                if (borders) rect(x,y,34.3f,h,p.keys)
                text(c.toString(),x+17.15f,y+27f,24f,p.text)
            }
            val third=rowTop+pitch*2
            if (borders) { rect(4.4f,third,54f,h,p.functional); rect(334.6f,third,54f,h,p.functional) }
            // The outlined shift silhouette matches the normal keyboard key.
            paint.color=p.functionalText; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f
            val shift=android.graphics.Path().apply { moveTo(23f,third+30f); lineTo(23f,third+20f); lineTo(17f,third+20f); lineTo(31f,third+7f); lineTo(45f,third+20f); lineTo(39f,third+20f); lineTo(39f,third+30f); close() }
            canvas.drawPath(shift,paint);paint.style=Paint.Style.FILL
            "zxcvbnm".forEachIndexed { i,c -> val x=63f+i*38.6f; if(borders) rect(x,third,34.3f,h,p.keys);text(c.toString(),x+17.15f,third+27f,24f,p.text) }
            icon(3,350f,third+9f,24f,p.functionalText)
            val bottom=rowTop+pitch*3
            if(borders) { rect(4.4f,bottom,54f,h,p.functional,24f); rect(63f,bottom,34.3f,h,p.functional); rect(101.6f,bottom,34.3f,h,p.keys);rect(140.2f,bottom,151f,h,p.space);rect(295.6f,bottom,34.3f,h,p.functional) }
            rect(334.6f,bottom,54f,h,p.accent,24f)
            text("?123",31.4f,bottom+26f,14f,p.functionalText)
            icon(5,74f,bottom+6f,12f,p.functionalText);text(",",80.2f,bottom+30f,17f,p.functionalText)
            icon(2,107f,bottom+9f,24f,p.text)
            text("English",215.7f,bottom+26f,14f,p.text);text(".",312.8f,bottom+26f,20f,p.functionalText)
            icon(4,350f,bottom+9f,24f,p.actionText)
            canvas.restoreToCount(saved)
        }
    }
}
