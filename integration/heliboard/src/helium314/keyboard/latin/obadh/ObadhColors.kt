// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors

/** One palette for native drawing and Compose previews. Theme work occurs only on reload/layout. */
object ObadhColors {
    val catalog get() = ObadhThemeCatalog.themes
    private val aliases = mapOf("rose" to "pink", "amber" to "sand", "graphite" to "black")
    val names get() = listOf("default", "dynamic", "light", "dark", "photo") + catalog.map { it.id } + aliases.keys
    data class Palette(val background: Int, val keys: Int, val functional: Int, val text: Int, val accent: Int,
        val space: Int = keys, val functionalText: Int = text,
        val actionText: Int = if (isLightAccent(accent)) 0xff1f1f1f.toInt() else Color.WHITE)

    private fun isLightAccent(color: Int): Boolean {
        fun component(shift: Int): Double {
            val c = ((color ushr shift) and 255) / 255.0
            return if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)
        }
        return .2126 * component(16) + .7152 * component(8) + .0722 * component(0) > .5
    }

    fun palette(name: String, night: Boolean): Palette {
        val resolved = aliases[name] ?: name
        catalog.firstOrNull { it.id == resolved }?.let { return it.palette }
        if (name == "photo") return Palette(0xff1e1f20.toInt(), 0x4dffffff, 0x0dffffff, Color.WHITE, 0xff5e97f6.toInt(), actionText = Color.WHITE)
        return if (name == "dark" || (name != "light" && night))
            Palette(0xff1e1f20.toInt(), 0xff37393b.toInt(), 0xff444746.toInt(), 0xffe3e3e3.toInt(), 0xffa8c7fa.toInt())
        else Palette(0xfff0f4f9.toInt(), Color.WHITE, 0xffe1e3e1.toInt(), 0xff1f1f1f.toInt(), 0xff0b57d0.toInt())
    }

    fun dynamicPalette(context: Context, night: Boolean): Palette {
        if (Build.VERSION.SDK_INT < 31) return palette("default", night)
        fun color(id: Int) = ContextCompat.getColor(context, id)
        fun role(name: String, fallback: Int): Int {
            val id = context.resources.getIdentifier(name, "color", "android")
            return if (id == 0) fallback else color(id)
        }
        val background = role(if (night) "system_surface_container_dark" else "system_surface_container_light",
            if (night) color(android.R.color.system_neutral1_900)
            else ColorUtils.blendARGB(color(android.R.color.system_neutral1_50), color(android.R.color.system_neutral1_100), .2f))
        val keys = if (night) role("system_surface_bright_dark", color(android.R.color.system_neutral1_800)) else Color.WHITE
        val functional = role(if (night) "system_secondary_container_dark" else "system_secondary_container_light",
            color(if (night) android.R.color.system_accent2_200 else android.R.color.system_accent2_500))
        val text = role(if (night) "system_on_surface_dark" else "system_on_surface_light",
            color(if (night) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900))
        val functionalText = role(if (night) "system_on_secondary_container_dark" else "system_on_secondary_container_light",
            if (ColorUtils.calculateLuminance(functional) > .5) 0xff1f1f1f.toInt() else Color.WHITE)
        return Palette(background, keys, functional, text, functional, functionalText = functionalText, actionText = functionalText)
    }

    @JvmStatic
    fun create(context: Context, name: String, style: String, borders: Boolean, night: Boolean): Colors {
        val p = if (name == "dynamic") dynamicPalette(context, night) else palette(name, night)
        val theme = catalog.firstOrNull { it.id == (aliases[name] ?: name) }
        val image = if (name == "photo") helium314.keyboard.latin.settings.Settings.readUserBackgroundImage(context, false)?.let(::CropDrawable)
            else theme?.gradient?.let { StopsDrawable(it, theme.positions!!) }
        val base = DefaultColors(style, borders, p.accent, p.background, p.keys, p.functional, p.space,
            p.text, p.text, spaceBarText = p.text, keyboardBackground = image)
        return object : Colors by base {
            override fun haveColorsChanged(context: Context) = name == "dynamic" && dynamicPalette(context, night) != p
            override fun get(color: ColorType): Int = when (color) {
                ColorType.FUNCTIONAL_KEY_TEXT, ColorType.SHIFT_KEY_ICON -> p.functionalText
                ColorType.ACTION_KEY_ICON -> p.actionText
                ColorType.NAVIGATION_BAR -> theme?.gradient?.last() ?: p.background
                else -> base.get(color)
            }
            override fun setColor(drawable: Drawable, color: ColorType) {
                if ((color == ColorType.FUNCTIONAL_KEY_TEXT || color == ColorType.SHIFT_KEY_ICON || color == ColorType.ACTION_KEY_ICON)) drawable.setTint(get(color))
                else base.setColor(drawable, color)
            }
            override fun setColor(view: ImageView, color: ColorType) {
                if ((color == ColorType.FUNCTIONAL_KEY_TEXT || color == ColorType.SHIFT_KEY_ICON || color == ColorType.ACTION_KEY_ICON)) view.setColorFilter(get(color))
                else base.setColor(view, color)
            }
            override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable {
                if (color != ColorType.ACTION_KEY_BACKGROUND) return base.selectAndColorDrawable(attr, color)
                return GradientDrawable().apply {
                    cornerRadius = 500 * context.resources.displayMetrics.density
                    setColor(Color.WHITE)
                    base.setColor(this, color)
                }
            }
        }
    }

    private class StopsDrawable(private val colors: IntArray, private val positions: FloatArray) : Drawable() {
        private val paint = Paint(Paint.DITHER_FLAG)
        override fun onBoundsChange(bounds: Rect) {
            paint.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), colors, positions, Shader.TileMode.CLAMP)
        }
        override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
    }

    /** Center-crop a photo at each new layout, including landscape and split tablet layouts. */
    private class CropDrawable(private val source: Drawable) : Drawable() {
        override fun draw(canvas: Canvas) {
            val scale = maxOf(bounds.width().toFloat() / source.intrinsicWidth, bounds.height().toFloat() / source.intrinsicHeight)
            val w = (source.intrinsicWidth * scale).toInt(); val h = (source.intrinsicHeight * scale).toInt()
            val save = canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(bounds.centerX() - w / 2f, bounds.centerY() - h / 2f)
            source.setBounds(0, 0, w, h); source.draw(canvas)
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { source.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { source.colorFilter = filter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
    }
}
