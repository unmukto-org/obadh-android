package org.unmukto.obadh.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Design tokens, light and dark. Measured against Gboard in the parity pass. */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val specialKey: Int,
    val label: Int,
    val accent: Int,
    val divider: Int,
) {
    companion object {
        fun forContext(context: Context): KeyboardTheme {
            val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return if (dark) {
                KeyboardTheme(
                    background = Color.rgb(0x1B, 0x1B, 0x1D), key = Color.rgb(0x3A, 0x3A, 0x3E),
                    keyPressed = Color.rgb(0x5A, 0x5A, 0x60), specialKey = Color.rgb(0x2A, 0x2A, 0x2D),
                    label = Color.WHITE, accent = Color.rgb(0x4C, 0xAF, 0x80), divider = Color.rgb(0x3A, 0x3A, 0x3E),
                )
            } else {
                KeyboardTheme(
                    background = Color.rgb(0xE8, 0xEA, 0xED), key = Color.WHITE,
                    keyPressed = Color.rgb(0xC9, 0xCD, 0xD2), specialKey = Color.rgb(0xCD, 0xD1, 0xD6),
                    label = Color.rgb(0x1B, 0x1B, 0x1D), accent = Color.rgb(0x0B, 0x7A, 0x4B), divider = Color.rgb(0xD0, 0xD3, 0xD8),
                )
            }
        }
    }
}
