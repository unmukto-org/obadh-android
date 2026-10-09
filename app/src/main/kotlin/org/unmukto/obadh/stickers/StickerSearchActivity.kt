// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import helium314.keyboard.latin.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** A private transient search editor. The original recipient never receives search text. */
class StickerSearchActivity : Activity() {
    private lateinit var token: String
    private var alive=true
    private var queryGeneration=0
    private var selected=false
    private var imeWasVisible=false
    private lateinit var field: EditText
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        token=intent.getStringExtra("token").orEmpty()
        if(!StickerController.validSearch(token)) { finish();return }
        val colors=Settings.getValues().mColors;val fg=colors.get(ColorType.KEY_TEXT)
        val root=FrameLayout(this).apply { setBackgroundColor(0x40000000);setOnClickListener { finish() } }
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(colors.get(ColorType.MAIN_BACKGROUND));setOnClickListener {} }
        root.addView(body,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        fun icon(label: String,resource: Int,action: ()->Unit)=ImageButton(this).apply {
            contentDescription=label;setImageResource(resource);imageTintList=ColorStateList.valueOf(fg)
            background=RippleDrawable(ColorStateList.valueOf(fg and 0x00ffffff or 0x22000000),GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(android.graphics.Color.TRANSPARENT) },null)
            setOnClickListener { action() }
        }
        row.addView(icon("Back to stickers",R.drawable.obadh_ic_arrow_back) { finish() },LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
        field=EditText(this).apply {
            id=android.R.id.edit;hint="Search stickers";contentDescription="Search stickers";textSize=16f
            setSingleLine(true);setTextColor(fg);setHintTextColor(fg and 0x00ffffff or 0x99000000.toInt())
            inputType=android.text.InputType.TYPE_CLASS_TEXT;imeOptions=EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            privateImeOptions="obadh.sticker.search";setPadding(dp(12),0,dp(12),0)
            background=GradientDrawable().apply { cornerRadius=dp(24).toFloat();setColor(colors.get(ColorType.KEY_BACKGROUND)) }
            if(android.os.Build.VERSION.SDK_INT>=29) textCursorDrawable=GradientDrawable().apply { setColor(fg);setSize(dp(2),dp(22)) }
        }
        row.addView(field,LinearLayout.LayoutParams(0,root.dp(48),1f))
        row.addView(icon("Clear sticker search",R.drawable.obadh_ic_close) { field.setText("") },LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
        body.addView(row)
        val status=TextView(this).apply { textSize=12f;setTextColor(fg);setPadding(dp(16),0,dp(16),0);text="Search downloaded stickers · English or বাংলা" }
        body.addView(status)
        val adapter=StickerGrid(this,{ alive },{ p,s -> selected=true;StickerController.chooseSearch(token,p,s);finish() },{ p,s ->
            Toast.makeText(this,if(StickerController.incognitoSearch(token)) "Favorites are paused in incognito" else if(StickerHistory.favorite(this,p,s)) "Added to favorites" else "Removed from favorites",Toast.LENGTH_SHORT).show()
        })
        val list=RecyclerView(this).apply { this.adapter=adapter;itemAnimator=null;layoutManager=GridLayoutManager(this@StickerSearchActivity,4);setHasFixedSize(true) }
        body.addView(list,LinearLayout.LayoutParams(-1,root.dp(180)))
        val more=TextView(this).apply { text="Get more packs";contentDescription="Get more sticker packs";textSize=14f;gravity=Gravity.CENTER;setTextColor(fg);setOnClickListener { StickerController.openManager(this@StickerSearchActivity) } }
        body.addView(more,LinearLayout.LayoutParams(-1,root.dp(40)))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _,insets ->
            val system=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            val visible=insets.isVisible(WindowInsetsCompat.Type.ime())
            if(imeWasVisible && !visible && !selected && !isChangingConfigurations && hasWindowFocus()) finish()
            if(visible) imeWasVisible=true
            root.setPadding(system.left,system.top,system.right,maxOf(system.bottom,ime.bottom))
            val available=(resources.displayMetrics.heightPixels-system.top-maxOf(system.bottom,ime.bottom)-root.dp(120)).coerceAtLeast(root.dp(64))
            list.layoutParams.height=minOf(root.dp(180),available)
            (list.layoutManager as GridLayoutManager).spanCount=((resources.displayMetrics.widthPixels-system.left-system.right)/root.dp(84)).coerceIn(2,10)
            insets
        }
        fun search(text: String) {
            val epoch=++queryGeneration
            StickerIO.main.removeCallbacksAndMessages(this)
            StickerIO.main.postAtTime({
                StickerIO.executor.execute {
                    val result=runCatching {
                        StickerHistory.load(this);val all=StickerCatalog.load(this);val installed=all.filter { it.available(this) }
                        val matches=StickerCatalog.search(installed,text)
                        val available=if(text.isNotBlank() && matches.isEmpty()) StickerCatalog.search(all,text).map { it.first }.distinctBy { it.id } else emptyList()
                        matches to available
                    }
                    StickerIO.main.post {
                        if(!alive || epoch!=queryGeneration) return@post
                        result.onSuccess { (matches,available) ->
                            adapter.items=matches;list.scrollToPosition(0)
                            status.text=when {
                                matches.isNotEmpty() -> "${matches.size} results · search stays on this device"
                                available.isNotEmpty() -> "Matching stickers in ${available.take(2).joinToString { it.title }} · Get more packs"
                                else -> "No stickers found. Try another word or download a pack."
                            }
                        }.onFailure { status.text="Couldn't load stickers. Please try again." }
                    }
                }
            },this,android.os.SystemClock.uptimeMillis()+120)
        }
        field.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { search(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        field.setText(state?.getString("query").orEmpty())
        search(field.text.toString());field.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        field.postDelayed({ if(alive)getSystemService(InputMethodManager::class.java).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT) },150)
    }
    override fun onSaveInstanceState(outState: Bundle) { if(::field.isInitialized)outState.putString("query",field.text.toString());super.onSaveInstanceState(outState) }
    override fun onDestroy() { alive=false;queryGeneration++;StickerIO.main.removeCallbacksAndMessages(this);if(!selected && !isChangingConfigurations)StickerController.cancelSearch(token);super.onDestroy() }
}
