// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

internal object StickerIO {
    val executor = Executors.newSingleThreadExecutor { Thread(it,"Obadh sticker files").apply { priority=Thread.NORM_PRIORITY-1 } }
    val main = Handler(Looper.getMainLooper())
}
/** Visible-cell thumbnails only, 4 MiB cache and bounded work queue. No animated decoders. */
internal object StickerImages {
    private val cache=object : LruCache<String,Bitmap>(4*1024*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.allocationByteCount
    }
    private val workers=ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,ArrayBlockingQueue(96),
        { task -> Thread(task,"Obadh sticker thumbnails").apply { priority=Thread.NORM_PRIORITY-1 } },ThreadPoolExecutor.DiscardOldestPolicy())
    fun bind(context: Context, view: ImageView, pack: StickerPack, item: Sticker, alive: () -> Boolean) {
        val key=pack.sha256+":"+item.id
        view.tag=key;view.setImageDrawable(null)
        cache.get(key)?.let { view.setImageBitmap(it);return }
        val reference=WeakReference(view);val app=context.applicationContext
        workers.execute {
            if(!alive() || reference.get()?.tag!=key) return@execute
            val bitmap=runCatching { pack.open(app,"thumbs/${item.id}.webp").use { input ->
                val data=StickerSafety.readLimited(input,StickerSafety.MAX_FILE)
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(data,0,data.size,bounds)
                if(bounds.outWidth !in 1..80 || bounds.outHeight !in 1..80) return@use null
                BitmapFactory.decodeByteArray(data,0,data.size)
            }}.getOrNull() ?: return@execute
            cache.put(key,bitmap)
            StickerIO.main.post { reference.get()?.takeIf { alive() && it.isAttachedToWindow && it.tag==key }?.setImageBitmap(bitmap) }
        }
    }
    fun trim() { cache.evictAll() }
}
