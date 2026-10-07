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
 * One custom view draws every key and receives every touch: keys are not child views, so gaps
 * between and around keys are live, and a touch resolves to the nearest key (row band by y,
 * nearest centre by x). The same single touch surface obadh-ios uses.
 *
 * On a tablet the layout family is chosen from the device's smallest width, with taller keys,
 * extra keys, and a secondary glyph per key that a downward flick or a long press emits.
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

    /**
     * The tablet family, from the device's PORTRAIT width. [forcedSmallestWidthDp] exists so the
     * debug preview can show every family on one phone; it is null in normal use.
     */
    var forcedSmallestWidthDp: Int? = null
        set(v) { field = v; rebuildRows(); requestLayout() }

    val family: TabletFamily?
        get() = TabletFamily.forSmallestWidthDp(forcedSmallestWidthDp ?: resources.configuration.smallestScreenWidthDp)

    /** Landscape is its own geometry, not a stretched portrait. Forced only by the debug preview. */
    var forcedLandscape: Boolean? = null
        set(v) { field = v; rebuildRows(); requestLayout() }

    val landscape: Boolean
        get() = forcedLandscape ?: (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)

    private var rows = KeyboardLayoutProvider.rows(KeyboardMode.LETTERS, true, family, landscape)

    private class Cell(val key: Key, val rect: RectF)
    private var cells: List<List<Cell>> = emptyList()
    private var pressed: Cell? = null
    private var downAt = 0L
    private var downY = 0f
    private var secondaryFired = false

    private val density = resources.displayMetrics.density
    private val gap: Float get() = (family?.gapDp(landscape) ?: 5f) * density
    private val topPad = 6f * density

    /**
     * Safe area under the bottom row. The system navigation strip sits below the view, but keys
     * pressed flush against it are hard to hit and look cramped, so the keys stop short of it.
     */
    val bottomPad: Float
        get() = (when {
            family != null -> 14f
            landscape -> 10f
            else -> 14f
        }) * density
    /** One normal key row. A phone's landscape rows are shorter so the app stays visible above. */
    private val rowHeight: Float
        get() = (family?.rowHeightDp(landscape) ?: if (landscape) PHONE_LANDSCAPE_ROW_DP else 50f) * density

    /** Edge margin; a landscape phone also centres its keys in a capped width rather than stretching to the glass. */
    private fun sidePad(viewWidth: Int): Float {
        val base = (family?.marginDp(landscape) ?: 3f) * density
        if (family != null || !landscape) return base
        val cap = PHONE_LANDSCAPE_MAX_WIDTH_DP * density
        return maxOf(base, (viewWidth - cap) / 2f)
    }

    /** Total height of the key block, honouring rows that are shorter than the rest. */
    private fun keyBlockHeight(): Float = rows.sumOf { it.heightFactor }.toFloat() * rowHeight
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

    /** Holding a key that has a secondary glyph emits it, like a flick (tablet layouts). */
    private val secondaryLongPress = Runnable {
        val cell = pressed ?: return@Runnable
        val secondary = KeyboardLayoutProvider.secondaryFor(cell.key) ?: return@Runnable
        secondaryFired = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        listener?.onKey(secondary)
    }

    private fun rebuildRows() {
        rows = KeyboardLayoutProvider.rows(mode, includesGlobeKey, family, landscape)
        relayout()
    }

    /** Rotation or a resize: the family is a device property, but orientation and height are not. */
    fun refreshForConfiguration() {
        rebuildRows()
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, (keyBlockHeight() + topPad + bottomPad).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = relayout()

    private fun relayout() {
        if (width == 0 || height == 0) return
        val side = sidePad(width)
        val unitHeight = (height - topPad - bottomPad) / rows.sumOf { it.heightFactor }.toFloat()
        var top = topPad
        cells = rows.map { row ->
            val rowH = unitHeight * row.heightFactor.toFloat()
            val total = (row.weights.sum() + row.leadingFlex + row.trailingFlex).toFloat()
            val usable = width - side * 2 - gap * (row.keys.size - 1)
            val unit = usable / total
            var x = side + row.leadingFlex.toFloat() * unit
            val rowTop = top
            top += rowH
            row.keys.mapIndexed { i, key ->
                val w = row.weights[i].toFloat() * unit
                Cell(key, RectF(x, rowTop, x + w, rowTop + rowH)).also { x += w + gap }
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
        val locked = cell.key is Key.CapsLock && capsLock
        keyPaint.color = when {
            cell === pressed -> theme.keyPressed
            cell.key is Key.Shift && (shiftActive || capsLock) -> theme.key
            locked -> theme.key
            special -> theme.specialKey
            else -> theme.key
        }
        val vGap = if (family != null) minOf(gap, 10f * density) else if (landscape) 6f * density else gap
        val r = RectF(cell.rect.left, cell.rect.top + vGap / 2, cell.rect.right, cell.rect.bottom - vGap / 2)
        canvas.drawRoundRect(r, 6 * density, 6 * density, keyPaint)

        val label = label(cell.key)
        textPaint.color = theme.label
        textPaint.typeface = if (cell.key is Key.Character || cell.key is Key.Symbol) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        textPaint.textSize = (if (label.length > 1 && cell.key !is Key.Symbol) 15f else letterSp()) * density
        val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, r.centerX(), y, textPaint)

        // The secondary glyph, in the key's top-left, quieter than the primary.
        if (family != null) KeyboardLayoutProvider.secondaryFor(cell.key)?.let {
            textPaint.textSize = 11f * density
            textPaint.alpha = 140
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(it.label, r.left + 6 * density, r.top + 15 * density, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.alpha = 255
        }
    }

    /** Native tablet letter type is a per-orientation constant: it does not scale with the key. */
    private fun letterSp(): Float = when {
        family != null -> if (landscape) 26f else 24f
        landscape -> 20f
        else -> 22f
    }

    private fun label(key: Key): String = when (key) {
        is Key.Character -> if (shiftActive || capsLock) key.value.uppercase() else key.value
        is Key.Symbol -> key.label
        Key.Shift -> if (capsLock) "⇪" else "⇧"
        Key.Backspace -> "⌫"
        is Key.ModeSwitch -> key.label
        Key.Globe -> "🌐"
        Key.Emoji -> "🙂"
        Key.Space -> ""
        Key.Return -> "⏎"
        Key.Tab -> "⇥"
        Key.CapsLock -> "⇪"
        Key.HideKeyboard -> "⌄"
    }

    private fun resolve(x: Float, y: Float): Cell? {
        if (cells.isEmpty()) return null
        val row = cells.firstOrNull { y < it[0].rect.bottom } ?: cells.last()
        return row.firstOrNull { x >= it.rect.left && x <= it.rect.right }
            ?: row.minByOrNull { abs(x - it.rect.centerX()) }
    }

    private companion object {
        const val PHONE_LANDSCAPE_ROW_DP = 38f
        const val PHONE_LANDSCAPE_MAX_WIDTH_DP = 820f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = resolve(e.x, e.y)
                downAt = SystemClock.uptimeMillis()
                downY = e.y
                secondaryFired = false
                invalidate()
                val key = pressed?.key
                if (key is Key.Backspace) {
                    listener?.onBackspace(BackspaceDeletionUnit.CHARACTER)
                    handler.postDelayed(repeat, 380L)
                } else if (family != null && key != null && KeyboardLayoutProvider.secondaryFor(key) != null) {
                    handler.postDelayed(secondaryLongPress, 420L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val now = resolve(e.x, e.y)
                if (pressed?.key !is Key.Backspace && !secondaryFired) {
                    // A downward flick on a key with a secondary emits it, like iPadOS.
                    val key = pressed?.key
                    if (family != null && key != null && e.y - downY > 22 * density) {
                        KeyboardLayoutProvider.secondaryFor(key)?.let {
                            secondaryFired = true
                            handler.removeCallbacks(secondaryLongPress)
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            listener?.onKey(it)
                        }
                    }
                    if (!secondaryFired && now !== pressed) { pressed = now; invalidate() }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeat)
                handler.removeCallbacks(secondaryLongPress)
                val wasBackspace = pressed?.key is Key.Backspace
                val cell = if (wasBackspace || secondaryFired) null else resolve(e.x, e.y)
                pressed = null
                invalidate()
                if (cell != null) listener?.onKey(cell.key)
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeat)
                handler.removeCallbacks(secondaryLongPress)
                pressed = null
                invalidate()
            }
        }
        return true
    }
}
