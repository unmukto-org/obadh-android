package org.unmukto.obadh.settings

import android.content.Context
import org.unmukto.obadh.engine.ObadhBridgeClient
import java.io.File
import android.util.AtomicFile

/**
 * Persists the engine-exported personal snapshot. The engine validates the
 * vocabulary fingerprint on import, so a snapshot from a different artifact
 * generation is dropped, never merged: a missing or mismatched file is simply an
 * empty personal dictionary.
 */
class PersonalAutosuggestStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "personal-autosuggest.bin"))

    fun restore(engine: ObadhBridgeClient) {
        val data = runCatching { file.openRead().use { it.readBytes() } }.getOrNull() ?: return
        if (!engine.importPersonalAutosuggestSnapshot(data)) file.delete()
    }

    fun save(engine: ObadhBridgeClient) {
        val data = engine.exportPersonalAutosuggestSnapshot() ?: return
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(data)
            file.finishWrite(stream)
        } catch (_: Exception) { stream?.let(file::failWrite) }
    }

    fun clear() {
        file.delete()
        check(!file.baseFile.exists()) { "Couldn't delete personal suggestions" }
    }
}
