package org.unmukto.obadh.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.personalization.PersonalizationHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.unmukto.obadh.keyboard.NativeObadhFeatures
import kotlin.coroutines.resume

/** Data is cleared by its owning process; acknowledge completion before showing success. */
object KeyboardDataCommands {
    suspend fun clear(context: Context, clipboard: Boolean): Boolean = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { continuation ->
            val reply = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (continuation.isActive) continuation.resume(resultCode == 1)
                }
            }
            context.sendBroadcast(Intent(context, KeyboardDataReceiver::class.java)
                .putExtra("clipboard", clipboard).putExtra("reply", reply))
        }
    } ?: false
}

class KeyboardDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val reply = intent.getParcelableExtra<ResultReceiver>("reply")
        if (intent.getBooleanExtra("clipboard", false)) {
            val success = runCatching {
                ClipboardDao.getInstance(context)?.clear() ?: error("Clipboard storage unavailable")
                ClipboardHistory(context).clear()
            }.isSuccess
            reply?.send(if (success) 1 else 0, null)
            return
        }
        val pending = goAsync()
        NativeObadhFeatures.initialize(context)
        NativeObadhFeatures.clearLearned { banglaCleared ->
            val completed: (Boolean) -> Unit = { nativeCleared ->
                reply?.send(if (banglaCleared && nativeCleared) 1 else 0, null)
                pending.finish()
            }
            runCatching {
                NativeObadhFeatures.activeIme?.clearObadhNativeHistory(completed)
                    ?: PersonalizationHelper.clearObadhHistory(context, completed)
            }.onFailure { completed(false) }
        }
    }
}
