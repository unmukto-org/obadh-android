package org.unmukto.obadh.settings

import android.content.Context
import org.unmukto.obadh.engine.ObadhBridgeClient
import java.io.File

/**
 * Persists the engine-exported personal snapshot. The engine validates the
 * vocabulary fingerprint on import, so a snapshot from a different artifact
 * generation is dropped, never merged: a missing or mismatched file is simply an
 * empty personal dictionary.
 */
class PersonalAutosuggestStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "personal-autosuggest.bin")

    fun restore(engine: ObadhBridgeClient) {
        val data = runCatching { file.readBytes() }.getOrNull() ?: return
        if (!engine.importPersonalAutosuggestSnapshot(data)) file.delete()
    }

    fun save(engine: ObadhBridgeClient) {
        val data = engine.exportPersonalAutosuggestSnapshot() ?: return
        runCatching { file.writeBytes(data) }
    }

    fun clear() {
        file.delete()
    }
}
