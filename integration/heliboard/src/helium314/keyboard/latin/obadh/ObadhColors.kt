// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.content.Context
import android.content.res.TypedArray
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import helium314.keyboard.latin.common.ColorType
import android.os.Build
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.common.DynamicColors

/** Shared palettes for the native renderer and settings previews; no work on the key-event path. */
object ObadhColors {
    val names = listOf("default", "dynamic", "blue", "green", "rose", "amber", "graphite", "photo")
    data class Palette(val background: Int, val keys: Int, val functional: Int, val text: Int, val accent: Int)

    fun palette(name: String, night: Boolean): Palette {
        if (name == "photo") return palette("default", true)
        fun rgb(value: Long) = value.toInt()
        if (name == "graphite") return if (night)
            Palette(rgb(0xff000000), rgb(0xff252525), rgb(0xff484848), rgb(0xfff4f4f4), rgb(0xff646464))
        else Palette(rgb(0xffe9e9e9), rgb(0xffffffff), rgb(0xffdadada), rgb(0xff202124), rgb(0xff555555))
        val hue = when (name) {
            "blue" -> if (night) 0xff172536 else 0xffe8f0fc
            "green" -> if (night) 0xff182b23 else 0xffe8f3eb
            "rose" -> if (night) 0xff34222a else 0xffffedf3
            "amber" -> if (night) 0xff30291b else 0xfffff2dc
            else -> if (night) 0xff1e1f20 else 0xfff0f4f9
        }
        val function = when (name) {
            "blue" -> if (night) 0xff43596e else 0xffd3e3fd
            "green" -> if (night) 0xff415d4c else 0xffd8eadd
            "rose" -> if (night) 0xff71515e else 0xfff1d6e0
            "amber" -> if (night) 0xff70603b else 0xfff0e3c2
            else -> if (night) 0xff444746 else 0xffe1e3e1
        }
        val accent = when (name) {
            "blue" -> if (night) 0xffb5d2f3 else 0xff315da8
            "green" -> if (night) 0xffa6d7b8 else 0xff2a6147
            "rose" -> if (night) 0xffefbad0 else 0xff853e60
            "amber" -> if (night) 0xffead399 else 0xff735721
            else -> if (night) 0xffa8c7fa else 0xff0b57d0
        }
        return Palette(rgb(hue), rgb(if (night) 0xff37393b else 0xffffffff), rgb(function),
            rgb(if (night) 0xffe3e3e3 else 0xff1f1f1f), rgb(accent))
    }

    @JvmStatic
    fun create(context: Context, name: String, style: String, borders: Boolean, night: Boolean): Colors {
        if (name == "dynamic" && Build.VERSION.SDK_INT >= 31) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            return DynamicColors(context.createConfigurationContext(config), style, borders)
        }
        val p = palette(name, night)
        val image = if (name == "photo") helium314.keyboard.latin.settings.Settings.readUserBackgroundImage(context, false) else null
        val base = DefaultColors(style, borders, p.accent, p.background, p.keys, p.functional, p.keys,
            p.text, p.text, spaceBarText = p.text, keyboardBackground = image)
        return object : Colors by base {
            override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable {
                if (color != ColorType.ACTION_KEY_BACKGROUND) return base.selectAndColorDrawable(attr, color)
                return GradientDrawable().apply {
                    cornerRadius = 500 * context.resources.displayMetrics.density
                    setColor(android.graphics.Color.WHITE)
                    base.setColor(this, color)
                }
            }
        }
    }
}
