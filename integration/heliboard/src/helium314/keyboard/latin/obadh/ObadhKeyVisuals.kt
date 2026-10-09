// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.KeyDrawParams

/** The comma remains the tap action; hold opens emoji without a second key or popup menu. */
object ObadhKeyVisuals {
    @JvmStatic
    fun drawComma(key: Key, canvas: Canvas, paint: Paint, params: KeyDrawParams, keyboard: Keyboard, density: Float) {
        val center = key.drawWidth / 2f
        val icon = keyboard.mIconsSet.getIconDrawable("emoji")
        val size = (12 * density).toInt()
        icon?.apply {
            setTint(params.mFunctionalTextColor)
            setBounds((center - size / 2).toInt(), (key.height * .16f).toInt(), (center + size / 2).toInt(), (key.height * .16f).toInt() + size)
            draw(canvas)
        }
        paint.apply {
            color = params.mFunctionalTextColor
            textSize = 18 * density
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            clearShadowLayer()
        }
        canvas.drawText(",", center, key.height * .72f, paint)
    }
}
