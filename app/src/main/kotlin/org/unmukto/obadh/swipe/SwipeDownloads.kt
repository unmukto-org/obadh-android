package org.unmukto.obadh.swipe

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import androidx.work.*
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

enum class SwipePhase { NotInstalled, Waiting, Downloading, Paused, Verifying, Ready, Failed }
data class SwipeStatus(val phase: SwipePhase, val downloaded: Long = 0, val total: Long = -1, val message: String = "") {
    val busy get() = phase in setOf(SwipePhase.Waiting, SwipePhase.Downloading, SwipePhase.Paused, SwipePhase.Verifying)
}

/** Explicit user-requested download; no network access on the keyboard input path. */
class SwipeDownloads(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("swipe_download", Context.MODE_PRIVATE)
    private val downloads = app.getSystemService(DownloadManager::class.java)
    val enabled get() = prefs.getBoolean("enabled", false) && helium314.keyboard.latin.obadh.ObadhSwipeCompatibility.supportsDownloadedLibrary()
    fun setEnabled(value: Boolean) {
        synchronized(installLock) {
            prefs.edit().putBoolean("enabled", value).commit()
            app.sendBroadcast(Intent(app, SwipePreferenceReceiver::class.java).putExtra("enabled", value))
        }
    }

    fun status(): SwipeStatus {
        if (Binaries.forDevice() == null) return SwipeStatus(SwipePhase.Failed, message = "Swipe typing isn't supported on this device yet.")
        if (prefs.getBoolean("installed", false) && installedFile(app).isFile) return SwipeStatus(SwipePhase.Ready)
        val id = prefs.getLong("id", -1)
        if (id < 0) return prefs.getString("error", null)?.let { SwipeStatus(SwipePhase.Failed, message = it) }
            ?: SwipeStatus(SwipePhase.NotInstalled)
        return try {
            downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return SwipeStatus(SwipePhase.Failed, message = "Download was removed. Try again.")
                val code = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                when (code) {
                    DownloadManager.STATUS_PENDING -> SwipeStatus(SwipePhase.Waiting, bytes, total, "Waiting to download")
                    DownloadManager.STATUS_RUNNING -> SwipeStatus(SwipePhase.Downloading, bytes, total)
                    DownloadManager.STATUS_PAUSED -> SwipeStatus(SwipePhase.Paused, bytes, total, when (reason) {
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a connection"
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
                        else -> "Android will retry the download"
                    })
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        enqueueVerification(app, id)
                        SwipeStatus(SwipePhase.Verifying, bytes, total, "Checking download")
                    }
                    else -> SwipeStatus(SwipePhase.Failed, message = when (reason) {
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage. Free some space and retry."
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable."
                        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The download server could not complete the request. Try again."
                        else -> "Download failed. Try again."
                    })
                }
            }
        } catch (_: Exception) { SwipeStatus(SwipePhase.Failed, message = "Android downloads are unavailable. Try again.") }
    }

    fun start(): Unit = synchronized(installLock) {
        if (status().busy) return
        cancel()
        val binary = Binaries.forDevice() ?: run { fail("Swipe typing isn't available for this device yet."); return }
        try {
            val directory = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: error("Download storage unavailable")
            val destination = File(directory, "swipe-library.download")
            check(!destination.exists() || destination.delete())
            val request = DownloadManager.Request(Uri.parse(binary.url))
                .setTitle("Obadh swipe typing")
                .setDescription("Downloading English swipe typing")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationUri(Uri.fromFile(destination))
            val id = downloads.enqueue(request)
            prefs.edit().putLong("id", id).putString("abi", binary.abi).remove("error").commit()
        } catch (_: Exception) { fail("Couldn't start the download. Check storage and try again.") }
    }

    fun cancel(): Unit = synchronized(installLock) {
        // Clear ownership first: a late completion cannot activate a cancelled download.
        val id = prefs.getLong("id", -1)
        prefs.edit().remove("id").remove("error").commit()
        if (id >= 0) {
            WorkManager.getInstance(app).cancelUniqueWork("swipe-verify-$id")
            runCatching { downloads.remove(id) }
        }
    }

    private fun fail(message: String) { prefs.edit().putString("error", message).remove("id").commit() }

    companion object {
        // UI and WorkManager live in the main process. Only the final install transaction
        // takes this lock; download/copy/hash work never blocks cancellation.
        internal val installLock = Any()
        fun installedFile(context: Context) = File(context.filesDir, "libjni_latinime.so")
        fun enqueueVerification(context: Context, id: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork("swipe-verify-$id", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SwipeInstallWorker>().setInputData(workDataOf("id" to id)).build())
        }
    }
}

data class SwipeBinary(val abi: String, val sha256: String) {
    val url get() = "https://raw.githubusercontent.com/erkserkserks/openboard/46fdf2b550035ca69299ce312fa158e7ade36967/app/src/main/jniLibs/$abi/libjni_latinimegoogle.so"
}
object Binaries {
    private val hashes = mapOf(
        "arm64-v8a" to "b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f",
        "armeabi-v7a" to "442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6",
        "x86_64" to "c882e12e6d48dd946e0b644c66868a720bd11ac3fecf152000e21a3d5abd59c9",
        "x86" to "bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8",
    )
    fun forAbi(abi: String) = hashes[abi]?.let { SwipeBinary(abi, it) }
    fun forDevice() = if (helium314.keyboard.latin.obadh.ObadhSwipeCompatibility.supportsDownloadedLibrary())
        Build.SUPPORTED_ABIS.firstNotNullOfOrNull(::forAbi) else null
}

class SwipeInstallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val prefs = app.getSharedPreferences("swipe_download", Context.MODE_PRIVATE)
        val id = inputData.getLong("id", -1)
        if (id < 0 || prefs.getLong("id", -1) != id) return@withContext Result.success()
        val downloads = app.getSystemService(DownloadManager::class.java)
        val binary = Binaries.forAbi(prefs.getString("abi", "") ?: "") ?: return@withContext Result.failure()
        val temporary = File(app.filesDir, "swipe-$id.tmp")
        try {
            downloads.openDownloadedFile(id).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { source ->
                    SwipeLibraryVerifier.copyVerified(source, temporary, binary.sha256) { ensureActive() }
                }
            }
            synchronized(SwipeDownloads.installLock) {
                if (prefs.getLong("id", -1) != id || isStopped) return@withContext Result.success()
                val installed = SwipeDownloads.installedFile(app)
                check(temporary.renameTo(installed)) { "Couldn't install swipe typing. Check available storage and retry." }
                check(app.protectedPrefs().edit().putString(Settings.PREF_LIBRARY_CHECKSUM, binary.sha256).commit())
                check(prefs.edit().putBoolean("installed", true).putBoolean("enabled", true).remove("id").remove("error").commit())
                // Restart only the isolated IME process. Android rebinds it; settings stay open.
                app.sendBroadcast(Intent(app, KeyboardReloadReceiver::class.java))
            }
            runCatching { downloads.remove(id) } // Keep only the installed binary.
            Result.success()
        } catch (cancelled: CancellationException) {
            // Android can stop a worker. WorkManager will resume it; retain download ownership.
            throw cancelled
        } catch (error: Exception) {
            synchronized(SwipeDownloads.installLock) {
                if (prefs.getLong("id", -1) == id) {
                    val message = if (error is IllegalStateException) error.message else null
                    prefs.edit().remove("id").putString("error", message ?: "Couldn't install swipe typing. Check storage and retry.").commit()
                    runCatching { downloads.remove(id) }
                }
            }
            Result.failure()
        } finally { temporary.delete() }
    }
}

class SwipeDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val expected = context.getSharedPreferences("swipe_download", Context.MODE_PRIVATE).getLong("id", -1)
        if (id < 0 || id != expected) return
        // An exported system-completion broadcast is only a hint. Ask Android for the
        // actual state; a spoofed or failed completion must never install a partial file.
        runCatching {
            context.getSystemService(DownloadManager::class.java)
                .query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL)
                        SwipeDownloads.enqueueVerification(context, id)
                }
        }
    }
}
class KeyboardReloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // This private receiver is declared exclusively in :keyboard (also works on API 26).
        Process.killProcess(Process.myPid())
    }
}

/** Apply the change inside the IME process: SharedPreferences caches aren't cross-process. */
class SwipePreferenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.prefs().edit().putBoolean(Settings.PREF_GESTURE_INPUT, intent.getBooleanExtra("enabled", false)
            && helium314.keyboard.latin.obadh.ObadhSwipeCompatibility.supportsDownloadedLibrary()).apply()
    }
}
