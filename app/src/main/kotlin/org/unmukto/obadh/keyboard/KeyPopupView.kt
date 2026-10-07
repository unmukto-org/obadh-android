package org.unmukto.obadh.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * A bubble above a held key showing what releasing will type. It covers the whole input
 * window, so a key in the top row can still pop above the keyboard; it never takes touches.
 */
class KeyPopupView(context: Context) : View(context) {
    var theme: KeyboardTheme = KeyboardTheme.forContext(context)
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var label: String? = null
    private val bubble = RectF()
    private val keyRect = RectF()
    private val path = android.graphics.Path()

    /** [key] is the drawn key in [source]'s coordinates; it is mapped into this view's. */
    fun show(source: View, key: RectF, glyph: String) {
        val a = IntArray(2); val b = IntArray(2)
        source.getLocationInWindow(a); getLocationInWindow(b)
        val dx = (a[0] - b[0]).toFloat(); val dy = (a[1] - b[1]).toFloat()
        keyRect.set(key.left + dx, key.top + dy, key.right + dx, key.bottom + dy)
        val d = density
        val w = keyRect.width() + 2 * CALLOUT_PAD_DP * d
        val m = 3 * d
        var left = keyRect.centerX() - w / 2
        left = min(max(left, m), width - w - m)
        bubble.set(min(left, keyRect.left), max(keyRect.top - keyRect.height() * 1.18f, m), max(left + w, keyRect.right), keyRect.top)
        label = glyph
        buildPath()
        invalidate()
    }

    fun hide() {
        if (label != null) { label = null; invalidate() }
    }

    /** One outline: the callout bubble flowing into the pressed key through concave fillets, as on iOS. */
    private fun buildPath() {
        val d = density
        val l = bubble.left; val r = bubble.right; val t = bubble.top; val n = bubble.bottom
        val kl = keyRect.left; val kr = keyRect.right; val kb = keyRect.bottom
        val big = 11 * d
        val small = 6 * d
        val f = min(8 * d, min(kl - l, r - kr)).coerceAtLeast(0f)
        path.reset()
        path.moveTo(l + big, t)
        path.lineTo(r - big, t)
        path.quadTo(r, t, r, t + big)
        path.lineTo(r, n - f)
        path.quadTo(r, n, r - f, n)
        path.lineTo(kr + f, n)
        path.quadTo(kr, n, kr, n + f)
        path.lineTo(kr, kb - small)
        path.quadTo(kr, kb, kr - small, kb)
        path.lineTo(kl + small, kb)
        path.quadTo(kl, kb, kl, kb - small)
        path.lineTo(kl, n + f)
        path.quadTo(kl, n, kl - f, n)
        path.lineTo(l + f, n)
        path.quadTo(l, n, l, n - f)
        path.lineTo(l, t + big)
        path.quadTo(l, t, l + big, t)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        val glyph = label ?: return
        fill.setShadowLayer(7 * density, 0f, 1.5f * density, 0x44000000)
        fill.color = theme.key
        canvas.drawPath(path, fill)
        fill.clearShadowLayer()
        text.color = theme.label
        text.textSize = 30f * density
        val cy = (bubble.top + bubble.bottom) / 2
        canvas.drawText(glyph, (bubble.left + bubble.right) / 2, cy - (text.descent() + text.ascent()) / 2, text)
    }

    private companion object {
        const val CALLOUT_PAD_DP = 10f
    }
}
