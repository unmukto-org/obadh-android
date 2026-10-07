package org.unmukto.obadh.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Icons drawn on a 24-unit grid, shared by the ribbon's tools and the bottom row. */
object KeyIcons {
    private val rect = RectF()

    /** The smiley: ring, two eyes, a smile. [paint] supplies colour, alpha and (reset here) style. */
    fun smiley(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        val u = size / 24f
        canvas.save()
        canvas.translate(cx - 12f * u, cy - 12f * u)
        canvas.scale(u, u)
        paint.strokeWidth = 1.9f
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(12f, 12f, 9f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(9f, 9.8f, 1.2f, paint); canvas.drawCircle(15f, 9.8f, 1.2f, paint)
        paint.style = Paint.Style.STROKE
        rect.set(7.6f, 8f, 16.4f, 17f); canvas.drawArc(rect, 25f, 130f, false, paint)
        canvas.restore()
    }
}
