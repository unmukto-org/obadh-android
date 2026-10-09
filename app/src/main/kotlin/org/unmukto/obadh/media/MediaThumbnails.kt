// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Only <=256px provider thumbnails may be cached. Originals never enter this class. */
object MediaThumbnails {
    private const val CACHE_BYTES=6L*1024*1024
    private const val CACHE_FILES=64
    private val filesLock=Any()
    private val decodeSlots=Semaphore(2)
    private fun directory(context: Context)=File(context.cacheDir,"klipy-thumbnails")
    private fun cachedFile(context: Context,file: MediaFile)=File(directory(context),MediaSafety.digest(file.url.toByteArray()))
    internal suspend fun warm(context: Context,file: MediaFile,network: Boolean=true): ByteArray {
        return withContext(Dispatchers.IO) {
            val cached=cachedFile(context,file)
            runCatching { synchronized(filesLock) {
                if(cached.length() in 1..MediaSafety.MAX_THUMB.toLong() && cached.lastModified()>=System.currentTimeMillis()-7*24*60*60*1000L) cached.inputStream().use { input ->
                    MediaSafety.read(input,MediaSafety.MAX_THUMB).takeIf { MediaSafety.valid(it,file,true) }
                        ?.also { cached.setLastModified(System.currentTimeMillis()) }
                } else null
            }}.getOrNull()
        } ?: run {
            if(!network)throw MediaException(MediaFailure.OFFLINE)
            val fetched=KlipyClient.image(file,true)
            withContext(Dispatchers.IO) {
                ensureActive()
                runCatching { synchronized(filesLock) {
                    // A settings clear may win the lock after this job was canceled.
                    ensureActive()
                    val dir=directory(context).apply { mkdirs() }
                    val stage=File(dir,".tmp-${UUID.randomUUID()}")
                    try { stage.writeBytes(fetched);stage.renameTo(cachedFile(context,file)) } finally { stage.delete() }
                    prune(dir)
                }}
            }
            fetched
        }
    }
    suspend fun drawable(context: Context,file: MediaFile,network: Boolean,reducedMotion: Boolean=false): Drawable {
        val data=warm(context,file,network)
        return withContext(Dispatchers.Default) { decodeSlots.withPermit {
            ensureActive()
            @Suppress("DEPRECATION")
            val poster=if(reducedMotion && file.mime=="image/gif")Movie.decodeByteArray(data,0,data.size) else null
            if(poster!=null)poster(context,poster)
            else if(Build.VERSION.SDK_INT>=28) {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder,info,_ ->
                    if(info.size.width !in 1..256 || info.size.height !in 1..256)throw MediaException(MediaFailure.INVALID)
                    decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                val movie=if(file.mime=="image/gif")Movie.decodeByteArray(data,0,data.size) else null
                if(movie!=null)poster(context,movie)
                else android.graphics.drawable.BitmapDrawable(context.resources,BitmapFactory.decodeByteArray(data,0,data.size)
                    ?: throw MediaException(MediaFailure.INVALID))
            }
        }}
    }
    private fun prune(dir: File) {
        // Retain recently sent previews ahead of disposable search thumbnails.
        val pinned=MediaKind.entries.flatMap(MediaPreferences::recent).map { MediaSafety.digest(it.preview.url.toByteArray()) }.toSet()
        val files=dir.listFiles()?.filter { !it.name.startsWith(".tmp-") }?.sortedWith(
            compareBy<File> { if(it.name in pinned)1 else 0 }.thenBy { it.lastModified() }).orEmpty()
        var bytes=files.sumOf { it.length() };var count=files.size
        for(file in files) {
            if(bytes>CACHE_BYTES || count>CACHE_FILES || file.lastModified()<System.currentTimeMillis()-7*24*60*60*1000L) {
                bytes-=file.length();count--;file.delete()
            }
        }
        dir.listFiles()?.filter { it.name.startsWith(".tmp-") && it.lastModified()<System.currentTimeMillis()-60_000 }?.forEach { it.delete() }
    }
    suspend fun clear(context: Context)=withContext(Dispatchers.IO) { synchronized(filesLock) { directory(context).deleteRecursively() };Unit }
    /** Rasterize one representative frame; never retain a whole Movie per visible tile. */
    @Suppress("DEPRECATION")
    private fun poster(context: Context,movie: Movie): Drawable {
        val width=movie.width();val height=movie.height()
        if(width !in 1..256 || height !in 1..256)throw MediaException(MediaFailure.INVALID)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val duration=movie.duration().takeIf { it>0 } ?: 1000
        movie.setTime(minOf(250,duration/2));movie.draw(Canvas(bitmap),0f,0f)
        return android.graphics.drawable.BitmapDrawable(context.resources,bitmap)
    }
}
