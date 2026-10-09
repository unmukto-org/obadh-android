// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.stickers

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object StickerDownloads {
    internal val installLock = Any()
    fun name(id: String) = "sticker-pack-$id"
    fun start(context: Context, pack: StickerPack) {
        if (pack.bundled) return
        WorkManager.getInstance(context).enqueueUniqueWork(name(pack.id), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<StickerInstallWorker>().setInputData(workDataOf("pack" to pack.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
    suspend fun cancel(context: Context, id: String) = withContext(Dispatchers.IO) {
        // Wait for WorkManager to mark the worker stopped before releasing the publication
        // lock. Merely scheduling cancellation lets a verified archive win the race.
        synchronized(installLock) { WorkManager.getInstance(context).cancelUniqueWork(name(id)).result.get() }
    }
    suspend fun remove(context: Context, pack: StickerPack) = withContext(Dispatchers.IO) {
        if (!pack.bundled) synchronized(installLock) { pack.directory(context).deleteRecursively() }
    }
}

class StickerInstallWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pack = StickerCatalog.load(applicationContext).firstOrNull { it.id == inputData.getString("pack") }
            ?: return@withContext Result.failure(workDataOf("error" to "Pack unavailable. Update Obadh and try again."))
        if (pack.available(applicationContext)) return@withContext Result.success()
        val root = File(applicationContext.filesDir,"sticker-packs").apply { mkdirs() }
        // Unique work owns one pack at a time. Reclaim staging files left by process death
        // or an interrupted retry before creating this attempt's private directory.
        root.listFiles()?.filter { it.name.startsWith(".stage-${pack.id}-") }?.forEach { it.deleteRecursively() }
        val stage = File(root,".stage-${pack.id}-$id").apply { mkdirs() }
        val archive = File(stage,"download.zip")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(StickerCatalog.downloadUrl(pack)).openConnection() as HttpURLConnection
            connection.connectTimeout=15000;connection.readTimeout=15000;connection.instanceFollowRedirects=false
            check(connection.responseCode == 200) { "The pack server is unavailable. Please retry." }
            require(connection.contentLengthLong == -1L || connection.contentLengthLong == pack.archiveBytes.toLong()) { "Pack size mismatch" }
            var count=0;var lastProgress=0
            connection.inputStream.use { input -> archive.outputStream().use { output ->
                val bytes=ByteArray(8192)
                while(true) {
                    ensureActive();if(isStopped) throw CancellationException();val n=input.read(bytes);if(n<0)break
                    count+=n;require(count<=pack.archiveBytes);output.write(bytes,0,n)
                    if(count-lastProgress>65536) { lastProgress=count;setProgress(workDataOf("bytes" to count,"total" to pack.archiveBytes,"phase" to "Downloading")) }
                }
            }}
            require(count==pack.archiveBytes && StickerSafety.digest(archive.readBytes())==pack.sha256) { "Pack verification failed. Please retry." }
            setProgress(workDataOf("phase" to "Verifying","bytes" to count,"total" to count))
            val content=File(stage,"content").apply { mkdirs() }
            archive.inputStream().use { StickerSafety.extract(it,content,pack.expectedFiles()) { isStopped || !isActive } }
            ensureActive()
            synchronized(StickerDownloads.installLock) {
                if(isStopped || !isActive) throw CancellationException()
                val target=pack.directory(applicationContext)
                if(!target.isDirectory) check(content.renameTo(target)) { "Not enough storage to install this pack." }
            }
            Result.success()
        } catch(e: CancellationException) { throw e }
        catch(e: IOException) {
            if(runAttemptCount<2) Result.retry() else Result.failure(workDataOf("error" to "Couldn't download the pack. Check your connection and storage, then retry."))
        } catch(e: Exception) { Result.failure(workDataOf("error" to (e.message?.take(120) ?: "Couldn't install the pack. Please retry."))) }
        finally { connection?.disconnect();stage.deleteRecursively() }
    }
}
