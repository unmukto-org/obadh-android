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
import android.widget.ScrollView
import android.view.Gravity
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private val text = this@ObadhToolbar.colors.get(ColorType.KEY_TEXT)
    private val home = strip.findViewById<ViewGroup>(R.id.toolbar)
    private val container = strip.findViewById<View>(R.id.toolbar_container)
    private val candidates = strip.findViewById<View>(R.id.suggestions_strip)
    private val grid = strip.findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val voice = strip.findViewById<ImageButton>(R.id.obadh_voice_placeholder)
    private var menu: PopupWindow? = null
    private var toast: Toast? = null
    private var homeNormallyVisible = true

    init {
        if (context.prefs().getString(Settings.PREF_THEME_COLORS, "") == "obadh_photo")
            strip.setBackgroundColor(0x4d000000)
        home.removeAllViews()
        // Fixed Gboard-style home controls; stickers negotiate capabilities with the editor.
        button("Stickers", R.drawable.obadh_ic_sticker) { showStickers() }
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
        voice.background = android.graphics.drawable.InsetDrawable(ripple(this@ObadhToolbar.colors.get(ColorType.KEY_BACKGROUND)), dp(6))
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
            tag = label
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(text)
            background = ripple(Color.TRANSPARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            isEnabled = enabled
            alpha = if (enabled) 1f else .38f
            setOnClickListener { menu?.dismiss(); action() }
        })
    }

    fun update(words: SuggestedWords, external: Boolean) {
        val sensitive = Settings.getValues().mInputAttributes.mIsPasswordField
        homeNormallyVisible = !external && (words.isEmpty || words.isPunctuationSuggestions)
        val showHome = menu?.isShowing == true || homeNormallyVisible
        container.isVisible = showHome
        candidates.isVisible = !showHome
        grid.isVisible = !sensitive
        voice.isVisible = !sensitive
        home.findViewWithTag<View>("Stickers").apply { isEnabled = !sensitive; alpha = if (ObadhExtensions.current?.stickersSupported == true && !sensitive) 1f else .38f }
        home.findViewWithTag<View>("Clipboard").apply { isEnabled = !sensitive; alpha = if (sensitive) .38f else 1f }
        if (!showHome || sensitive || external) menu?.dismiss()
    }

    private fun open(activity: String, action: String? = null) {
        menu?.dismiss()
        context.startActivity(Intent(action).setClassName(context.packageName, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun setExpanded(expanded: Boolean) {
        container.isVisible = expanded || homeNormallyVisible
        candidates.isVisible = !container.isVisible
        grid.setImageResource(if (expanded) R.drawable.obadh_ic_arrow_back else R.drawable.obadh_ic_tools)
        grid.contentDescription = if (expanded) "Back to keyboard" else "Keyboard tools"
        grid.imageTintList = ColorStateList.valueOf(if (expanded) this@ObadhToolbar.colors.get(ColorType.FUNCTIONAL_KEY_TEXT) else text)
        grid.background = if (expanded) android.graphics.drawable.InsetDrawable(ripple(this@ObadhToolbar.colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)), dp(7)) else ripple(Color.TRANSPARENT)
        for (i in 0 until home.childCount) home.getChildAt(i).background = if (expanded) {
            android.graphics.drawable.InsetDrawable(RippleDrawable(ColorStateList.valueOf((text and 0x00ffffff) or 0x22000000),
                GradientDrawable().apply { cornerRadius=dp(12).toFloat();setColor(this@ObadhToolbar.colors.get(ColorType.KEY_BACKGROUND)) }, null), dp(3), dp(5), dp(3), dp(5))
        } else ripple(Color.TRANSPARENT)
    }

    private fun showStickers() {
        menu?.dismiss()
        menu = ObadhExtensions.current?.showStickers(strip) { menu=null;setExpanded(false) }
        if (menu != null) setExpanded(true)
    }

    private fun showTools() {
        if (menu?.isShowing == true) { menu?.dismiss(); return }
        val keyboard = strip.rootView.findViewById<View>(R.id.keyboard_view_wrapper) ?: return
        if (keyboard.height <= 0) return
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
            setBackgroundColor(this@ObadhToolbar.colors.get(ColorType.MAIN_BACKGROUND))
        }
        root.addView(TextView(context).apply {
            text="Keyboard tools";textSize=14f;setTextColor(this@ObadhToolbar.text);gravity=Gravity.CENTER
            layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(32))
        })
        data class Tool(val label: String, val key: ToolbarKey? = null, val icon: Int? = null, val action: (() -> Unit)? = null)
        val automatic = context.prefs().getString("obadh.tablet_layout", "automatic") == "automatic"
        val tools = listOf(Tool("One-handed",ToolbarKey.ONE_HANDED,icon=R.drawable.obadh_ic_mobile_hand),Tool("Text editing",ToolbarKey.DPAD,icon=R.drawable.obadh_ic_text_editing),
            if (automatic) Tool("Split",ToolbarKey.SPLIT) else Tool("Incognito",ToolbarKey.INCOGNITO),Tool("Floating",ToolbarKey.FLOATING,icon=R.drawable.obadh_ic_keyboard),
            Tool("Keyboard size",icon=R.drawable.obadh_ic_resize,action={ open("org.unmukto.obadh.app.MainActivity",Intent.ACTION_APPLICATION_PREFERENCES) }),
            Tool("Next language",icon=R.drawable.obadh_ic_language,action={ send(KeyCode.LANGUAGE_SWITCH) }),Tool("Undo",ToolbarKey.UNDO))
        tools.chunked(4).forEach { row ->
            root.addView(LinearLayout(context).apply {
                row.forEach { tool ->
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f)
                        contentDescription = tool.label;isFocusable=true
                        val icon = if (tool.icon != null) ImageButton(context).apply { setImageResource(tool.icon) } else createToolbarKey(context,tool.key!!)
                        addView(icon.apply {
                            layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)).apply { setMargins(dp(4),0,dp(4),0) }
                            imageTintList=ColorStateList.valueOf(this@ObadhToolbar.text)
                            background=RippleDrawable(ColorStateList.valueOf((text and 0x00ffffff) or 0x22000000),GradientDrawable().apply { cornerRadius=dp(12).toFloat();setColor(this@ObadhToolbar.colors.get(ColorType.KEY_BACKGROUND)) },null)
                            isClickable=false;isFocusable=false;isDuplicateParentStateEnabled=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        })
                        addView(TextView(context).apply {
                            text=tool.label;setTextColor(this@ObadhToolbar.text);textSize=12f;gravity=Gravity.CENTER
                            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        })
                        setOnClickListener { menu?.dismiss();tool.action?.invoke() ?: tool.key?.let { send(getCodeForToolbarKey(it)) } }
                    })
                }
                repeat(4-row.size) { addView(View(context),LinearLayout.LayoutParams(0,dp(72),1f)) }
            })
        }
        val scroll=ScrollView(context).apply { isFillViewport=true;addView(root);setBackgroundColor(this@ObadhToolbar.colors.get(ColorType.MAIN_BACKGROUND)) }
        val position=IntArray(2);keyboard.getLocationOnScreen(position)
        val nav = ViewCompat.getRootWindowInsets(keyboard)?.getInsets(WindowInsetsCompat.Type.navigationBars())
        val width = keyboard.width.coerceAtLeast(dp(120)) // The IME window already fits side navigation insets.
        val height = (keyboard.height - (nav?.bottom ?: 0)).coerceAtLeast(dp(80))
        menu = PopupWindow(scroll, width, height, false).apply {
            isOutsideTouchable=false;isClippingEnabled=true;setIsLaidOutInScreen(true)
            setBackgroundDrawable(ContextCompat.getDrawable(context, android.R.color.transparent))
            inputMethodMode=PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener { menu=null;setExpanded(false) }
            showAtLocation(strip,Gravity.TOP or Gravity.LEFT,position[0],position[1])
        }
        setExpanded(true)
    }

    /** Read-only debug touch coordinates; stripped from release along with its sole caller. */
    fun coordinatesForTests(): org.json.JSONArray {
        val result=org.json.JSONArray()
        fun collect(view: View) {
            if (!view.isShown) return
            view.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let { label ->
                val p=IntArray(2);view.getLocationOnScreen(p)
                result.put(org.json.JSONObject().put("label",label).put("x",p[0]+view.width/2).put("y",p[1]+view.height/2)
                    .put("width",view.width).put("height",view.height).put("enabled",view.isEnabled))
            }
            if(view is ViewGroup) for(i in 0 until view.childCount) collect(view.getChildAt(i))
        }
        collect(strip);menu?.contentView?.let(::collect)
        return result
    }

    fun close() { menu?.dismiss(); menu = null; toast?.cancel() }
}
