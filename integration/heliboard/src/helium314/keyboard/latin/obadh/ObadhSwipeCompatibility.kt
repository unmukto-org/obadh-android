// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import android.system.Os
import android.system.OsConstants

/** The pinned optional decoder has 4 KB RELRO boundaries despite 64 KB LOAD alignment. */
object ObadhSwipeCompatibility {
    private val compatible by lazy {
        runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) in 1L..4096L }.getOrDefault(false)
    }

    @JvmStatic fun supportsDownloadedLibrary(): Boolean = compatible
}
