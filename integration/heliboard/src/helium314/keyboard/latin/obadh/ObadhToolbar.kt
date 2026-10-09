// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey

/** Fixed controls around the upstream candidate/clipboard/autofill strip. Never runs engine work. */
class ObadhToolbar(private val strip: SuggestionStripView, private val send: (Int) -> Unit) {
    private val context = strip.context
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density + .5f).toInt()
    private val colors = Settings.getValues().mColors
    private val text = colors.get(ColorType.KEY_TEXT)
    private val home = strip.findViewById<ViewGroup>(R.id.toolbar)
    private val container = strip.findViewById<View>(R.id.toolbar_container)
    private val candidates = strip.findViewById<View>(R.id.suggestions_strip)
    private val grid = strip.findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val voice = strip.findViewById<ImageButton>(R.id.obadh_voice_placeholder)
    private var menu: PopupWindow? = null
    private var toast: Toast? = null

    init {
        if (context.prefs().getString(Settings.PREF_THEME_COLORS, "") == "obadh_photo")
            strip.setBackgroundColor(0x4d000000)
        home.removeAllViews()
        // These five slots retain their positions. Unsupported media is visibly unavailable.
        button("Emoji", R.drawable.obadh_ic_sticky_note_2) { send(getCodeForToolbarKey(ToolbarKey.EMOJI)) }
        button("GIFs unavailable", R.drawable.obadh_ic_gif, false) {}
        button("Clipboard", R.drawable.obadh_ic_assignment) { send(getCodeForToolbarKey(ToolbarKey.CLIPBOARD)) }
        button("Settings", R.drawable.obadh_ic_settings) { open("org.unmukto.obadh.app.MainActivity") }
        button("Theme", R.drawable.obadh_ic_palette) { open("org.unmukto.obadh.app.ThemeActivity") }
        grid.setImageResource(R.drawable.obadh_ic_tools)
        grid.imageTintList = ColorStateList.valueOf(text)
        grid.setOnClickListener { showTools() }
        grid.background = ripple(Color.TRANSPARENT)
        voice.setImageResource(R.drawable.obadh_ic_mic)
        voice.imageTintList = ColorStateList.valueOf(text)
        // The circle is 36dp inside a 48dp touch target, including at large font sizes.
        voice.background = android.graphics.drawable.InsetDrawable(ripple(colors.get(ColorType.KEY_BACKGROUND)), dp(6))
        voice.setOnClickListener {
            toast?.cancel()
            toast = Toast.makeText(context, "Voice typing will be available in the next release", Toast.LENGTH_SHORT).also { it.show() }
        }
        strip.findViewById<View>(R.id.pinned_keys).isVisible = false
    }

    private fun ripple(color: Int) = RippleDrawable(ColorStateList.valueOf((text and 0x00ffffff) or 0x22000000),
        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }, null)

    private fun button(label: String, icon: Int, enabled: Boolean = true, action: () -> Unit) {
        home.addView(ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            contentDescription = label
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(text)
            background = ripple(Color.TRANSPARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            isEnabled = enabled
            alpha = if (enabled) 1f else .38f
            setOnClickListener { action() }
        })
    }

    fun update(words: SuggestedWords, external: Boolean) {
        val sensitive = Settings.getValues().mInputAttributes.mIsPasswordField
        val showHome = !external && (words.isEmpty || words.isPunctuationSuggestions)
        container.isVisible = showHome
        candidates.isVisible = !showHome
        grid.isVisible = !sensitive
        voice.isVisible = !sensitive
        home.getChildAt(0).apply { isEnabled = !sensitive; alpha = if (sensitive) .38f else 1f }
        home.getChildAt(2).apply { isEnabled = !sensitive; alpha = if (sensitive) .38f else 1f }
        if (!showHome) menu?.dismiss()
    }

    private fun open(activity: String) {
        menu?.dismiss()
        context.startActivity(Intent().setClassName(context.packageName, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun showTools() {
        if (menu?.isShowing == true) { menu?.dismiss(); return }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(this@ObadhToolbar.colors.get(ColorType.MAIN_BACKGROUND)); cornerRadius = dp(20).toFloat() }
        }
        val tools = listOf(ToolbarKey.EMOJI to "Emoji", ToolbarKey.CLIPBOARD to "Clipboard", ToolbarKey.DPAD to "Text editing", ToolbarKey.ONE_HANDED to "One-handed",
            ToolbarKey.FLOATING to "Floating", ToolbarKey.SPLIT to "Split", ToolbarKey.INCOGNITO to "Incognito", ToolbarKey.SETTINGS to "Settings",
            ToolbarKey.UNDO to "Undo", ToolbarKey.REDO to "Redo", ToolbarKey.SELECT_ALL to "Select all", ToolbarKey.PASTE to "Paste")
        tools.chunked(4).forEach { row ->
            root.addView(LinearLayout(context).apply {
                row.forEach { (tool, title) ->
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dp(68), 1f)
                        background = ripple(Color.TRANSPARENT)
                        contentDescription = title
                        isFocusable = true
                        addView(createToolbarKey(context, tool).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                            imageTintList = ColorStateList.valueOf(text)
                            background = null
                            isClickable = false
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        })
                        addView(TextView(context).apply { this.text = title; setTextColor(this@ObadhToolbar.text); textSize = 11f; gravity = android.view.Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
                        setOnClickListener { menu?.dismiss(); if (tool == ToolbarKey.SETTINGS) open("org.unmukto.obadh.app.MainActivity") else send(getCodeForToolbarKey(tool)) }
                    })
                }
            })
        }
        menu = PopupWindow(root, strip.width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ContextCompat.getDrawable(context, android.R.color.transparent))
            elevation = dp(8).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAsDropDown(strip, 0, -strip.height - dp(220))
        }
    }

    fun close() { menu?.dismiss(); menu = null; toast?.cancel() }
}
