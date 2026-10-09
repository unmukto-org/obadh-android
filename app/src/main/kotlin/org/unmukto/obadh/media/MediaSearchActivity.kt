// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.keyboard.KeyboardTheme
import kotlinx.coroutines.*

/** A private search editor: provider queries can never be inserted into the recipient. */
class MediaSearchActivity : ComponentActivity() {
    private var token=""
    private lateinit var session: MediaController.Session
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var field: EditText
    private lateinit var clear: ImageButton
    private lateinit var grid: RecyclerView
    private lateinit var adapter: MediaGrid
    private lateinit var status: TextView
    private lateinit var retry: TextView
    private lateinit var progress: ProgressBar
    private lateinit var recent: ImageButton
    private var action: (() -> Unit)?=null
    private var request: Job?=null
    private var prepare: Job?=null
    private var generation=0
    private var ready=false
    private var chosen=false
    private var alive=true
    private var reducedMotion=false
    private var panelBackground=Color.BLACK
    private lateinit var mediaColors: Colors

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        token=intent.getStringExtra("token").orEmpty()
        session=MediaController.session(token) ?: run { finish();return }
        val memory=session.memory
        reducedMotion=android.provider.Settings.Global.getFloat(contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
        // Resolve from the current window configuration: the hidden IME may still
        // hold colors from before a system night-mode change.
        val colors=KeyboardTheme.getColorsForCurrentTheme(this);mediaColors=colors
        val fg=colors.get(ColorType.KEY_TEXT)
        val keyboardBackground=colors.get(ColorType.MAIN_BACKGROUND)
        // Photo keyboards use transparent surfaces. Media needs a readable solid canvas.
        val background=if(Color.alpha(keyboardBackground)==255)keyboardBackground else
            if(androidx.core.graphics.ColorUtils.calculateLuminance(fg)>.5)0xff1e1f20.toInt() else 0xfff0f4f9.toInt()
        panelBackground=background
        val navPaint=android.graphics.Paint().apply { color=background }
        val root=object: FrameLayout(this) {
            var navigationInset=0
            override fun dispatchDraw(canvas: android.graphics.Canvas) {
                super.dispatchDraw(canvas)
                if(navigationInset>0)canvas.drawRect(0f,(height-navigationInset).toFloat(),width.toFloat(),height.toFloat(),navPaint)
            }
        }.apply { setBackgroundColor(0x40000000);setOnClickListener { finish() } }
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(4),dp(8),0);setOnClickListener { }
            setBackgroundColor(background)
        }
        root.addView(body,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        fun icon(label: String,resource: Int,click: ()->Unit)=ImageButton(this).apply {
            contentDescription=label;setImageResource(resource);imageTintList=ColorStateList.valueOf(fg);scaleType=ImageView.ScaleType.CENTER
            this.background=RippleDrawable(ColorStateList.valueOf((fg and 0x00ffffff) or 0x22000000),GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(Color.TRANSPARENT) },null)
            setOnClickListener { click() }
        }
        val search=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        val searchFill=androidx.core.graphics.ColorUtils.blendARGB(background,fg,.04f)
        val back=icon("Back to keyboard",R.drawable.obadh_ic_arrow_back) { finish() }.apply {
            this.background=android.graphics.drawable.InsetDrawable(RippleDrawable(ColorStateList.valueOf((fg and 0x00ffffff) or 0x22000000),
                GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(searchFill) },null),dp(4))
        }
        search.addView(back,LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
        field=EditText(this).apply {
            id=android.R.id.edit;hint="Search KLIPY";contentDescription=session.kind.searchHint;textSize=16f;setSingleLine(true)
            setTextColor(fg);setHintTextColor((fg and 0x00ffffff) or 0x99000000.toInt());setPadding(dp(12),0,dp(12),0)
            inputType=android.text.InputType.TYPE_CLASS_TEXT
            imeOptions=EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_FULLSCREEN
            privateImeOptions=MediaController.SEARCH_OPTIONS;filters=arrayOf(InputFilter.LengthFilter(120))
            this.background=GradientDrawable().apply { cornerRadius=dp(24).toFloat();setColor(colors.get(ColorType.KEY_BACKGROUND)) }
            if(android.os.Build.VERSION.SDK_INT>=29)textCursorDrawable=GradientDrawable().apply { setColor(fg);setSize(dp(2),dp(22)) }
            setText(memory.query)
            setOnEditorActionListener { _,_,_ -> request?.cancel();load();getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken,0);true }
        }
        val pill=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            this.background=android.graphics.drawable.InsetDrawable(GradientDrawable().apply { cornerRadius=dp(24).toFloat();setColor(searchFill) },0,dp(4),0,dp(4))
            setPadding(dp(8),0,0,0)
        }
        pill.addView(ImageView(this).apply {
            setImageResource(R.drawable.obadh_ic_search);imageTintList=ColorStateList.valueOf(fg)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(root.dp(24),root.dp(48)))
        field.background=null;field.setPadding(root.dp(8),0,root.dp(4),0)
        pill.addView(field,LinearLayout.LayoutParams(0,root.dp(48),1f))
        clear=icon("Clear media search",R.drawable.obadh_ic_close) { field.setText("") }.apply {
            visibility=if(memory.query.isBlank())View.GONE else View.VISIBLE
        }
        pill.addView(clear,LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
        search.addView(pill,LinearLayout.LayoutParams(0,root.dp(48),1f).apply { setMargins(root.dp(4),0,root.dp(4),0) })
        recent=icon("Recent",org.unmukto.obadh.R.drawable.ic_media_recent) { switchRecent(!session.memory.recent) }
        search.addView(recent,LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
        body.addView(search)
        fun tab(title: String)=TextView(this).apply {
            text=title;contentDescription=title;textSize=14f;gravity=Gravity.CENTER;isFocusable=true;setTextColor(fg);setPadding(dp(12),0,dp(12),0)
            this.background=android.graphics.drawable.InsetDrawable(RippleDrawable(ColorStateList.valueOf((fg and 0x00ffffff) or 0x22000000),GradientDrawable().apply { cornerRadius=dp(24).toFloat();setColor(colors.get(ColorType.KEY_BACKGROUND)) },null),0,dp(4),0,dp(4))
            // InsetDrawable supplies its own padding; set label padding afterwards.
            setPadding(dp(16),0,dp(16),0);minWidth=dp(48)
        }
        adapter=MediaGrid(this,session.kind,colors,scope,{ MediaController.canRequest(token) },reducedMotion,::select)
        grid=RecyclerView(this).apply {
            this.adapter=this@MediaSearchActivity.adapter;itemAnimator=null;setHasFixedSize(true);clipToPadding=false
            layoutManager=if(session.kind==MediaKind.GIFS)StaggeredGridLayoutManager(2,StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy=StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            } else GridLayoutManager(this@MediaSearchActivity,4)
            setPadding(0,0,0,dp(4))
        }
        body.addView(grid,LinearLayout.LayoutParams(-1,root.dp(240)))
        val footer=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),0);minimumHeight=dp(32) }
        status=TextView(this).apply { textSize=12f;setTextColor(fg);maxLines=3 }
        footer.addView(status,LinearLayout.LayoutParams(0,-2,1f))
        retry=tab("Retry").apply { visibility=View.GONE;setOnClickListener { action?.invoke() } }
        footer.addView(retry,LinearLayout.LayoutParams(-2,root.dp(48)))
        footer.addView(ImageView(this).apply {
            setImageBitmap(android.graphics.BitmapFactory.decodeResource(resources,
                if(androidx.core.graphics.ColorUtils.calculateLuminance(background)>.4)
                    org.unmukto.obadh.R.drawable.klipy_powered_black else org.unmukto.obadh.R.drawable.klipy_powered_white,
                android.graphics.BitmapFactory.Options().apply { inSampleSize=4 }))
            scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Powered by KLIPY";setPadding(dp(4),dp(8),dp(4),dp(8))
        },LinearLayout.LayoutParams(root.dp(88),root.dp(32)))
        body.addView(footer)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate=true;visibility=View.GONE;indeterminateTintList=ColorStateList.valueOf(fg)
        }
        body.addView(progress,LinearLayout.LayoutParams(-1,root.dp(3)))
        setContentView(root)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            if(state==null)WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN else WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        window.navigationBarColor=background
        if(android.os.Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
        androidx.core.view.WindowInsetsControllerCompat(window,root).isAppearanceLightNavigationBars=
            androidx.core.graphics.ColorUtils.calculateLuminance(background)>.4
        ViewCompat.setOnApplyWindowInsetsListener(root) { _,insets ->
            val system=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            root.navigationInset=system.bottom
            root.setPadding(system.left,system.top,system.right,maxOf(system.bottom,ime.bottom))
            val available=(resources.displayMetrics.heightPixels-system.top-maxOf(system.bottom,ime.bottom)-root.dp(120)-maxOf(0,status.lineHeight*3-root.dp(48))).coerceAtLeast(root.dp(64))
            grid.layoutParams.height=minOf(root.dp(240),available)
            val columns=((resources.displayMetrics.widthPixels-system.left-system.right-root.dp(16))/root.dp(if(session.kind==MediaKind.GIFS)168 else 84))
                .coerceIn(if(session.kind==MediaKind.GIFS)2 else 3,6)
            when(val manager=grid.layoutManager) {
                is GridLayoutManager -> manager.spanCount=columns
                is StaggeredGridLayoutManager -> if(manager.spanCount!=columns)manager.spanCount=columns
            }
            // Hidden IME insets are normal while switching editor/rotation. Never close
            // search in response to them; only explicit navigation restores the recipient.
            insets
        }
        field.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                val query=s?.toString().orEmpty();if(query==memory.query)return
                memory.query=query;if(memory.query.isNotBlank())memory.recent=false;clear.visibility=if(memory.query.isBlank())View.GONE else View.VISIBLE;memory.page=0;memory.hasNext=false;request?.cancel()
                if(ready) { adapter.enabled=false;updateTabs();request=scope.launch { delay(600);load() } }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        grid.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView,dx: Int,dy: Int) {
                adapter.viewportChanged()
                memory.scroll=firstVisible()
                if(dy>0 && !memory.recent && memory.hasNext && request?.isActive!=true && prepare?.isActive!=true &&
                    lastVisible()>=adapter.itemCount-3)load(true)
            }
        })
        scope.launch {
            MediaPreferences.load(this@MediaSearchActivity)
            if(!alive)return@launch
            if(!memory.initialized) {
                memory.initialized=true
                if(memory.query.isBlank() && MediaPreferences.recent(session.kind).isNotEmpty())memory.recent=true
            }
            if(!MediaSafety.canAnimate(session.types)) {
                field.isEnabled=false;recent.isEnabled=false
                status.text="This app doesn't accept animated images. Use a compatible messaging app.";return@launch
            }
            if(!MediaPreferences.enabled) {
                mediaDialog("Enable GIFs and stickers?",
                    "Your media searches and accepted shares go to KLIPY with a random identifier for this installation. Ordinary typing, surrounding text and clipboard content stay on this device. Ads are disabled for Obadh.",
                    positive="Enable",cancel="Cancel",onCancel={ finish() },
                    onPositive={ MediaPreferences.setEnabled(true);ready=true;load() })
            } else {
                ready=true
                if(memory.page>0 && memory.items.isNotEmpty()) { adapter.replace(memory.items);grid.scrollToPosition(memory.scroll);updateTabs();status.text=if(memory.recent)"Recent ${session.kind.title}" else session.kind.title }
                else load()
            }
        }
        scope.launch {
            delay((session.expires-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1))
            if(alive && !chosen) {
                if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                    Toast.makeText(this@MediaSearchActivity,"Media search session ended. Reopen it from the keyboard.",Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    private fun firstVisible() = when(val manager=grid.layoutManager) {
        is GridLayoutManager -> manager.findFirstVisibleItemPosition().coerceAtLeast(0)
        is StaggeredGridLayoutManager -> manager.findFirstVisibleItemPositions(null).filter { it>=0 }.minOrNull() ?: 0
        else -> 0
    }
    private fun lastVisible() = when(val manager=grid.layoutManager) {
        is GridLayoutManager -> manager.findLastVisibleItemPosition()
        is StaggeredGridLayoutManager -> manager.findLastVisibleItemPositions(null).maxOrNull() ?: 0
        else -> 0
    }
    private fun switchRecent(isRecent: Boolean) {
        if(prepare?.isActive==true || !ready)return
        request?.cancel();val memory=session.memory
        memory.recent=isRecent;memory.page=0;memory.items=emptyList();field.setText("");load()
    }
    private fun updateTabs() {
        recent.isSelected=session.memory.recent;recent.alpha=if(recent.isSelected)1f else .65f
        if(android.os.Build.VERSION.SDK_INT>=30)recent.stateDescription=if(recent.isSelected)"Showing recent ${session.kind.title}. Tap for trending." else "Show recent ${session.kind.title}"
        val colors=mediaColors;val fg=colors.get(ColorType.KEY_TEXT)
        val selectedFill=androidx.core.graphics.ColorUtils.blendARGB(panelBackground,colors.get(ColorType.ACTION_KEY_BACKGROUND),.18f)
        val iconColor=if(!recent.isSelected || androidx.core.graphics.ColorUtils.calculateContrast(fg,selectedFill)>=3.0)fg
            else if(androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE,selectedFill)>=3.0)Color.WHITE else Color.BLACK
        recent.imageTintList=ColorStateList.valueOf(iconColor)
        recent.background=android.graphics.drawable.InsetDrawable(RippleDrawable(ColorStateList.valueOf((fg and 0x00ffffff) or 0x22000000),
            GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(if(recent.isSelected)selectedFill else Color.TRANSPARENT) },null),recent.dp(4))
        field.hint="Search KLIPY"
    }
    private fun load(more: Boolean=false) {
        if(!ready || !MediaController.canRequest(token))return
        val memory=session.memory;updateTabs();request?.cancel();val epoch=++generation
        if(memory.recent) {
            val rows=MediaPreferences.recent(session.kind).filter { memory.query.isBlank() || (it.title+" "+it.attribution).contains(memory.query,true) }
                .map { MediaItem(it.slug,it.title,it.attribution,it.preview,emptyList()) }
            adapter.enabled=true;adapter.replace(rows);memory.items=rows;memory.renderedQuery="";memory.page=1;memory.hasNext=false
            progress.visibility=View.GONE;retry.visibility=View.GONE
            status.text=if(rows.isEmpty())"Your recently sent ${session.kind.title.lowercase()} will appear here." else "Recent ${session.kind.title}"
            return
        }
        val query=memory.query;val page=if(more)memory.page+1 else 1
        if(!more)adapter.enabled=false
        progress.isIndeterminate=true;progress.visibility=View.VISIBLE;retry.visibility=View.GONE;status.text=if(more)"Loading more…" else "Searching KLIPY…"
        request=scope.launch {
            try {
                val result=KlipyClient.browse(session.kind,query,page,MediaPreferences.customer)
                ensureActive();if(epoch!=generation || !MediaController.canRequest(token))return@launch
                adapter.enabled=true
                if(more)adapter.append(result.items) else { adapter.replace(result.items);grid.scrollToPosition(0) }
                memory.items=adapter.items;memory.renderedQuery=query;memory.page=page;memory.hasNext=result.hasNext && adapter.itemCount<120
                status.text=if(adapter.itemCount==0)"No results. Try another word." else session.kind.title
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(epoch==generation) {
                    status.text=(e as? MediaException)?.failure?.message ?: MediaFailure.SERVER.message
                    retry.text="Retry";retry.visibility=View.VISIBLE;action={ load(more) }
                }
            } finally { if(epoch==generation)progress.visibility=View.GONE }
        }
    }
    private fun select(row: MediaItem) {
        if(prepare?.isActive==true || !MediaController.canRequest(token))return
        request?.cancel();generation++;val query=session.memory.renderedQuery
        adapter.enabled=false;adapter.playing=false;field.isEnabled=false
        clear.isEnabled=false;recent.isEnabled=false
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken,0)
        progress.visibility=View.VISIBLE;status.text="Preparing animation…";retry.text="Cancel";retry.visibility=View.VISIBLE
        action={ prepare?.cancel();status.text="Send canceled" }
        prepare=scope.launch {
            try {
                val item=if(row.files.isEmpty())KlipyClient.item(session.kind,row.slug) else row
                val file=MediaSafety.select(item.files,session.types) ?: throw MediaException(MediaFailure.INVALID)
                progress.isIndeterminate=false;progress.max=100;progress.progress=0
                val sendingJob=currentCoroutineContext()[Job]
                var lastUpdate=0L
                val bytes=KlipyClient.image(file,progress={ count ->
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(now-lastUpdate>=100 || count>=file.bytes) {
                        lastUpdate=now
                        scope.launch {
                            if(alive && sendingJob?.isActive==true) {
                                progress.progress=(100L*count/file.bytes).toInt().coerceIn(0,100)
                                status.text="Preparing animation… ${(count/1024).coerceAtLeast(1)} KB"
                            }
                        }
                    }
                })
                ensureActive()
                if(MediaController.choose(token,item,file,bytes,query)) { chosen=true;finish() }
                else status.text="The input field changed. Select the item again."
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                status.text=(e as? MediaException)?.failure?.message ?: MediaFailure.OFFLINE.message
                retry.text="Retry";action={ select(row) }
            } finally {
                if(alive) {
                    adapter.enabled=true;adapter.playing=!reducedMotion;field.isEnabled=true;progress.visibility=View.GONE
                    clear.isEnabled=true;recent.isEnabled=true
                    if(prepare?.isCancelled==true)retry.visibility=View.GONE
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if(token.isNotBlank() && MediaController.session(token)==null && !chosen) { finish();return }
        if(::adapter.isInitialized)adapter.playing=!reducedMotion
    }
    override fun onStop() {
        if(!isChangingConfigurations && !chosen)MediaController.cancel(token)
        if(::adapter.isInitialized)adapter.playing=false
        request?.cancel();prepare?.cancel();super.onStop()
    }
    override fun finish() { if(!chosen)MediaController.cancel(token);super.finish() }
    override fun onDestroy() {
        alive=false;scope.cancel();if(::adapter.isInitialized)adapter.dispose();if(::grid.isInitialized)grid.adapter=null
        if(!isChangingConfigurations && !chosen)MediaController.cancel(token)
        super.onDestroy()
    }
}
