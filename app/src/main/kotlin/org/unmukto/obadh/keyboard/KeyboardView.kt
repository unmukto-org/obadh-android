package org.unmukto.obadh.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.unmukto.obadh.engine.BackspaceDeletionUnit
import org.unmukto.obadh.engine.BackspaceRepeatPolicy
import kotlin.math.abs

interface KeyboardViewListener {
    fun onKey(key: Key)
    fun onBackspace(unit: BackspaceDeletionUnit)
}

/**
 * One custom view draws every key and receives every touch: keys are not child
 * views, so gaps between and around keys are live, and a touch resolves to the
 * nearest key (row band by y, nearest centre by x). Same idea as iOS's single
 * touch surface, without iOS's transparent-region constraint.
 */
class KeyboardView(context: Context) : View(context) {
    var listener: KeyboardViewListener? = null
    var theme: KeyboardTheme = KeyboardTheme.forContext(context)
    var mode: KeyboardMode = KeyboardMode.LETTERS
        set(v) { field = v; rebuildRows() }
    var includesGlobeKey = true
        set(v) { field = v; rebuildRows() }
    var shiftActive = false
        set(v) { field = v; invalidate() }
    var capsLock = false
        set(v) { field = v; invalidate() }

    private var rows = KeyboardLayoutProvider.rows(KeyboardMode.LETTERS, true)
    private fun rebuildRows() { rows = KeyboardLayoutProvider.rows(mode, includesGlobeKey); relayout() }
    private class Cell(val key: Key, val rect: RectF)
    private var cells: List<List<Cell>> = emptyList()
    private var pressed: Cell? = null
    private var downAt = 0L

    private val density = resources.displayMetrics.density
    private val gap = 5f * density
    private val sidePad = 3f * density
    private val vertPad = 6f * density
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())

    private val repeat = object : Runnable {
        override fun run() {
            val stage = BackspaceRepeatPolicy.stage(SystemClock.uptimeMillis() - downAt)
            if (stage != null) listener?.onBackspace(stage.unit)
            handler.postDelayed(this, stage?.intervalMs ?: 20L)
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, (ROW_DP * 4 * density).toInt() + (vertPad * 2).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout()

    private fun relayout() {
        if (width == 0 || height == 0) return
        val rowH = (height - vertPad * 2) / rows.size
        cells = rows.mapIndexed { r, row ->
            val total = (row.weights.sum() + row.sideFlex * 2).toFloat()
            val usable = width - sidePad * 2 - gap * (row.keys.size - 1)
            val unit = usable / total
            var x = sidePad + row.sideFlex.toFloat() * unit
            val top = vertPad + r * rowH
            row.keys.mapIndexed { i, key ->
                val w = row.weights[i].toFloat() * unit
                // Rects include the gap so the whole surface is hittable; drawing insets it.
                Cell(key, RectF(x, top, x + w, top + rowH)).also { x += w + gap }
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        for (row in cells) for (cell in row) drawKey(canvas, cell)
    }

    private fun drawKey(canvas: Canvas, cell: Cell) {
        val special = cell.key !is Key.Character && cell.key !is Key.Symbol && cell.key !is Key.Space
        keyPaint.color = when {
            cell === pressed -> theme.keyPressed
            cell.key is Key.Shift && (shiftActive || capsLock) -> theme.key
            special -> theme.specialKey
            else -> theme.key
        }
        val r = RectF(cell.rect.left, cell.rect.top + gap / 2, cell.rect.right, cell.rect.bottom - gap / 2)
        canvas.drawRoundRect(r, 6 * density, 6 * density, keyPaint)

        val label = label(cell.key)
        textPaint.color = theme.label
        textPaint.typeface = if (cell.key is Key.Character || cell.key is Key.Symbol) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        textPaint.textSize = (if (label.length > 1 && cell.key !is Key.Symbol) 15f else 22f) * density
        val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, r.centerX(), y, textPaint)
    }

    private fun label(key: Key): String = when (key) {
        is Key.Character -> if (shiftActive || capsLock) key.value.uppercase() else key.value
        is Key.Symbol -> key.label
        Key.Shift -> if (capsLock) "⇪" else "⇧"
        Key.Backspace -> "⌫"
        is Key.ModeSwitch -> key.label
        Key.Globe -> "🌐"
        Key.Space -> ""
        Key.Return -> "⏎"
    }

    private fun resolve(x: Float, y: Float): Cell? {
        if (cells.isEmpty()) return null
        val row = cells.firstOrNull { y < it[0].rect.bottom } ?: cells.last()
        return row.firstOrNull { x >= it.rect.left && x <= it.rect.right }
            ?: row.minByOrNull { abs(x - it.rect.centerX()) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = resolve(e.x, e.y)
                downAt = SystemClock.uptimeMillis()
                invalidate()
                if (pressed?.key is Key.Backspace) {
                    listener?.onBackspace(BackspaceDeletionUnit.CHARACTER)
                    handler.postDelayed(repeat, 380L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val now = resolve(e.x, e.y)
                if (now !== pressed && pressed?.key !is Key.Backspace) { pressed = now; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeat)
                val cell = resolve(e.x, e.y).takeIf { pressed?.key !is Key.Backspace }
                val wasBackspace = pressed?.key is Key.Backspace
                pressed = null
                invalidate()
                if (!wasBackspace && cell != null) listener?.onKey(cell.key)
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(repeat); pressed = null; invalidate() }
        }
        return true
    }

    private companion object { const val ROW_DP = 50 }
}
