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
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.*

internal fun View.dp(n: Int)=(resources.displayMetrics.density*n+.5f).toInt()

/** Recycled thumbnails; bounded viewport, cancel on detach and stop every hidden animation. */
internal class MediaGrid(private val context: Context,private val kind: MediaKind,
    private val scope: CoroutineScope,private val network: ()->Boolean,private val reducedMotion: Boolean,private val selected: (MediaItem)->Unit): RecyclerView.Adapter<MediaGrid.Cell>() {
    private val colors=Settings.getValues().mColors
    private val attached=mutableSetOf<Cell>()
    var playing=true
        set(value) { field=value;attached.forEach(::animate) }
    var enabled=true
        set(value) { field=value;attached.forEach { it.root.isEnabled=value } }
    var items: List<MediaItem> = emptyList()
        private set
    fun replace(rows: List<MediaItem>) { items=rows;notifyDataSetChanged() }
    fun append(rows: List<MediaItem>) { val old=items.size;items=(items+rows).distinctBy { it.slug };notifyItemRangeInserted(old,items.size-old) }
    class Cell(val root: LinearLayout,val image: ImageView,val credit: TextView,val tileWidth: Int): RecyclerView.ViewHolder(root) {
        var job: Job?=null;var item: MediaItem?=null
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
            background=GradientDrawable().apply { cornerRadius=dp(12).toFloat();setColor(this@MediaGrid.colors.get(ColorType.KEY_BACKGROUND)) }
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
        cell.root.setOnClickListener { if(enabled)selected(item) }
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
        cell.job?.cancel()
        cell.image.setImageResource(if(kind==MediaKind.GIFS)R.drawable.obadh_ic_gif else R.drawable.obadh_ic_sticker)
        cell.image.imageTintList=ColorStateList.valueOf(colors.get(ColorType.KEY_TEXT));cell.image.alpha=.35f
        cell.job=scope.launch {
            try {
                val drawable=MediaThumbnails.drawable(context,item.preview,network(),reducedMotion)
                ensureActive();if(cell.item?.slug!=item.slug)return@launch
                cell.image.imageTintList=null;cell.image.alpha=1f;cell.image.setImageDrawable(drawable);animate(cell)
            } catch(_: CancellationException) { }
            catch(_: Exception) { cell.image.alpha=.5f } // Sending still provides an explicit network/retry state.
        }
    }
    private fun animate(cell: Cell) {
        val animation=cell.image.drawable as? Animatable ?: return
        if(playing && attached.take(12).contains(cell) && cell.image.isShown)animation.start() else animation.stop()
    }
    override fun onViewAttachedToWindow(cell: Cell) {
        attached.add(cell);if(cell.job?.isCancelled==true)load(cell);animate(cell)
    }
    override fun onViewDetachedFromWindow(cell: Cell) { attached.remove(cell);cell.job?.cancel();(cell.image.drawable as? Animatable)?.stop();attached.forEach(::animate) }
    override fun onViewRecycled(cell: Cell) {
        attached.remove(cell);cell.job?.cancel();(cell.image.drawable as? Animatable)?.stop();cell.image.setImageDrawable(null);cell.item=null
    }
    fun dispose() { playing=false;attached.forEach { it.job?.cancel();it.image.setImageDrawable(null) };attached.clear() }
}
