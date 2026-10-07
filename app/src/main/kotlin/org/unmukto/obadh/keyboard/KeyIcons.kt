package org.unmukto.obadh.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** What the return key does in the focused field; each has its own icon. */
enum class ReturnIcon { ENTER, SEARCH, GO, NEXT, PREVIOUS, SEND, DONE }

/** Icons drawn on a 24-unit grid, shared by the ribbon's tools and the bottom row. */
object KeyIcons {
    private val rect = RectF()
    private val path = Path()

    /** The return key's face: a line-art glyph in [paint]'s colour, [size] points across. */
    fun returnKey(canvas: Canvas, paint: Paint, icon: ReturnIcon, cx: Float, cy: Float, size: Float) {
        val u = size / 24f
        canvas.save()
        canvas.translate(cx - 12f * u, cy - 12f * u)
        canvas.scale(u, u)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        path.reset()
        when (icon) {
            ReturnIcon.ENTER -> {
                path.moveTo(19f, 6f); path.lineTo(19f, 11f); path.quadTo(19f, 14f, 16f, 14f); path.lineTo(5f, 14f)
                path.moveTo(9.5f, 9.5f); path.lineTo(5f, 14f); path.lineTo(9.5f, 18.5f)
            }
            ReturnIcon.SEARCH -> {
                canvas.drawCircle(10.5f, 10.5f, 6f, paint)
                path.moveTo(15f, 15f); path.lineTo(20f, 20f)
            }
            ReturnIcon.GO -> {
                path.moveTo(4f, 12f); path.lineTo(19f, 12f)
                path.moveTo(13f, 6f); path.lineTo(19f, 12f); path.lineTo(13f, 18f)
            }
            ReturnIcon.NEXT -> {
                path.moveTo(3.5f, 12f); path.lineTo(16f, 12f)
                path.moveTo(10.5f, 6.5f); path.lineTo(16f, 12f); path.lineTo(10.5f, 17.5f)
                path.moveTo(20f, 6f); path.lineTo(20f, 18f)
            }
            ReturnIcon.PREVIOUS -> {
                path.moveTo(20.5f, 12f); path.lineTo(8f, 12f)
                path.moveTo(13.5f, 6.5f); path.lineTo(8f, 12f); path.lineTo(13.5f, 17.5f)
                path.moveTo(4f, 6f); path.lineTo(4f, 18f)
            }
            ReturnIcon.SEND -> {
                path.moveTo(3f, 11f); path.lineTo(21f, 3f); path.lineTo(14f, 21f); path.lineTo(11.5f, 13f); path.close()
                path.moveTo(11.5f, 13f); path.lineTo(21f, 3f)
            }
            ReturnIcon.DONE -> {
                path.moveTo(4.5f, 12.5f); path.lineTo(10f, 18f); path.lineTo(19.5f, 7f)
            }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

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
