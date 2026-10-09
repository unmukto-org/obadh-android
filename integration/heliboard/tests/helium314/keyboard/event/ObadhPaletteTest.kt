package helium314.keyboard.event

import helium314.keyboard.latin.obadh.ObadhColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ObadhPaletteTest {
    private fun luminance(color: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((color ushr shift) and 255) / 255.0
            return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        return .2126 * linear(16) + .7152 * linear(8) + .0722 * linear(0)
    }
    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun smallLabelsStayReadableInBothAppearances() {
        for (theme in listOf("default", "light", "dark")) for (night in listOf(false, true)) {
            val p = ObadhColors.palette(theme, night)
            for (background in listOf(p.keys, p.functional, p.background)) {
                assertTrue("$theme night=$night labels need 4.5:1 contrast", contrast(p.text, background) >= 4.5)
            }
            assertTrue("$theme action icon must have a readable foreground",
                maxOf(contrast(0xffffffff.toInt(), p.accent), contrast(0xff444444.toInt(), p.accent)) >= 4.5)
        }
    }
}
