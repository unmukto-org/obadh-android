package org.unmukto.obadh.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * The ribbon: three slots. A quoted literal means "keep my spelling"; every shown slot is
 * tappable. Up to three emoji take over the third slot (the top two text candidates always
 * survive), each tappable on its own.
 */
class SuggestionBarView(context: Context) : View(context) {
    data class Item(val text: String, val quoted: Boolean = false, val highlighted: Boolean = false)

    /** [base] is what the index returned; [display] is the remembered skin tone, if any. */
    data class EmojiSlot(val base: String, val display: String = base)

    var onSelect: ((Item) -> Unit)? = null
    var onSelectEmoji: ((EmojiSlot) -> Unit)? = null
    var theme: KeyboardTheme = KeyboardTheme.forContext(context)
    var items: List<Item> = emptyList()
        set(v) { field = v; invalidate() }
    var emojis: List<EmojiSlot> = emptyList()
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val divider = Paint().apply { strokeWidth = density }

    /** Shorter in a phone's landscape, where the app above needs every line. */
    var heightDp: Float = 44f
        set(v) { field = v; requestLayout() }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (heightDp * density).toInt())

    private val textSlotCount: Int get() = if (emojis.isEmpty()) 3 else 2

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val slot = width / 3f
        divider.color = theme.divider
        items.take(textSlotCount).forEachIndexed { i, item ->
            paint.color = if (item.highlighted) theme.accent else theme.label
            paint.textSize = 18f * density
            val label = if (item.quoted) "\u201C${item.text}\u201D" else item.text
            canvas.drawText(label, slot * i + slot / 2, baseline(), paint)
        }
        if (emojis.isNotEmpty()) {
            val each = slot / emojis.size
            paint.color = theme.label
            paint.textSize = 24f * density
            emojis.forEachIndexed { i, e -> canvas.drawText(e.display, slot * 2 + each * i + each / 2, baseline(), paint) }
        }
        val dividers = if (emojis.isEmpty()) items.size.coerceAtMost(3) else 3
        for (i in 1 until dividers) {
            canvas.drawLine(slot * i, height * 0.25f, slot * i, height * 0.75f, divider)
        }
    }

    private fun baseline() = height / 2f - (paint.descent() + paint.ascent()) / 2

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val slot = width / 3f
        val index = (e.x / slot).toInt().coerceIn(0, 2)
        if (emojis.isNotEmpty() && index == 2) {
            val within = ((e.x - slot * 2) / (slot / emojis.size)).toInt().coerceIn(0, emojis.size - 1)
            onSelectEmoji?.invoke(emojis[within])
        } else {
            items.getOrNull(index)?.let { onSelect?.invoke(it) }
        }
        return true
    }
}
