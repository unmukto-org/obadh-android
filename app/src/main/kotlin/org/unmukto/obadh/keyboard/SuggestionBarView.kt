package org.unmukto.obadh.keyboard

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.animation.LinearInterpolator
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

    /** What the left switch reveals in place of the suggestions. */
    enum class Tool(val label: String) {
        LANGUAGE("Language"), CLIPBOARD("Clipboard"), NUMBERS("Numbers"), EMOJI("Emoji"), SETTINGS("Settings"),
    }

    /** English typing instead of Bangla transliteration; drawn on the language tool. */
    var english = false
        set(v) { field = v; invalidate() }

    var onToggleTools: (() -> Unit)? = null
    var onTool: ((Tool) -> Unit)? = null

    /** True while the tools row is showing instead of the suggestions. */
    var toolsOpen = false
        set(v) { field = v; invalidate() }

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

    // ---- action confirmation: a small icon badge at the right edge, not a toast ----
    private var flashAction: ClipboardAction? = null
    private var flashProgress = 0f
    private val flashAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = FLASH_MS
        interpolator = LinearInterpolator()
        addUpdateListener { flashProgress = it.animatedValue as Float; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) { flashAction = null; invalidate() }
            override fun onAnimationCancel(a: android.animation.Animator) { flashAction = null }
        })
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = android.graphics.Color.WHITE
    }
    private val iconPath = Path()
    private val iconRect = RectF()

    /** Briefly pops an icon for [action] at the ribbon's right edge: pop in, hold, fade out. */
    fun flash(action: ClipboardAction) {
        flashAnimator.cancel()
        flashAction = action
        flashProgress = 0f
        flashAnimator.start()
    }

    override fun onDetachedFromWindow() {
        flashAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private fun drawFlash(canvas: Canvas) {
        val action = flashAction ?: return
        val p = flashProgress
        val alpha = when { p < 0.12f -> p / 0.12f; p > 0.75f -> (1f - p) / 0.25f; else -> 1f }.coerceIn(0f, 1f)
        // Overshoot pop on the way in, then rest at full size.
        val scale = when {
            p < 0.12f -> 0.5f + 0.6f * (p / 0.12f)        // 0.5 -> 1.1
            p < 0.22f -> 1.1f - 0.1f * ((p - 0.12f) / 0.10f) // settle 1.1 -> 1.0
            else -> 1f
        }
        val radius = minOf(height * 0.36f, 17f * density) * scale
        val cx = width - 12f * density - radius
        val cy = height / 2f
        badgePaint.color = theme.accent
        badgePaint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(cx, cy, radius, badgePaint)
        iconPaint.alpha = (alpha * 255).toInt()
        drawIcon(canvas, action, cx, cy, radius * 1.15f)
    }

    /** Line icons on a 24-unit grid, scaled to [size] across, centred at (cx, cy). */
    private fun drawIcon(canvas: Canvas, action: ClipboardAction, cx: Float, cy: Float, size: Float) {
        val u = size / 24f
        iconPaint.strokeWidth = 2f * u
        canvas.save()
        canvas.translate(cx - 12f * u, cy - 12f * u)
        canvas.scale(u, u)
        iconPaint.strokeWidth = 2f
        when (action) {
            ClipboardAction.CUT -> {
                canvas.drawCircle(7f, 18f, 2.6f, iconPaint)
                canvas.drawCircle(17f, 18f, 2.6f, iconPaint)
                canvas.drawLine(8.8f, 16.1f, 18f, 4f, iconPaint)
                canvas.drawLine(15.2f, 16.1f, 6f, 4f, iconPaint)
            }
            ClipboardAction.COPY -> {
                iconRect.set(9f, 9f, 20f, 20f)
                canvas.drawRoundRect(iconRect, 2.2f, 2.2f, iconPaint)
                iconPath.reset()
                iconPath.moveTo(16f, 9f); iconPath.lineTo(16f, 6.2f)
                iconPath.quadTo(16f, 5f, 14.8f, 5f); iconPath.lineTo(6.2f, 5f)
                iconPath.quadTo(5f, 5f, 5f, 6.2f); iconPath.lineTo(5f, 14.8f)
                iconPath.quadTo(5f, 16f, 6.2f, 16f); iconPath.lineTo(9f, 16f)
                canvas.drawPath(iconPath, iconPaint)
            }
            ClipboardAction.PASTE -> {
                iconRect.set(5f, 5f, 19f, 21f)
                canvas.drawRoundRect(iconRect, 2.2f, 2.2f, iconPaint)
                iconRect.set(9f, 3f, 15f, 7f)
                canvas.drawRoundRect(iconRect, 1.6f, 1.6f, iconPaint)
                canvas.drawLine(8.5f, 12f, 15.5f, 12f, iconPaint)
                canvas.drawLine(8.5f, 16f, 13.5f, 16f, iconPaint)
            }
        }
        canvas.restore()
    }

    private companion object {
        const val FLASH_MS = 1100L
    }

    /** The switch occupies a square at the left; the suggestions share what is left. */
    private val switchWidth: Float get() = minOf(height.toFloat(), 48f * density)

    /** Line icons on a 24-unit grid in the label colour, centred at (cx, cy). */
    private fun drawToolIcon(canvas: Canvas, tool: Tool, cx: Float, cy: Float, size: Float) {
        val u = size / 24f
        iconPaint.color = theme.label
        iconPaint.alpha = 215
        iconPaint.style = Paint.Style.STROKE
        canvas.save()
        canvas.translate(cx - 12f * u, cy - 12f * u)
        canvas.scale(u, u)
        iconPaint.strokeWidth = 1.9f
        when (tool) {
            Tool.CLIPBOARD -> {
                iconRect.set(5f, 5f, 19f, 21f); canvas.drawRoundRect(iconRect, 2.2f, 2.2f, iconPaint)
                iconRect.set(9f, 3f, 15f, 7f); canvas.drawRoundRect(iconRect, 1.6f, 1.6f, iconPaint)
                canvas.drawLine(8.5f, 12f, 15.5f, 12f, iconPaint)
                canvas.drawLine(8.5f, 16f, 13.5f, 16f, iconPaint)
            }
            Tool.NUMBERS -> {
                // A 3x3 keypad.
                iconPaint.style = Paint.Style.FILL
                for (ix in 0..2) for (iy in 0..2) canvas.drawCircle(6f + ix * 6f, 6f + iy * 6f, 1.6f, iconPaint)
            }
            Tool.EMOJI -> KeyIcons.smiley(canvas, iconPaint, 12f, 12f, 24f)
            Tool.LANGUAGE -> {
                // The current language, in a rounded box: tap to flip.
                iconRect.set(2.5f, 4.5f, 21.5f, 19.5f)
                canvas.drawRoundRect(iconRect, 4f, 4f, iconPaint)
                iconPaint.style = Paint.Style.FILL
                paint.color = theme.label; paint.alpha = 230
                paint.textSize = 8.5f
                canvas.drawText(if (english) "EN" else "বাং", 12f, 12f - (paint.descent() + paint.ascent()) / 2, paint)
                paint.alpha = 255
                iconPaint.style = Paint.Style.STROKE
            }
            Tool.SETTINGS -> {
                // Sliders: two rails with a knob each.
                canvas.drawLine(4f, 8f, 20f, 8f, iconPaint); canvas.drawLine(4f, 16f, 20f, 16f, iconPaint)
                iconPaint.style = Paint.Style.FILL
                canvas.drawCircle(9f, 8f, 3f, iconPaint); canvas.drawCircle(15f, 16f, 3f, iconPaint)
                iconPaint.style = Paint.Style.STROKE
            }
        }
        canvas.restore()
        iconPaint.color = android.graphics.Color.WHITE
        iconPaint.alpha = 255
    }

    private fun drawSwitch(canvas: Canvas) {
        val w = switchWidth
        val cx = w / 2; val cy = height / 2f
        iconPaint.color = if (toolsOpen) theme.accent else theme.label
        iconPaint.alpha = 220
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = 2f * density
        if (toolsOpen) {
            val a = 5f * density
            iconPath.reset()
            iconPath.moveTo(cx + a * 0.6f, cy - a * 1.3f); iconPath.lineTo(cx - a * 0.7f, cy); iconPath.lineTo(cx + a * 0.6f, cy + a * 1.3f)
            canvas.drawPath(iconPath, iconPaint)
        } else {
            // Four rounded tiles: "more".
            val t = 4.6f * density; val g = 2.6f * density
            for (ix in 0..1) for (iy in 0..1) {
                val x = cx - g / 2 - t + ix * (t + g); val y = cy - g / 2 - t + iy * (t + g)
                iconRect.set(x, y, x + t, y + t)
                canvas.drawRoundRect(iconRect, 1.4f * density, 1.4f * density, iconPaint)
            }
        }
        iconPaint.color = android.graphics.Color.WHITE
        iconPaint.alpha = 255
        divider.color = theme.divider
        canvas.drawLine(w, height * 0.25f, w, height * 0.75f, divider)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        drawSwitch(canvas)
        val left = switchWidth
        val slot = (width - left) / 3f
        divider.color = theme.divider
        if (toolsOpen) {
            val tools = Tool.entries
            val each = (width - left) / tools.size
            tools.forEachIndexed { i, t ->
                drawToolIcon(canvas, t, left + each * i + each / 2, height / 2f, minOf(height * 0.5f, 24f * density))
                if (i > 0) canvas.drawLine(left + each * i, height * 0.25f, left + each * i, height * 0.75f, divider)
            }
            drawFlash(canvas)
            return
        }
        items.take(textSlotCount).forEachIndexed { i, item ->
            paint.color = if (item.highlighted) theme.accent else theme.label
            val label = if (item.quoted) "\u201C${item.text}\u201D" else item.text
            canvas.drawText(fit(label, slot - 16f * density), left + slot * i + slot / 2, baseline(), paint)
        }
        if (emojis.isNotEmpty()) {
            val each = slot / emojis.size
            paint.color = theme.label
            paint.textSize = 24f * density
            emojis.forEachIndexed { i, e -> canvas.drawText(e.display, left + slot * 2 + each * i + each / 2, baseline(), paint) }
        }
        val dividers = if (emojis.isEmpty()) items.size.coerceAtMost(3) else 3
        for (i in 1 until dividers) {
            canvas.drawLine(left + slot * i, height * 0.25f, left + slot * i, height * 0.75f, divider)
        }
        drawFlash(canvas)
    }

    /** Shrinks the text to fit [maxWidth], then ellipsizes, so neighbouring slots never overlap. */
    private fun fit(label: String, maxWidth: Float): String {
        var size = 18f * density
        paint.textSize = size
        while (paint.measureText(label) > maxWidth && size > 13f * density) {
            size -= density
            paint.textSize = size
        }
        return android.text.TextUtils.ellipsize(label, android.text.TextPaint(paint), maxWidth, android.text.TextUtils.TruncateAt.END).toString()
    }

    private fun baseline() = height / 2f - (paint.descent() + paint.ascent()) / 2

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val left = switchWidth
        if (e.x < left) { onToggleTools?.invoke(); return true }
        if (toolsOpen) {
            val tools = Tool.entries
            val index = ((e.x - left) / ((width - left) / tools.size)).toInt().coerceIn(0, tools.size - 1)
            onTool?.invoke(tools[index])
            return true
        }
        val slot = (width - left) / 3f
        val index = ((e.x - left) / slot).toInt().coerceIn(0, 2)
        if (emojis.isNotEmpty() && index == 2) {
            val within = ((e.x - left - slot * 2) / (slot / emojis.size)).toInt().coerceIn(0, emojis.size - 1)
            onSelectEmoji?.invoke(emojis[within])
        } else {
            items.getOrNull(index)?.let { onSelect?.invoke(it) }
        }
        return true
    }
}
