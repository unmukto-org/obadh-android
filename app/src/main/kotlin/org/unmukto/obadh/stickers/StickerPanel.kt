// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import org.unmukto.obadh.keyboard.ObadhInputMethodService

internal fun trimStickerMemory() = StickerImages.trim()
internal fun View.dp(value: Int)=(value*resources.displayMetrics.density+.5f).toInt()

/** Recycled, fixed-size cells; live views never own image/network workers. */
internal class StickerGrid(private val context: android.content.Context, private val alive: () -> Boolean,
    private val select: (StickerPack,Sticker)->Unit, private val favorite: ((StickerPack,Sticker)->Unit)?=null) : RecyclerView.Adapter<StickerGrid.Cell>() {
    private val colors=Settings.getValues().mColors
    var enabled=true
    var items: List<Pair<StickerPack,Sticker>> = emptyList()
        set(value) { field=value;notifyDataSetChanged() }
    class Cell(val root: LinearLayout,val image: ImageView,val name: TextView): RecyclerView.ViewHolder(root)
    override fun onCreateViewHolder(parent: ViewGroup,type: Int): Cell {
        val root=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
            layoutParams=RecyclerView.LayoutParams(-1,dp(80))
            background=RippleDrawable(ColorStateList.valueOf(colors.get(ColorType.KEY_TEXT) and 0x00ffffff or 0x22000000),
                GradientDrawable().apply { cornerRadius=dp(12).toFloat();setColor(Color.TRANSPARENT) },null)
            isFocusable=true
        }
        val image=ImageView(context).apply { layoutParams=LinearLayout.LayoutParams(root.dp(56),root.dp(56));scaleType=ImageView.ScaleType.FIT_CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val name=TextView(context).apply { textSize=10f;gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(colors.get(ColorType.KEY_TEXT));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(image);root.addView(name,LinearLayout.LayoutParams(-1,-2));return Cell(root,image,name)
    }
    override fun getItemCount()=items.size
    override fun onBindViewHolder(holder: Cell,position: Int) {
        val (pack,item)=items[position];val installed=true // Dataset contains only packs verified available on the IO worker.
        holder.root.contentDescription="${item.name}, ${pack.title}" + if(!installed) ", download pack" else ", sticker"
        holder.root.isEnabled=enabled || !installed;holder.root.alpha=if(enabled || !installed) 1f else .45f
        holder.root.setOnClickListener { if(alive()) select(pack,item) }
        holder.root.setOnLongClickListener { if(enabled && installed && alive() && favorite!=null) { favorite.invoke(pack,item);true } else false }
        holder.name.text=if(installed) item.name else "↓ ${item.name}"
        if(installed) StickerImages.bind(context,holder.image,pack,item,alive)
        else { holder.image.tag=null;holder.image.setImageResource(R.drawable.obadh_ic_sticker);holder.image.imageTintList=ColorStateList.valueOf(colors.get(ColorType.KEY_TEXT)) }
        if(installed) holder.image.imageTintList=null
    }
    override fun onViewRecycled(holder: Cell) { holder.image.tag=null;holder.image.setImageDrawable(null) }
}

internal object StickerPanel {
    fun create(ime: ObadhInputMethodService,anchor: View,onDismiss: () -> Unit): PopupWindow? {
        val keyboard=anchor.rootView.findViewById<View>(R.id.keyboard_view_wrapper) ?: return null
        if(keyboard.height<=0) return null
        val context=anchor.context;val colors=Settings.getValues().mColors;val foreground=colors.get(ColorType.KEY_TEXT)
        val backgroundColor=colors.get(ColorType.MAIN_BACKGROUND);val incognito=Settings.getValues().mIncognitoModeEnabled
        var alive=true;var loaded: List<StickerPack> = emptyList();var selected="all"
        val root=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(backgroundColor);setPadding(dp(8),0,dp(8),0) }
        fun label(title: String,action: () -> Unit) = TextView(context).apply {
            text=title;contentDescription=title;textSize=14f;gravity=Gravity.CENTER;setTextColor(foreground);isFocusable=true
            setPadding(dp(12),0,dp(12),0)
            background=RippleDrawable(ColorStateList.valueOf(foreground and 0x00ffffff or 0x22000000),
                GradientDrawable().apply { cornerRadius=dp(24).toFloat();setColor(colors.get(ColorType.KEY_BACKGROUND)) },null)
            setOnClickListener { action() }
        }
        val nav=ViewCompat.getRootWindowInsets(keyboard)?.getInsets(WindowInsetsCompat.Type.navigationBars())
        val compact=keyboard.height-(nav?.bottom ?: 0)<root.dp(180)
        fun icon(title: String,resource: Int,action: ()->Unit)=ImageButton(context).apply {
            contentDescription=title;setImageResource(resource);imageTintList=ColorStateList.valueOf(foreground)
            background=RippleDrawable(ColorStateList.valueOf(foreground and 0x00ffffff or 0x22000000),
                GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(Color.TRANSPARENT) },null)
            setOnClickListener { action() }
        }
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        val tabs=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        val tabScroll=HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false;addView(tabs) }
        if(compact) {
            header.addView(icon("Search stickers",R.drawable.obadh_ic_search) { StickerController.startSearch(ime) },LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
            header.addView(tabScroll,LinearLayout.LayoutParams(0,root.dp(48),1f))
        } else {
            val search=LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL;contentDescription="Search stickers";isFocusable=true
                setPadding(dp(12),0,dp(12),0)
                background=GradientDrawable().apply { cornerRadius=root.dp(24).toFloat();setColor(colors.get(ColorType.KEY_BACKGROUND)) }
                setOnClickListener { StickerController.startSearch(ime) }
                addView(ImageView(context).apply { setImageResource(R.drawable.obadh_ic_search);imageTintList=ColorStateList.valueOf(foreground);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(24),dp(24)))
                addView(TextView(context).apply { text="Search stickers";textSize=14f;setTextColor(foreground);setPadding(dp(12),0,0,0);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO })
            }
            header.addView(search,LinearLayout.LayoutParams(0,root.dp(44),1f).apply { setMargins(root.dp(4),root.dp(2),root.dp(4),root.dp(2)) })
        }
        header.addView(icon("Add sticker packs",R.drawable.obadh_ic_add) { StickerController.openManager(context) },LinearLayout.LayoutParams(root.dp(48),root.dp(48)))
        root.addView(header)
        if(!compact) root.addView(tabScroll,LinearLayout.LayoutParams(-1,root.dp(40)))
        val message=TextView(context).apply { textSize=12f;gravity=Gravity.CENTER;setTextColor(foreground);text="This app doesn't accept keyboard stickers";visibility=if(StickerController.supported(ime))View.GONE else View.VISIBLE }
        root.addView(message,LinearLayout.LayoutParams(-1,root.dp(28)))
        val adapter=StickerGrid(context,{ alive },{ p,s -> StickerController.send(ime,p,s) },{ p,s ->
            val text=if(incognito) "Favorites are paused in incognito" else if(StickerHistory.favorite(context,p,s)) "Added to favorites" else "Removed from favorites"
            Toast.makeText(context,text,Toast.LENGTH_SHORT).show()
        }).apply { enabled=StickerController.supported(ime) }
        val list=RecyclerView(context).apply {
            layoutManager=GridLayoutManager(context,(keyboard.width/root.dp(84)).coerceIn(2,10));this.adapter=adapter;itemAnimator=null
            setHasFixedSize(true);clipToPadding=false;setPadding(0,0,0,root.dp(8))
        }
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        fun display(id: String) {
            selected=id
            adapter.items=when(id) {
                "recent","favorites" -> {
                    val map=loaded.flatMap { p -> p.items.map { s -> StickerHistory.key(p,s) to (p to s) } }.toMap()
                    StickerHistory.ids(context,id).mapNotNull(map::get)
                }
                "all" -> loaded.flatMap { p -> p.items.map { p to it } }
                else -> loaded.firstOrNull { it.id==id }?.let { p -> p.items.map { p to it } }.orEmpty()
            }
            list.scrollToPosition(0)
            if(adapter.items.isEmpty()) message.text=if(id=="favorites") "Long-press a sticker to add it here" else "Your recent stickers will appear here"
            else message.text="This app doesn't accept keyboard stickers"
            message.contentDescription=message.text
            message.visibility=if(adapter.items.isEmpty() || !adapter.enabled) View.VISIBLE else View.GONE
            for(i in 0 until tabs.childCount) tabs.getChildAt(i).alpha=if(tabs.getChildAt(i).tag==id) 1f else .6f
        }
        StickerIO.executor.execute {
            val result=runCatching { StickerHistory.load(context);StickerCatalog.load(context).filter { it.available(context) } }
            StickerIO.main.post {
                if(!alive) return@post
                result.onSuccess { packs ->
                    loaded=packs
                    val categories=buildList { add("all" to "All");if(!incognito)add("recent" to "Recent");add("favorites" to "Favorites");packs.forEach { add(it.id to it.title) } }
                    categories.forEach { (id,title) -> tabs.addView(label(title) { display(id) }.apply { tag=id },LinearLayout.LayoutParams(-2,root.dp(36)).apply { setMargins(root.dp(3),0,root.dp(3),0) }) }
                    display(selected)
                }.onFailure { message.text="Couldn't load stickers. Try reopening the keyboard.";message.contentDescription=message.text;message.visibility=View.VISIBLE }
            }
        }
        val position=IntArray(2);keyboard.getLocationOnScreen(position)
        return PopupWindow(root,keyboard.width,(keyboard.height-(nav?.bottom ?: 0)).coerceAtLeast(root.dp(100)),false).apply {
            isOutsideTouchable=false;isClippingEnabled=true;if(android.os.Build.VERSION.SDK_INT>=29)setIsLaidOutInScreen(true);setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            inputMethodMode=PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener { alive=false;adapter.items=emptyList();onDismiss() }
            showAtLocation(anchor,Gravity.TOP or Gravity.LEFT,position[0],position[1])
        }
    }
}
