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
import org.unmukto.obadh.settings.Haptics
import kotlin.math.abs

/** Clipboard actions on a long press of X, C and V (the keys' positions in Cut, Copy, Paste). */
enum class ClipboardAction { CUT, COPY, PASTE }

interface KeyboardViewListener {
    fun onKey(key: Key)
    fun onBackspace(unit: BackspaceDeletionUnit)
    fun onClipboard(action: ClipboardAction)

    /** Holding space turned the keys into a trackpad. */
    fun onCursorDragStart()

    /** Move the caret by [columns] characters (negative is left) and [rows] lines (negative is up). */
    fun onCursorMove(columns: Int, rows: Int)

    /** The space bar was swiped left or right before the trackpad hold: switch language. */
    fun onLanguageSwipe()

    /** Backspace was swiped left: the text before the caret is being selected for deletion. */
    fun onSwipeDeleteStart()

    /** The swipe now covers [words] words before the caret. */
    fun onSwipeDeleteSelect(words: Int)

    /** The finger lifted ([apply]) or the gesture was cancelled. */
    fun onSwipeDeleteEnd(apply: Boolean)

    /** Cut or copy was held and slid: text around the caret is being selected. */
    fun onClipboardSelectStart()

    /** The slide now covers [words] words: negative before the caret, positive after it. */
    fun onClipboardSelect(words: Int)

    /** The finger lifted: [apply] cuts or copies the selection, otherwise the caret is restored. */
    fun onClipboardSelectEnd(action: ClipboardAction, apply: Boolean)
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
    /** English typing: Latin digits replace the Bangla numerals on every page. */
    var english = false
        set(v) { field = v; rebuildRows() }
    /** The bottom row's comma becomes @ in e-mail fields and / in web-address fields. */
    var fieldKind = FieldKind.TEXT
        set(v) { field = v; rebuildRows() }
    /** The return key's icon for this field's action, or its [returnLabel] when the app named one. */
    var returnIcon = ReturnIcon.ENTER
        set(v) { field = v; invalidate() }
    var returnLabel: String? = null
        set(v) { field = v; invalidate() }
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

    /** Where the held-key preview is drawn; null in the debug preview. */
    var popup: KeyPopupView? = null

    /** A held key's second glyph, shown in the popup and typed only if the finger lifts on the key. */
    private var armed: Key.Symbol? = null

    /** The rectangle a key is actually drawn in (rows are inset vertically). */
    private fun drawnRect(cell: Cell): RectF {
        val vGap = if (family != null) minOf(gap, 10f * density) else if (landscape) 6f * density else gap
        return RectF(cell.rect.left, cell.rect.top + vGap / 2, cell.rect.right, cell.rect.bottom - vGap / 2)
    }

    /** The iOS-style callout over a pressed character or symbol key. */
    // Feature switches, mirrored from the app's settings when a field starts.
    var trackpadEnabled = true
    var swipeDeleteEnabled = true
    var spaceSwipeLanguageEnabled = true
    var longPressSymbolsEnabled = true
        set(v) { field = v; invalidate() }
    var clipboardKeysEnabled = true
        set(v) { field = v; invalidate() }
    var calloutEnabled = true

    private fun previewPress(cell: Cell?) {
        if (!calloutEnabled) { popup?.hide(); return }
        val glyph = when (val k = cell?.key) {
            is Key.Character -> if (shiftActive || capsLock) k.value.uppercase() else k.value
            is Key.Symbol -> k.label
            else -> null
        }
        if (cell == null || glyph == null) popup?.hide() else popup?.show(this, drawnRect(cell), glyph)
    }

    private fun disarm() {
        armed = null
        popup?.hide()
    }

    // Space-bar trackpad: hold space, then slide to move the caret.
    private var trackpad = false
    private var trackpadX = 0f
    private var trackpadY = 0f
    private var trackpadRemainder = 0f
    private var trackpadRemainderY = 0f
    private var downX = 0f
    // Backspace swipe: slide left to select words before the caret, lift to delete them.
    private var swipeDelete = false
    private var swipeWords = 0

    private fun endSwipeDelete(apply: Boolean) {
        if (!swipeDelete) return
        swipeDelete = false
        swipeWords = 0
        listener?.onSwipeDeleteEnd(apply)
    }

    // Held X or C, then slid: select words either side of the caret, act on release.
    private var clipHold: ClipboardAction? = null
    private var clipSliding = false
    private var clipWords = 0

    private fun endClipHold(apply: Boolean) {
        val action = clipHold ?: return
        val slid = clipSliding
        clipHold = null; clipSliding = false; clipWords = 0
        if (slid) listener?.onClipboardSelectEnd(action, apply)
        else if (apply) listener?.onClipboard(action)
    }

    private val startTrackpad = Runnable {
        if (pressed?.key !is Key.Space) return@Runnable
        trackpad = true
        popup?.hide()
        trackpadX = downX
        trackpadY = downY
        trackpadRemainder = 0f
        trackpadRemainderY = 0f
        Haptics.play(this, long = true)
        listener?.onCursorDragStart()
        invalidate()
    }

    private fun endTrackpad() {
        handler.removeCallbacks(startTrackpad)
        if (trackpad) { trackpad = false; invalidate() }
    }

    private val density = resources.displayMetrics.density
    private val gap: Float get() = (family?.gapDp(landscape) ?: 5f) * density
    private val topPad = 6f * density

    /**
     * Safe area under the bottom row. The system navigation strip sits below the view, but keys
     * pressed flush against it are hard to hit and look cramped, so the keys stop short of it.
     */
    val bottomPad: Float
        get() = (when {
            family != null -> 18f
            landscape -> 14f
            else -> 22f
        }) * density
    /** One normal key row. A phone's landscape rows are shorter so the app stays visible above. */
    private val rowHeight: Float
        get() = (family?.rowHeightDp(landscape) ?: if (landscape) PHONE_LANDSCAPE_ROW_DP else 53f) * density

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
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())

    private val repeat = object : Runnable {
        override fun run() {
            val stage = BackspaceRepeatPolicy.stage(SystemClock.uptimeMillis() - downAt)
            if (stage != null) listener?.onBackspace(stage.unit)
            handler.postDelayed(this, stage?.intervalMs ?: 20L)
        }
    }

    private fun clipboardFor(key: Key?): ClipboardAction? =
        if (clipboardKeysEnabled && key is Key.Character) when (key.value.lowercase()) {
            "x" -> ClipboardAction.CUT
            "c" -> ClipboardAction.COPY
            "v" -> ClipboardAction.PASTE
            else -> null
        } else null

    /**
     * Holding X, C or V cuts, copies or pastes (on every form factor; on a tablet the key's
     * symbol stays on the downward flick). Otherwise holding a key that has a secondary glyph
     * emits it: a digit, symbol or punctuation mark, on phones and tablets alike.
     */
    private val secondaryLongPress = Runnable {
        val cell = pressed ?: return@Runnable
        clipboardFor(cell.key)?.let {
            secondaryFired = true
            Haptics.play(this, long = true)
            popup?.hide()
            // Cut and copy wait for the finger: lifting acts on the whole field, sliding selects.
            if (it == ClipboardAction.PASTE) listener?.onClipboard(it) else clipHold = it
            return@Runnable
        }
        val secondary = secondary(cell.key) ?: return@Runnable
        secondaryFired = true
        Haptics.play(this, long = true)
        armed = secondary
        popup?.show(this, drawnRect(cell), secondary.label)
    }

    private fun latinDigit(k: Key): Key =
        if (k is Key.Symbol && k.output in BN_DIGITS) Key.Symbol(BN_DIGITS.indexOf(k.output).toString()) else k

    private fun latinDigits(row: KeyboardRow) = row.copy(keys = row.keys.map(::latinDigit))

    /** The letters page's bottom-row dari becomes a full stop when typing English. */
    /** E-mail and web fields: `@` or `/` for the comma, and a `.com` key after the full stop. */
    private fun fieldComma(row: KeyboardRow): KeyboardRow {
        val glyph = when (fieldKind) { FieldKind.EMAIL -> "@"; FieldKind.URL -> "/"; else -> return row }
        val keys = row.keys.toMutableList()
        val weights = row.weights.toMutableList()
        for (i in keys.indices) {
            val k = keys[i]
            if (k is Key.Symbol && k.output == ",") keys[i] = Key.Symbol(glyph)
        }
        val stop = keys.indexOfFirst { it is Key.Symbol && (it.output == "." || it.output == "।") }
        val space = keys.indexOf(Key.Space)
        if (stop >= 0 && space >= 0 && weights[space] > 2.2) {
            keys.add(stop + 1, Key.Symbol(".com"))
            weights.add(stop + 1, 1.5)
            weights[space] = weights[space] - 1.5
        }
        return KeyboardRow(keys, weights)
    }

    private fun fullStop(row: KeyboardRow) =
        row.copy(keys = row.keys.map { if (it is Key.Symbol && it.output == "।") Key.Symbol(".", terminator = true) else it })

    /** A held key's second glyph, with Latin digits when typing English. */
    private fun secondary(key: Key): Key.Symbol? = if (!longPressSymbolsEnabled) null else KeyboardLayoutProvider.secondaryFor(key)?.let { if (english) latinDigit(it) as Key.Symbol else it }

    private fun rebuildRows() {
        rows = KeyboardLayoutProvider.rows(mode, includesGlobeKey, family, landscape).let { built ->
            val bottom = mode == KeyboardMode.LETTERS && family == null
            val r = if (english) built.map(::latinDigits) else built
            if (!bottom) r
            else r.dropLast(1) + fieldComma(if (english) fullStop(r.last()) else r.last())
        }
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
        if (trackpad) drawTrackpadHint(canvas)
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
        val r = drawnRect(cell)
        canvas.drawRoundRect(r, 6 * density, 6 * density, keyPaint)

        if (cell.key == Key.Emoji) {
            iconPaint.color = theme.label
            KeyIcons.smiley(canvas, iconPaint, r.centerX(), r.centerY(), 25f * density)
            return
        }
        if (cell.key == Key.Return && returnLabel == null) {
            iconPaint.color = theme.label
            KeyIcons.returnKey(canvas, iconPaint, returnIcon, r.centerX(), r.centerY(), 22f * density)
            return
        }
        val label = label(cell.key)
        textPaint.color = theme.label
        textPaint.typeface = if (cell.key is Key.Character || cell.key is Key.Symbol) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        textPaint.textSize = (if (label.length > 1 && cell.key !is Key.Symbol) 15f else letterSp()) * density
        // Long labels (Search, .com) shrink to fit the key instead of spilling over it.
        while (textPaint.measureText(label) > r.width() - 8 * density && textPaint.textSize > 9 * density) {
            textPaint.textSize *= 0.92f
        }
        val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, r.centerX(), y, textPaint)

        // The secondary glyph, quieter than the primary: top-left on a tablet, top-right on a
        // phone. Holding the key emits it (a downward flick too, on a tablet).
        // On a phone X, C and V are cut, copy and paste, and carry nothing else.
        secondary(cell.key)?.takeIf { family != null || clipboardFor(cell.key) == null }?.let {
            val tablet = family != null
            textPaint.textSize = (if (tablet) 11f else 10f) * density
            textPaint.alpha = 140
            textPaint.textAlign = if (tablet) Paint.Align.LEFT else Paint.Align.RIGHT
            val x = if (tablet) r.left + 6 * density else r.right - 4 * density
            canvas.drawText(it.label, x, r.top + (if (tablet) 15f else 12f) * density, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.alpha = 255
        }
    }

    /** Letters fade out while the keys are a trackpad, as on Gboard and iOS. */
    private fun drawTrackpadHint(canvas: Canvas) {
        keyPaint.color = (theme.background and 0x00FFFFFF) or (0xB8 shl 24)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), keyPaint)
        textPaint.color = theme.label
        textPaint.alpha = 160
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 16f * density
        canvas.drawText("◂ ▴ ▾ ▸", width / 2f, height / 2f, textPaint)
        textPaint.alpha = 255
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
        Key.Return -> returnLabel ?: "⏎"
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
        val BN_DIGITS = listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯")
        const val TRACKPAD_HOLD_MS = 350L
        const val TRACKPAD_STEP_DP = 9f
        const val TRACKPAD_LINE_DP = 22f
        const val TRACKPAD_SLOP_DP = 8f
        const val LANGUAGE_SWIPE_DP = 40f
        const val SWIPE_DELETE_START_DP = 24f
        const val SWIPE_DELETE_WORD_DP = 30f
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
                downX = e.x
                trackpad = false
                swipeDelete = false
                swipeWords = 0
                clipHold = null; clipSliding = false; clipWords = 0
                invalidate()
                previewPress(pressed)
                val key = pressed?.key
                if (key is Key.Space) {
                    if (trackpadEnabled) handler.postDelayed(startTrackpad, TRACKPAD_HOLD_MS)
                } else if (key is Key.Backspace) {
                    listener?.onBackspace(BackspaceDeletionUnit.CHARACTER)
                    handler.postDelayed(repeat, 380L)
                } else if (clipboardFor(key) != null ||
                    (key != null && secondary(key) != null)
                ) {
                    handler.postDelayed(secondaryLongPress, 420L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeDeleteEnabled && pressed?.key is Key.Backspace) {
                    val dx = downX - e.x
                    if (!swipeDelete && dx > SWIPE_DELETE_START_DP * density) {
                        swipeDelete = true
                        handler.removeCallbacks(repeat)
                        listener?.onSwipeDeleteStart()
                    }
                    if (swipeDelete) {
                        val words = ((dx - SWIPE_DELETE_START_DP * density) / (SWIPE_DELETE_WORD_DP * density)).toInt().coerceAtLeast(0) + 1
                        if (words != swipeWords) { swipeWords = words; listener?.onSwipeDeleteSelect(words) }
                        return true
                    }
                }
                if (clipHold != null) {
                    val dx = e.x - downX
                    val start = SWIPE_DELETE_START_DP * density
                    if (!clipSliding && abs(dx) > start) {
                        clipSliding = true
                        listener?.onClipboardSelectStart()
                    }
                    if (clipSliding) {
                        val steps = (((abs(dx) - start) / (SWIPE_DELETE_WORD_DP * density)).toInt() + 1)
                        val words = if (dx < 0) -steps else steps
                        if (abs(dx) <= start) { if (clipWords != 0) { clipWords = 0; listener?.onClipboardSelect(0) } }
                        else if (words != clipWords) { clipWords = words; listener?.onClipboardSelect(words) }
                    }
                    return true
                }
                if (trackpad) {
                    // Whole characters only; the leftover travel carries to the next event, so
                    // slow drags never lose distance and fast ones never overshoot.
                    trackpadRemainder += e.x - trackpadX
                    trackpadX = e.x
                    trackpadRemainderY += e.y - trackpadY
                    trackpadY = e.y
                    val step = TRACKPAD_STEP_DP * density
                    val lineStep = TRACKPAD_LINE_DP * density
                    val columns = (trackpadRemainder / step).toInt()
                    val rows = (trackpadRemainderY / lineStep).toInt()
                    if (columns != 0) trackpadRemainder -= columns * step
                    if (rows != 0) trackpadRemainderY -= rows * lineStep
                    if (columns != 0 || rows != 0) listener?.onCursorMove(columns, rows)
                    return true
                }
                armed?.let {
                    // Sliding off the key withdraws the choice: nothing is typed on release.
                    val key = pressed
                    if (key == null || !key.rect.contains(e.x, e.y)) disarm()
                    return true
                }
                if (pressed?.key is Key.Space && abs(e.x - downX) > TRACKPAD_SLOP_DP * density) {
                    handler.removeCallbacks(startTrackpad)
                    // A quick slide, before the trackpad hold, flips the language.
                    if (spaceSwipeLanguageEnabled && !secondaryFired && abs(e.x - downX) > LANGUAGE_SWIPE_DP * density) {
                        secondaryFired = true
                        listener?.onLanguageSwipe()
                    }
                }
                val now = resolve(e.x, e.y)
                if (pressed?.key !is Key.Backspace && !secondaryFired) {
                    // A downward flick on a key with a secondary emits it, like iPadOS.
                    val key = pressed?.key
                    if (family != null && key != null && e.y - downY > 22 * density) {
                        secondary(key)?.let {
                            secondaryFired = true
                            handler.removeCallbacks(secondaryLongPress)
                            popup?.hide()
                            Haptics.play(this)
                            listener?.onKey(it)
                        }
                    }
                    if (!secondaryFired && now !== pressed) { pressed = now; invalidate(); previewPress(now) }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (swipeDelete) {
                    handler.removeCallbacks(repeat)
                    endSwipeDelete(true)
                    pressed = null
                    invalidate()
                    return true
                }
                if (clipHold != null) {
                    endClipHold(true)
                    handler.removeCallbacks(secondaryLongPress)
                    pressed = null
                    invalidate()
                    return true
                }
                if (trackpad) {
                    // The hold was a cursor gesture, not a space.
                    endTrackpad()
                    pressed = null
                    invalidate()
                    return true
                }
                handler.removeCallbacks(startTrackpad)
                handler.removeCallbacks(repeat)
                handler.removeCallbacks(secondaryLongPress)
                val chosen = armed
                disarm()
                val wasBackspace = pressed?.key is Key.Backspace
                val cell = if (wasBackspace || secondaryFired) null else resolve(e.x, e.y)
                pressed = null
                invalidate()
                if (cell != null) listener?.onKey(cell.key)
                else if (chosen != null) listener?.onKey(chosen)
            }
            MotionEvent.ACTION_CANCEL -> {
                endSwipeDelete(false)
                endClipHold(false)
                disarm()
                endTrackpad()
                handler.removeCallbacks(repeat)
                handler.removeCallbacks(secondaryLongPress)
                pressed = null
                invalidate()
            }
        }
        return true
    }
}
