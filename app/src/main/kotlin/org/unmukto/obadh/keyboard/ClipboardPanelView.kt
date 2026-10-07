package org.unmukto.obadh.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import org.unmukto.obadh.settings.ClipEntry
import kotlin.math.abs
import kotlin.math.max

interface ClipboardPanelListener {
    fun onPaste(text: String)
    fun onDelete(text: String)
    fun onTogglePin(text: String)
    fun onClear()
    fun onClose()
}

/**
 * The clipboard panel: what was copied, newest first, as tappable cards. Tap pastes, the pin keeps
 * one at the top, the cross removes one, Clear removes all but the pins. Canvas-drawn like the emoji panel, with its own scroll.
 */
class ClipboardPanelView(context: Context) : View(context) {
    var listener: ClipboardPanelListener? = null
    var theme: KeyboardTheme = KeyboardTheme.forContext(context)
    var bottomInset = 0f
        set(v) { field = v; invalidate() }
    var collecting = true
        set(v) { field = v; invalidate() }

    var items: List<ClipEntry> = emptyList()
        set(v) { field = v; layouts = emptyList(); scrollY0 = 0f.coerceAtLeast(0f); clampScroll(); invalidate() }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var layouts: List<StaticLayout> = emptyList()
    private var layoutWidth = 0

    private val headerH get() = 48 * d
    private val margin get() = 10 * d
    private val gap get() = 8 * d
    private val cardPad get() = 12 * d
    private val crossW get() = 40 * d
    private val pinW get() = 36 * d
    private val cardH get() = 62 * d

    private var scrollY0 = 0f
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val contentHeight get() = items.size * (cardH + gap) + gap
    private val viewport get() = height - headerH - bottomInset

    private fun clampScroll() {
        scrollY0 = scrollY0.coerceIn(0f, max(0f, contentHeight - viewport))
    }

    private fun ensureLayouts() {
        val w = (width - 2 * margin - 2 * cardPad - crossW - pinW).toInt()
        if (w <= 0 || (layouts.size == items.size && layoutWidth == w)) return
        layoutWidth = w
        textPaint.textSize = 15f * d
        layouts = items.map {
            StaticLayout.Builder.obtain(it.text.replace('\n', ' '), 0, it.text.length, textPaint, w)
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layouts = emptyList(); clampScroll() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        ensureLayouts()
        // Cards, clipped under the header and above the safe area.
        canvas.save()
        canvas.clipRect(0f, headerH, width.toFloat(), height - bottomInset)
        if (items.isEmpty()) {
            paint.color = theme.label; paint.alpha = 150
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 15f * d
            val msg = if (collecting) "Text you copy will appear here" else "Clipboard history is turned off in Obadh settings"
            canvas.drawText(msg, width / 2f, headerH + viewport / 2f, paint)
            paint.alpha = 255; paint.textAlign = Paint.Align.LEFT
        }
        textPaint.color = theme.label
        for (i in items.indices) {
            val top = headerH + gap + i * (cardH + gap) - scrollY0
            if (top > height || top + cardH < headerH) continue
            rect.set(margin, top, width - margin, top + cardH)
            paint.color = theme.key
            canvas.drawRoundRect(rect, 10 * d, 10 * d, paint)
            if (items[i].pinned) {
                paint.color = theme.accent; paint.alpha = 90
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d
                canvas.drawRoundRect(rect, 10 * d, 10 * d, paint)
                paint.style = Paint.Style.FILL; paint.alpha = 255
            }
            val layout = layouts.getOrNull(i) ?: continue
            canvas.save()
            canvas.translate(margin + cardPad, top + (cardH - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
            drawPin(canvas, width - margin - crossW - pinW / 2, top + cardH / 2, items[i].pinned)
            drawCross(canvas, width - margin - crossW / 2, top + cardH / 2)
        }
        canvas.restore()
        drawHeader(canvas)
    }

    /** A push-pin: filled and accent-coloured when pinned, an outline otherwise. */
    private fun drawPin(canvas: Canvas, cx: Float, cy: Float, pinned: Boolean) {
        paint.color = if (pinned) theme.accent else theme.label
        paint.alpha = if (pinned) 255 else 130
        paint.strokeWidth = 1.6f * d; paint.strokeCap = Paint.Cap.ROUND
        paint.style = if (pinned) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val u = d
        canvas.save()
        canvas.rotate(35f, cx, cy)
        canvas.drawRoundRect(cx - 3 * u, cy - 8 * u, cx + 3 * u, cy + 1 * u, 1.5f * u, 1.5f * u, paint)
        canvas.drawLine(cx - 5 * u, cy + 1 * u, cx + 5 * u, cy + 1 * u, paint)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy + 1 * u, cx, cy + 8 * u, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL; paint.alpha = 255
    }

    private fun drawCross(canvas: Canvas, cx: Float, cy: Float) {
        paint.color = theme.label; paint.alpha = 130
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.6f * d; paint.strokeCap = Paint.Cap.ROUND
        val a = 5f * d
        canvas.drawLine(cx - a, cy - a, cx + a, cy + a, paint)
        canvas.drawLine(cx - a, cy + a, cx + a, cy - a, paint)
        paint.style = Paint.Style.FILL; paint.alpha = 255
    }

    private fun drawHeader(canvas: Canvas) {
        paint.color = theme.background
        canvas.drawRect(0f, 0f, width.toFloat(), headerH, paint)
        paint.textSize = 16f * d
        val base = headerH / 2 - (paint.descent() + paint.ascent()) / 2
        paint.color = theme.accent; paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ABC", margin + 6 * d, base, paint)
        paint.color = theme.label; paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
        canvas.drawText("Clipboard", width / 2f, base, paint)
        paint.isFakeBoldText = false
        if (items.isNotEmpty()) {
            paint.color = theme.accent; paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Clear", width - margin - 6 * d, base, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        paint.color = theme.divider
        canvas.drawRect(0f, headerH - d, width.toFloat(), headerH, paint)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY0 = scroller.currY.toFloat(); clampScroll(); invalidate()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocity?.recycle(); velocity = VelocityTracker.obtain()
                downX = e.x; downY = e.y; lastY = e.y; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                if (!dragging && abs(e.y - downY) > slop) dragging = true
                if (dragging) {
                    scrollY0 -= e.y - lastY
                    clampScroll(); invalidate()
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                velocity?.addMovement(e)
                if (dragging) {
                    velocity?.computeCurrentVelocity(1000)
                    val vy = -(velocity?.yVelocity ?: 0f)
                    scroller.fling(0, scrollY0.toInt(), 0, vy.toInt(), 0, 0, 0, max(0f, contentHeight - viewport).toInt())
                    invalidate()
                } else tap(e.x, e.y)
                velocity?.recycle(); velocity = null
            }
            MotionEvent.ACTION_CANCEL -> { velocity?.recycle(); velocity = null }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (y < headerH) {
            when {
                x < width * 0.28f -> listener?.onClose()
                x > width * 0.72f && items.isNotEmpty() -> listener?.onClear()
            }
            return
        }
        if (y > height - bottomInset) return
        val i = ((y - headerH + scrollY0 - gap) / (cardH + gap)).toInt()
        val top = headerH + gap + i * (cardH + gap) - scrollY0
        if (i !in items.indices || y > top + cardH) return
        val text = items[i].text
        when {
            x > width - margin - crossW -> listener?.onDelete(text)
            x > width - margin - crossW - pinW -> listener?.onTogglePin(text)
            else -> listener?.onPaste(text)
        }
    }
}
