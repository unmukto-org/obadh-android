package org.unmukto.obadh.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.unmukto.obadh.keyboard.KeyboardMode
import org.unmukto.obadh.keyboard.KeyboardTheme
import org.unmukto.obadh.keyboard.KeyboardView

/**
 * Shows the real [KeyboardView] as if the device were `sw` dp wide, scaled to fit this screen.
 * Pure review tooling (the iOS repo has the same idea in its debug test screen); no device
 * setting is changed. Extras: sw (smallest width dp, default 800), mode (letters|numbers|symbols).
 */
class KeyboardPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sw = intent.getIntExtra("sw", 800)
        val landscape = intent.getBooleanExtra("land", false)
        // Landscape shows the device's longer side. 16:10 is the common tablet ratio, ~19.5:9 a phone.
        val widthDp = intent.getIntExtra("w", if (landscape) (sw * (if (sw >= 600) 1.6 else 2.1)).toInt() else sw)
        val mode = KeyboardMode.entries.firstOrNull { it.name.equals(intent.getStringExtra("mode"), true) }
            ?: KeyboardMode.LETTERS
        val density = resources.displayMetrics.density
        val theme = KeyboardTheme.forContext(this)

        val keyboard = KeyboardView(this).also {
            it.forcedSmallestWidthDp = sw
            it.forcedLandscape = landscape
            it.mode = mode
        }
        val logicalWidth = (widthDp * density).toInt()
        val scale = resources.displayMetrics.widthPixels.toFloat() / logicalWidth

        val stage = FrameLayout(this)
        stage.addView(keyboard, FrameLayout.LayoutParams(logicalWidth, FrameLayout.LayoutParams.WRAP_CONTENT))
        keyboard.pivotX = 0f; keyboard.pivotY = 0f
        keyboard.scaleX = scale; keyboard.scaleY = scale
        // Scaling a view does not change its layout box, so reserve the scaled height.
        keyboard.post { stage.layoutParams = stage.layoutParams.also { p -> p.height = (keyboard.height * scale).toInt() } }

        val label = TextView(this).apply {
            text = "sw ${sw}dp  ·  ${keyboard.family ?: "phone"}  ·  $mode  ·  ${if (landscape) "landscape ${widthDp}dp" else "portrait"}  ·  1:${"%.2f".format(1 / scale)}"
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            setPadding(0, (40 * density).toInt(), 0, (12 * density).toInt())
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(theme.background.let { Color.rgb(0x10, 0x12, 0x14) })
                gravity = Gravity.BOTTOM
                addView(label)
                addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(View(this@KeyboardPreviewActivity), LinearLayout.LayoutParams(1, (24 * density).toInt()))
            },
        )
    }
}
