// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import kotlinx.coroutines.*

internal fun View.dp(n: Int)=(resources.displayMetrics.density*n+.5f).toInt()

/** Recycled thumbnails; bounded viewport, cancel on detach and stop every hidden animation. */
internal class MediaGrid(private val context: Context,private val kind: MediaKind,private val colors: Colors,
    private val scope: CoroutineScope,private val network: ()->Boolean,private val reducedMotion: Boolean,private val selected: (MediaItem)->Unit): RecyclerView.Adapter<MediaGrid.Cell>() {
    private val attached=mutableSetOf<Cell>()
    private val visibleRect=android.graphics.Rect()
    var playing=true
        set(value) { field=value;viewportChanged() }
    var enabled=true
        set(value) { field=value;attached.forEach { it.root.isEnabled=value && (it.ready || it.failed) } }
    var items: List<MediaItem> = emptyList()
        private set
    fun replace(rows: List<MediaItem>) { items=rows;notifyDataSetChanged() }
    fun append(rows: List<MediaItem>) { val old=items.size;items=(items+rows).distinctBy { it.slug };notifyItemRangeInserted(old,items.size-old) }
    class Cell(val root: LinearLayout,val image: ImageView,val credit: TextView,val tileWidth: Int): RecyclerView.ViewHolder(root) {
        var job: Job?=null;var item: MediaItem?=null;var ready=false;var failed=false
    }
    override fun getItemCount()=items.size
    override fun onCreateViewHolder(parent: ViewGroup,type: Int): Cell {
        val root=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isFocusable=true
            layoutParams=RecyclerView.LayoutParams(-1,dp(if(kind==MediaKind.GIFS)144 else 120))
            setPadding(dp(4),dp(4),dp(4),dp(4))
            background=RippleDrawable(ColorStateList.valueOf((colors.get(ColorType.KEY_TEXT) and 0x00ffffff) or 0x22000000),
                GradientDrawable().apply { cornerRadius=dp(12).toFloat();setColor(Color.TRANSPARENT) },null)
        }
        val image=ImageView(context).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background=GradientDrawable().apply { cornerRadius=dp(4).toFloat();setColor(this@MediaGrid.colors.get(ColorType.KEY_BACKGROUND)) }
            clipToOutline=true
        }
        val credit=TextView(context).apply {
            textSize=10f;gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            setTextColor(colors.get(ColorType.KEY_TEXT));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(credit,LinearLayout.LayoutParams(-1,-2))
        val manager=(parent as RecyclerView).layoutManager
        val columns=if(manager is StaggeredGridLayoutManager)manager.spanCount else (manager as GridLayoutManager).spanCount
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> viewportChanged() }
        return Cell(root,image,credit,(parent.width-parent.paddingLeft-parent.paddingRight)/columns-root.dp(8))
    }
    override fun onBindViewHolder(cell: Cell,position: Int) {
        cell.job?.cancel();(cell.image.drawable as? Animatable)?.stop();cell.image.setImageDrawable(null)
        val item=items[position];cell.item=item;cell.root.isEnabled=enabled
        cell.root.contentDescription="${item.title}, ${kind.title}"+if(item.attribution.isBlank())"" else ", ${item.attribution}"
        val artHeight=if(kind==MediaKind.GIFS)(cell.tileWidth.toLong()*item.preview.height/item.preview.width).toInt().coerceIn(cell.root.dp(72),cell.root.dp(192))
            else cell.tileWidth.coerceAtMost(cell.root.dp(112))
        cell.root.layoutParams.height=artHeight+cell.root.dp(8)+if(item.attribution.isBlank())0 else cell.credit.lineHeight
        cell.credit.text=item.attribution;cell.credit.visibility=if(item.attribution.isBlank())View.GONE else View.VISIBLE
        cell.root.setOnClickListener { if(enabled) { if(cell.ready)selected(item) else if(cell.failed)load(cell) } }
        // Full creator/source attribution is available without truncation, even in compact cells.
        cell.root.setOnLongClickListener {
            (context as androidx.activity.ComponentActivity).mediaDialog(item.title,
                (item.attribution.takeIf(String::isNotBlank)?.plus("\n\n").orEmpty())+"Powered by KLIPY")
            true
        }
        load(cell)
    }
    private fun load(cell: Cell) {
        val item=cell.item ?: return
        cell.job?.cancel();cell.ready=false;cell.failed=false;cell.root.isEnabled=false
        cell.root.contentDescription="${item.title}, ${kind.title}, loading preview"
        cell.image.scaleType=ImageView.ScaleType.CENTER
        cell.image.setImageResource(if(kind==MediaKind.GIFS)R.drawable.obadh_ic_gif else R.drawable.obadh_ic_sticker)
        cell.image.imageTintList=ColorStateList.valueOf(colors.get(ColorType.KEY_TEXT));cell.image.alpha=.35f
        cell.job=scope.launch {
            try {
                val drawable=MediaThumbnails.drawable(context,item.preview,network(),reducedMotion)
                ensureActive();if(cell.item?.slug!=item.slug)return@launch
                cell.image.imageTintList=null;cell.image.alpha=1f;cell.image.scaleType=ImageView.ScaleType.FIT_CENTER;cell.image.setImageDrawable(drawable)
                cell.ready=true;cell.root.isEnabled=enabled
                cell.root.contentDescription="${item.title}, ${kind.title}"+if(item.attribution.isBlank())"" else ", ${item.attribution}"
                if(item.attribution.isBlank() && cell.credit.visibility==View.VISIBLE) {
                    cell.root.layoutParams.height-=cell.credit.lineHeight;cell.credit.visibility=View.GONE
                }
                cell.credit.text=item.attribution;viewportChanged()
            } catch(_: CancellationException) { }
            catch(_: Exception) {
                if(cell.item?.slug!=item.slug)return@launch
                cell.failed=true;cell.root.isEnabled=enabled;cell.image.alpha=.5f
                cell.root.contentDescription="${item.title}, ${kind.title}, preview unavailable. Tap to retry."
                if(cell.credit.visibility==View.GONE)cell.root.layoutParams.height+=cell.credit.lineHeight
                cell.credit.text=if(item.attribution.isBlank())"Tap to retry" else "${item.attribution} · Tap to retry"
                cell.credit.visibility=View.VISIBLE
            }
        }
    }
    fun viewportChanged() {
        var budget=12
        for(cell in attached) {
            val animation=cell.image.drawable as? Animatable ?: continue
            if(playing && budget>0 && cell.image.isShown && cell.image.getGlobalVisibleRect(visibleRect) && !visibleRect.isEmpty) {
                animation.start();budget--
            } else animation.stop()
        }
    }
    override fun onViewAttachedToWindow(cell: Cell) {
        attached.add(cell);if(cell.job?.isCancelled==true)load(cell);viewportChanged()
    }
    override fun onViewDetachedFromWindow(cell: Cell) { attached.remove(cell);cell.job?.cancel();(cell.image.drawable as? Animatable)?.stop();viewportChanged() }
    override fun onViewRecycled(cell: Cell) {
        attached.remove(cell);cell.job?.cancel();(cell.image.drawable as? Animatable)?.stop();cell.image.setImageDrawable(null);cell.item=null
    }
    fun dispose() { playing=false;attached.forEach { it.job?.cancel();it.image.setImageDrawable(null) };attached.clear() }
}
