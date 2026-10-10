// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** A short-lived delivery buffer, not a reusable original-media cache. Never writes to disk. */
object MediaDelivery {
    private const val TTL=90_000L
    private const val MAX_TOTAL=16*1024*1024
    data class Entry(var bytes: ByteArray?,val size: Int,val mime: String,val name: String,val expires: Long)
    private val entries=linkedMapOf<String,Entry>()
    private fun prune() { entries.entries.removeAll { it.value.expires<SystemClock.elapsedRealtime() } }
    @Synchronized fun put(authority: String,bytes: ByteArray,mime: String): Uri {
        prune()
        if(entries.size>=64 || entries.values.sumOf { it.bytes?.size ?: 0 }+bytes.size>MAX_TOTAL)throw MediaException(MediaFailure.BUSY)
        val token=UUID.randomUUID().toString();val name="obadh-${token.take(8)}.${if(mime=="image/gif")"gif" else "webp"}"
        entries[token]=Entry(bytes,bytes.size,mime,name,SystemClock.elapsedRealtime()+TTL)
        return Uri.Builder().scheme("content").authority(authority).appendPath("media").appendPath(token).appendPath(name).build()
    }
    private fun token(uri: Uri)=uri.pathSegments.takeIf { it.size==3 && it[0]=="media" }?.get(1)
    @Synchronized fun get(uri: Uri): Entry? { prune();return entries[token(uri)]?.takeIf { it.name==uri.lastPathSegment } }
    @Synchronized fun read(uri: Uri,offset: Long,size: Int,buffer: ByteArray): Int {
        val bytes=get(uri)?.bytes ?: throw ErrnoException("media read",OsConstants.ENOENT)
        if(offset<0 || size<0 || size>buffer.size)throw ErrnoException("media read",OsConstants.EINVAL)
        if(offset>=bytes.size || size==0)return 0
        val from=offset.toInt();val count=minOf(size,bytes.size-from)
        bytes.copyInto(buffer,0,from,from+count)
        return count
    }
    @Synchronized fun discard(uri: Uri) { entries.remove(token(uri)) }
    @Synchronized fun expire() { prune() }
}

/** Android enforces the temporary read grant. The URI contains no source URL or API key. */
class MediaContentProvider : ContentProvider() {
    private val openFiles=AtomicInteger()
    private val ioHandler by lazy {
        Handler(HandlerThread("Obadh media delivery",Process.THREAD_PRIORITY_BACKGROUND).apply { start() }.looper)
    }
    override fun onCreate()=true
    override fun getType(uri: Uri)=MediaDelivery.get(uri)?.mime
    override fun query(uri: Uri,projection: Array<out String>?,selection: String?,args: Array<out String>?,sort: String?): Cursor? {
        val entry=MediaDelivery.get(uri) ?: return null
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns,1).apply { addRow(Array<Any?>(columns.size) { when(columns[it]) {
            OpenableColumns.DISPLAY_NAME -> entry.name
            OpenableColumns.SIZE -> entry.size
            else -> null
        }}) }
    }
    override fun openFile(uri: Uri,mode: String): ParcelFileDescriptor {
        if(mode!="r")throw FileNotFoundException("Read-only media")
        MediaDelivery.get(uri) ?: throw FileNotFoundException("Media delivery expired. Select the item again.")
        if(openFiles.incrementAndGet()>32) { openFiles.decrementAndGet();throw FileNotFoundException("Too many open media files") }
        try {
            // Chat importers and image decoders seek/reopen content; a pipe fails
            // with ESPIPE. Public API 26 proxies a seekable, read-only descriptor
            // to our bounded memory buffer without persisting original media.
            return requireNotNull(context).getSystemService(StorageManager::class.java).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,object: ProxyFileDescriptorCallback() {
                    override fun onGetSize(): Long=MediaDelivery.get(uri)?.size?.toLong()
                        ?: throw ErrnoException("media size",OsConstants.ENOENT)
                    override fun onRead(offset: Long,size: Int,data: ByteArray)=MediaDelivery.read(uri,offset,size,data)
                    override fun onRelease() { openFiles.decrementAndGet() }
                },ioHandler)
        } catch(e: Exception) {
            openFiles.decrementAndGet()
            throw FileNotFoundException("Unable to open media delivery").apply { initCause(e) }
        }
    }
    override fun insert(uri: Uri,values: ContentValues?): Uri?=throw UnsupportedOperationException()
    override fun delete(uri: Uri,selection: String?,args: Array<out String>?): Int=throw UnsupportedOperationException()
    override fun update(uri: Uri,values: ContentValues?,selection: String?,args: Array<out String>?): Int=throw UnsupportedOperationException()
}
