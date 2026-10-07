package org.unmukto.obadh.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import org.unmukto.obadh.engine.BackspaceDeletionUnit
import org.unmukto.obadh.engine.BackspaceRepeatPolicy
import org.unmukto.obadh.keyboard.KeyboardTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

interface EmojiPanelListener {
    fun onEmojiSelected(emoji: String, base: String)
    fun onReturnToKeyboard()
    fun onBackspace(unit: BackspaceDeletionUnit)
    /** A tone was picked from the long-press popup; persist it. */
    fun onVariantPicked(base: String, selected: String)
}

/**
 * The emoji panel: a horizontally scrolling 4-row grid of sections (recents first), a
 * category bar, and backspace. Drawn on a Canvas like the keyboard, with its own fling and
 * a long-press popup for skin tones. Receives the catalog lazily (never on the typing path).
 */
class EmojiPanelView(context: Context) : View(context) {
    var listener: EmojiPanelListener? = null
    var theme: KeyboardTheme = KeyboardTheme.forContext(context)

    private class Cell(val display: String, val base: EmojiItem?)
    private class Section(val category: EmojiCategory, val cells: List<Cell>, var startX: Float = 0f, var width: Float = 0f)

    private var store: EmojiDataStore? = null
    private var recents: List<String> = emptyList()
    private var preferences: Map<String, String> = emptyMap()
    private var sections: List<Section> = emptyList()
    /** Only emoji the device font can draw: older Android versions lack newer emoji. */
    private val drawable = HashMap<String, Boolean>()

    private val density = resources.displayMetrics.density
    private val rows = 4
    private val cellSide = 40f * density
    private val colSpacing = 4f * density
    private val rowSpacing = 2f * density
    private val sectionGap = 12f * density
    private val leadInset = 8f * density
    private val headerH = 22f * density
    private val barH = 44f * density
    private val colW get() = cellSide + colSpacing

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint().apply { strokeWidth = density }

    private val scroller = OverScroller(context)
    private var scrollPos = 0f
    private var velocity: VelocityTracker? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var pressed: Pair<Section, Int>? = null
    private var backspaceDown = false
    private var backspaceAt = 0L

    // long-press popup
    private var popupBase: EmojiItem? = null
    private var popupOptions: List<EmojiItem> = emptyList()
    private var popupAnchor: RectF? = null
    private var popupHover = -1

    private val longPress = Runnable { showPopupForPressed() }
    private val repeat = object : Runnable {
        override fun run() {
            val stage = BackspaceRepeatPolicy.stage(SystemClock.uptimeMillis() - backspaceAt)
            if (stage != null) listener?.onBackspace(stage.unit)
            handler.postDelayed(this, stage?.intervalMs ?: 20L)
        }
    }

    // ---------------------------------------------------------------- data

    fun configure(dataStore: EmojiDataStore, recentEmojis: List<String>, variantPreferences: Map<String, String>) {
        store = dataStore
        recents = recentEmojis
        preferences = variantPreferences
        rebuild()
        scrollPos = 0f
        invalidate()
    }

    /** Mirror a tap into the in-memory recents so the row is right without a store round trip. */
    fun recordRecent(emoji: String) {
        recents = (listOf(emoji) + recents.filter { it != emoji }).take(EmojiRecentStore.DEFAULT_LIMIT)
        rebuild()
        invalidate()
    }

    fun updatePreference(base: String, selected: String) {
        preferences = if (base == selected) preferences - base else preferences + (base to selected)
        rebuild()
        invalidate()
    }

    private val pageCapacity: Int
        get() = rows * max(1, ((width - leadInset + colSpacing) / colW).toInt())

    private fun canDraw(emoji: String): Boolean =
        drawable.getOrPut(emoji) { paint.hasGlyph(emoji) }

    private fun rebuild() {
        val s = store ?: return
        paint.textSize = 26f * density
        val built = ArrayList<Section>()
        val recentItems = recents.filter(::canDraw).take(pageCapacity)
            .map { Cell(it, s.item(it)) }
        built += Section(EmojiCategory.RECENTS, recentItems)
        for (cat in EmojiCategory.visible) {
            if (cat == EmojiCategory.RECENTS) continue
            val cells = s.items(cat).filter { canDraw(it.emoji) }
                .map { Cell(preferences[it.emoji]?.takeIf(::canDraw) ?: it.emoji, it) }
            if (cells.isNotEmpty()) built += Section(cat, cells)
        }
        var x = leadInset
        for (sec in built) {
            val columns = if (sec.cells.isEmpty()) max(1, ((width - leadInset) / colW).toInt())
            else (sec.cells.size + rows - 1) / rows
            sec.startX = x
            sec.width = columns * colW
            x += sec.width + sectionGap
        }
        sections = built
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

    private val contentWidth: Float get() = (sections.lastOrNull()?.let { it.startX + it.width } ?: 0f) + leadInset
    private val maxScroll: Float get() = max(0f, contentWidth - width)

    private val gridTop: Float get() = headerH
    private val gridHeight: Float get() = height - barH - headerH
    private val rowH: Float get() = gridHeight / rows

    // --------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        if (scroller.computeScrollOffset()) {
            scrollPos = scroller.currX.toFloat()
            postInvalidateOnAnimation()
        }
        if (store == null) {
            paint.color = theme.label; paint.textSize = 14f * density
            canvas.drawText("…", width / 2f, height / 2f, paint)
            drawBar(canvas)
            return
        }
        for (sec in sections) {
            val left = sec.startX - scrollPos
            if (left > width || left + sec.width < 0) continue
            paint.color = theme.label; paint.alpha = 160
            paint.textSize = 12f * density; paint.textAlign = Paint.Align.LEFT
            canvas.drawText(sec.category.label.uppercase(), max(left, leadInset), headerH * 0.7f, paint)
            paint.textAlign = Paint.Align.CENTER; paint.alpha = 255

            if (sec.cells.isEmpty()) {
                paint.textSize = 14f * density; paint.alpha = 140
                canvas.drawText("Emoji you use appear here", left + sec.width / 2f, gridTop + gridHeight / 2f, paint)
                paint.alpha = 255
                continue
            }
            paint.textSize = 26f * density
            sec.cells.forEachIndexed { i, cell ->
                val col = i / rows
                val row = i % rows
                val cx = left + col * colW + cellSide / 2f
                if (cx < -cellSide || cx > width + cellSide) return@forEachIndexed
                val cy = gridTop + row * rowH + rowH / 2f
                if (pressed?.first === sec && pressed?.second == i && popupBase == null) {
                    fill.color = theme.keyPressed
                    canvas.drawRoundRect(cx - cellSide / 2, cy - rowH / 2 + 1, cx + cellSide / 2, cy + rowH / 2 - 1, 8 * density, 8 * density, fill)
                }
                paint.color = theme.label
                canvas.drawText(cell.display, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
            }
        }
        drawBar(canvas)
        drawPopup(canvas)
    }

    private fun categoryAtScroll(): EmojiCategory {
        val probe = scrollPos + width * 0.25f
        return sections.lastOrNull { it.startX <= probe }?.category ?: EmojiCategory.RECENTS
    }

    private val barCategories = EmojiCategory.visible
    private fun barRects(): List<RectF> {
        val abc = 56f * density
        val back = 56f * density
        val inner = (width - abc - back) / barCategories.size
        val top = height - barH
        val rects = ArrayList<RectF>()
        rects += RectF(0f, top, abc, height.toFloat())
        barCategories.forEachIndexed { i, _ -> rects += RectF(abc + i * inner, top, abc + (i + 1) * inner, height.toFloat()) }
        rects += RectF(width - back, top, width.toFloat(), height.toFloat())
        return rects
    }

    private fun drawBar(canvas: Canvas) {
        val top = height - barH
        line.color = theme.divider
        canvas.drawLine(0f, top, width.toFloat(), top, line)
        val rects = barRects()
        val current = categoryAtScroll()
        paint.color = theme.label
        paint.textSize = 15f * density
        canvas.drawText("ABC", rects.first().centerX(), rects.first().centerY() - (paint.descent() + paint.ascent()) / 2, paint)
        paint.textSize = 20f * density
        barCategories.forEachIndexed { i, cat ->
            val r = rects[i + 1]
            if (cat == current) {
                fill.color = theme.keyPressed
                canvas.drawRoundRect(r.left + 4 * density, r.top + 6 * density, r.right - 4 * density, r.bottom - 6 * density, 10 * density, 10 * density, fill)
            }
            paint.color = theme.label
            canvas.drawText(cat.glyph, r.centerX(), r.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
        }
        val b = rects.last()
        if (backspaceDown) { fill.color = theme.keyPressed; canvas.drawRoundRect(b.left + 6 * density, b.top + 6 * density, b.right - 6 * density, b.bottom - 6 * density, 10 * density, 10 * density, fill) }
        paint.textSize = 22f * density
        canvas.drawText("⌫", b.centerX(), b.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    }

    private fun drawPopup(canvas: Canvas) {
        val anchor = popupAnchor ?: return
        if (popupOptions.isEmpty()) return
        val w = popupOptions.size * cellSide + 12 * density
        val h = cellSide + 12 * density
        var left = anchor.centerX() - w / 2f
        left = min(max(left, 4 * density), width - w - 4 * density)
        val top = max(0f, anchor.top - h - 4 * density)
        fill.color = theme.key
        canvas.drawRoundRect(left, top, left + w, top + h, 14 * density, 14 * density, fill)
        popupOptions.forEachIndexed { i, o ->
            val cx = left + 6 * density + i * cellSide + cellSide / 2f
            if (i == popupHover) {
                fill.color = theme.accent; fill.alpha = 90
                canvas.drawRoundRect(cx - cellSide / 2, top + 4 * density, cx + cellSide / 2, top + h - 4 * density, 10 * density, 10 * density, fill)
                fill.alpha = 255
            }
            paint.color = theme.label; paint.textSize = 26f * density
            canvas.drawText(o.emoji, cx, top + h / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        }
        popupRect = RectF(left, top, left + w, top + h)
    }
    private var popupRect: RectF? = null

    // ----------------------------------------------------------------- touch

    private fun hitCell(x: Float, y: Float): Pair<Section, Int>? {
        if (y < gridTop || y >= height - barH) return null
        val cx = x + scrollPos
        val sec = sections.lastOrNull { it.startX <= cx && cx < it.startX + it.width } ?: return null
        val col = ((cx - sec.startX) / colW).toInt()
        val row = ((y - gridTop) / rowH).toInt().coerceIn(0, rows - 1)
        val index = col * rows + row
        return if (index in sec.cells.indices) sec to index else null
    }

    private fun cellRect(sec: Section, index: Int): RectF {
        val left = sec.startX - scrollPos + (index / rows) * colW
        val top = gridTop + (index % rows) * rowH
        return RectF(left, top, left + cellSide, top + rowH)
    }

    private fun showPopupForPressed() {
        val (sec, index) = pressed ?: return
        val base = sec.cells[index].base ?: return
        val options = store?.variantOptions(base).orEmpty().filter { canDraw(it.emoji) }
        if (options.size < 2) return
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        popupBase = options.first()
        popupOptions = options
        popupAnchor = cellRect(sec, index)
        popupHover = options.indexOfFirst { it.emoji == sec.cells[index].display }.takeIf { it >= 0 } ?: 0
        dragging = false
        invalidate()
    }

    private fun popupIndexAt(x: Float, y: Float): Int {
        val r = popupRect ?: return -1
        if (y < r.top - cellSide || y > r.bottom + cellSide) return -1
        val i = ((x - r.left - 6 * density) / cellSide).toInt()
        return if (i in popupOptions.indices && x >= r.left && x <= r.right) i else -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = e.x; downY = e.y; lastX = e.x; dragging = false
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                if (e.y >= height - barH) {
                    val rects = barRects()
                    if (rects.last().contains(e.x, e.y)) {
                        backspaceDown = true; backspaceAt = SystemClock.uptimeMillis()
                        listener?.onBackspace(BackspaceDeletionUnit.CHARACTER)
                        handler.postDelayed(repeat, 380L)
                        invalidate()
                    }
                } else {
                    pressed = hitCell(e.x, e.y)
                    if (pressed != null) handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                if (popupBase != null) {
                    popupHover = popupIndexAt(e.x, e.y)
                    invalidate()
                    return true
                }
                if (!dragging && abs(e.x - downX) > slop && e.y < height - barH) {
                    dragging = true
                    handler.removeCallbacks(longPress)
                    pressed = null
                }
                if (dragging) {
                    scrollPos = (scrollPos - (e.x - lastX)).coerceIn(0f, maxScroll)
                    invalidate()
                }
                lastX = e.x
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(repeat)
                if (backspaceDown) { backspaceDown = false; invalidate(); return true }
                if (popupBase != null) {
                    val picked = popupOptions.getOrNull(popupHover)
                    val base = popupBase!!.emoji
                    clearPopup()
                    if (picked != null) {
                        listener?.onVariantPicked(base, picked.emoji)
                        listener?.onEmojiSelected(picked.emoji, base)
                    }
                    return true
                }
                if (dragging) {
                    velocity?.computeCurrentVelocity(1000)
                    val vx = -(velocity?.xVelocity ?: 0f)
                    scroller.fling(scrollPos.toInt(), 0, vx.toInt(), 0, 0, maxScroll.toInt(), 0, 0)
                    postInvalidateOnAnimation()
                } else if (e.y >= height - barH) {
                    handleBarTap(e.x, e.y)
                } else {
                    val hit = pressed
                    if (hit != null) {
                        val cell = hit.first.cells[hit.second]
                        listener?.onEmojiSelected(cell.display, cell.base?.emoji ?: cell.display)
                    }
                }
                pressed = null
                velocity?.recycle(); velocity = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress); handler.removeCallbacks(repeat)
                backspaceDown = false; pressed = null; clearPopup()
                velocity?.recycle(); velocity = null
                invalidate()
            }
        }
        return true
    }

    private fun clearPopup() {
        popupBase = null; popupOptions = emptyList(); popupAnchor = null; popupHover = -1; popupRect = null
        invalidate()
    }

    private fun handleBarTap(x: Float, y: Float) {
        val rects = barRects()
        if (rects.first().contains(x, y)) { listener?.onReturnToKeyboard(); return }
        barCategories.forEachIndexed { i, cat ->
            if (rects[i + 1].contains(x, y)) scrollToCategory(cat)
        }
    }

    private fun scrollToCategory(category: EmojiCategory) {
        val sec = sections.firstOrNull { it.category == category } ?: return
        val target = (sec.startX - leadInset).coerceIn(0f, maxScroll)
        scroller.forceFinished(true)
        scroller.startScroll(scrollPos.toInt(), 0, (target - scrollPos).toInt(), 0, 280)
        postInvalidateOnAnimation()
    }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
}
