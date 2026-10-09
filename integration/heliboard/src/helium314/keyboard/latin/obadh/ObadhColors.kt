// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.content.Context
import android.os.Build
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.common.DynamicColors

/** Shared palettes for the native renderer and settings previews; no work on the key-event path. */
object ObadhColors {
    val names = listOf("default", "dynamic", "blue", "green", "rose", "amber", "graphite")
    data class Palette(val background: Int, val keys: Int, val functional: Int, val text: Int, val accent: Int)

    fun palette(name: String, night: Boolean): Palette {
        fun rgb(value: Long) = value.toInt()
        if (name == "graphite") return if (night)
            Palette(rgb(0xff000000), rgb(0xff252525), rgb(0xff484848), rgb(0xfff4f4f4), rgb(0xff646464))
        else Palette(rgb(0xffe9e9e9), rgb(0xffffffff), rgb(0xff707070), rgb(0xff202124), rgb(0xff707070))
        val hue = when (name) {
            "blue" -> if (night) 0xff172536 else 0xffe8f0fc
            "green" -> if (night) 0xff182b23 else 0xffe8f3eb
            "rose" -> if (night) 0xff34222a else 0xffffedf3
            "amber" -> if (night) 0xff30291b else 0xfffff2dc
            else -> if (night) 0xff1e1f20 else 0xfff0f4f9
        }
        val function = when (name) {
            "blue" -> if (night) 0xff526b89 else 0xff596e8c
            "green" -> if (night) 0xff50725e else 0xff5f7969
            "rose" -> if (night) 0xff886171 else 0xff8b6676
            "amber" -> if (night) 0xff887345 else 0xff807048
            else -> if (night) 0xff444746 else 0xffe1e3e1
        }
        return Palette(rgb(hue), rgb(if (night) 0xff37393b else 0xffffffff), rgb(function),
            rgb(if (night) 0xffe3e3e3 else 0xff1f1f1f), rgb(if (name == "default") { if (night) 0xffa8c7fa else 0xff0b57d0 } else function))
    }

    @JvmStatic
    fun create(context: Context, name: String, style: String, borders: Boolean, night: Boolean): Colors {
        if (name == "dynamic" && Build.VERSION.SDK_INT >= 31) return DynamicColors(context, style, borders)
        val p = palette(name, night)
        return DefaultColors(style, borders, p.accent, p.background, p.keys, p.functional, p.keys,
            p.text, p.text, spaceBarText = p.text)
    }
}
