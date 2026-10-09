// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

import android.content.Context
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** One-time upgrade cleanup; the excluded offline feature leaves no installed artwork behind. */
object LegacyStickerCleanup {
    private val started=AtomicBoolean(false)
    fun start(context: Context,cancelDownloads: Boolean=true) {
        if(!started.compareAndSet(false,true))return
        val app=context.applicationContext
        Thread({
            val prefs=app.getSharedPreferences("media-upgrade",Context.MODE_PRIVATE)
            val ids=listOf("blobfox-more","vlpn","openmoji-smileys-emotion","openmoji-animals-nature","openmoji-food-drink",
                "openmoji-people-body","openmoji-travel-places","openmoji-activities","openmoji-objects","openmoji-symbols")
            // Runs from the main app process, where Android initializes WorkManager.
            if(cancelDownloads && !prefs.getBoolean("offline-work-cancelled",false))runCatching {
                val wm=WorkManager.getInstance(app)
                ids.forEach { wm.cancelUniqueWork("sticker-pack-$it").result.get() }
                prefs.edit().putBoolean("offline-work-cancelled",true).apply()
            }
            if(prefs.getBoolean("offline-removed",false))return@Thread
            val removed=listOf("sticker-packs","sticker-share").all { name ->
                val directory=File(app.filesDir,name);!directory.exists() || directory.deleteRecursively()
            }
            app.deleteSharedPreferences("sticker-history")
            if(removed)prefs.edit().putBoolean("offline-removed",true).apply()
            else started.set(false)
        },"Obadh media upgrade cleanup").apply { priority=Thread.MIN_PRIORITY }.start()
    }
}
