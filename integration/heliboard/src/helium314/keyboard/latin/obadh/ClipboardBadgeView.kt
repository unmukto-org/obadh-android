// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import java.lang.ref.WeakReference

/**
 * A small icon badge that pops at the strip's right edge when cut, copy or paste lands: pop in,
 * hold, fade out. It is drawn over the strip and never takes a touch.
 */
class ClipboardBadgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class Action { CUT, COPY, PASTE }

    private val density = resources.displayMetrics.density
    private var action: Action? = null
    private var progress = 0f
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = FLASH_MS
        interpolator = LinearInterpolator()
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) { action = null; invalidate() }
            override fun onAnimationCancel(a: Animator) { action = null }
        })
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun show(next: Action) {
        animator.cancel()
        action = next
        progress = 0f
        val colors = Settings.getValues().mColors
        badge.color = colors.get(ColorType.ACTION_KEY_BACKGROUND)
        icon.color = colors.get(ColorType.ACTION_KEY_ICON)
        animator.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        current = WeakReference(this)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        if (current?.get() === this) current = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val shown = action ?: return
        val p = progress
        val alpha = when { p < .12f -> p / .12f; p > .75f -> (1f - p) / .25f; else -> 1f }.coerceIn(0f, 1f)
        // Overshoot on the way in, then rest at full size.
        val scale = when {
            p < .12f -> .5f + .6f * (p / .12f)
            p < .22f -> 1.1f - .1f * ((p - .12f) / .10f)
            else -> 1f
        }
        val room = height - paddingTop - paddingBottom
        val radius = minOf(room * .36f, 17f * density) * scale
        val cx = width - 12f * density - radius
        val cy = paddingTop + room / 2f
        badge.alpha = (alpha * 255).toInt()
        canvas.drawCircle(cx, cy, radius, badge)
        icon.alpha = (alpha * 255).toInt()
        drawIcon(canvas, shown, cx, cy, radius * 1.15f)
    }

    /** Line icons on a 24-unit grid, scaled to [size] across and centred at (cx, cy). */
    private fun drawIcon(canvas: Canvas, shown: Action, cx: Float, cy: Float, size: Float) {
        val u = size / 24f
        canvas.save()
        canvas.translate(cx - 12f * u, cy - 12f * u)
        canvas.scale(u, u)
        icon.strokeWidth = 2f
        when (shown) {
            Action.CUT -> {
                canvas.drawCircle(7f, 18f, 2.6f, icon)
                canvas.drawCircle(17f, 18f, 2.6f, icon)
                canvas.drawLine(8.8f, 16.1f, 18f, 4f, icon)
                canvas.drawLine(15.2f, 16.1f, 6f, 4f, icon)
            }
            Action.COPY -> {
                rect.set(9f, 9f, 20f, 20f)
                canvas.drawRoundRect(rect, 2.2f, 2.2f, icon)
                path.reset()
                path.moveTo(16f, 9f); path.lineTo(16f, 6.2f)
                path.quadTo(16f, 5f, 14.8f, 5f); path.lineTo(6.2f, 5f)
                path.quadTo(5f, 5f, 5f, 6.2f); path.lineTo(5f, 14.8f)
                path.quadTo(5f, 16f, 6.2f, 16f); path.lineTo(9f, 16f)
                canvas.drawPath(path, icon)
            }
            Action.PASTE -> {
                rect.set(5f, 5f, 19f, 21f)
                canvas.drawRoundRect(rect, 2.2f, 2.2f, icon)
                rect.set(9f, 3f, 15f, 7f)
                canvas.drawRoundRect(rect, 1.6f, 1.6f, icon)
                canvas.drawLine(8.5f, 12f, 15.5f, 12f, icon)
                canvas.drawLine(8.5f, 16f, 13.5f, 16f, icon)
            }
        }
        canvas.restore()
    }

    companion object {
        private const val FLASH_MS = 1100L
        private var current: WeakReference<ClipboardBadgeView>? = null

        /** Pops the badge on the strip that is on screen, if any. Call on the main thread. */
        @JvmStatic fun flash(action: Action) {
            current?.get()?.show(action)
        }
    }
}
