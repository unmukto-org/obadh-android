package org.unmukto.obadh.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * The ribbon: up to three slots. A quoted literal means "keep my spelling";
 * slots are always tappable. Up to three emoji can take over the third slot.
 */
class SuggestionBarView(context: Context) : View(context) {
    data class Item(val text: String, val quoted: Boolean = false, val isEmoji: Boolean = false, val highlighted: Boolean = false)

    var onSelect: ((Item) -> Unit)? = null
    var theme: KeyboardTheme = KeyboardTheme.forContext(context)
    var items: List<Item> = emptyList()
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val divider = Paint().apply { strokeWidth = density }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (44 * density).toInt())

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        if (items.isEmpty()) return
        val slot = width / 3f
        divider.color = theme.divider
        items.take(3).forEachIndexed { i, item ->
            paint.color = if (item.highlighted) theme.accent else theme.label
            paint.textSize = (if (item.isEmoji) 24f else 18f) * density
            val label = if (item.quoted) "“${item.text}”" else item.text
            canvas.drawText(label, slot * i + slot / 2, height / 2f - (paint.descent() + paint.ascent()) / 2, paint)
            if (i > 0) canvas.drawLine(slot * i, height * 0.25f, slot * i, height * 0.75f, divider)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val index = (e.x / (width / 3f)).toInt().coerceIn(0, 2)
            items.getOrNull(index)?.let { onSelect?.invoke(it) }
        }
        return true
    }
}
