package org.unmukto.obadh.settings

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/**
 * The engine opens artifacts by filesystem path, and APK assets are not files,
 * so the bundled ObadhModels are copied into app storage once per app version.
 * Runs off the main thread; the keyboard degrades to deterministic-only until done.
 */
object ModelInstaller {
    private const val ASSET_ROOT = "ObadhModels"
    private val FILES = listOf(
        "autocorrect/bn.fst",
        "autocorrect/en_bn_loanwords.fst",
        "autosuggest/autosuggest-ngram-c64.bin",
        "emoji/emoji.bin",
        "emoji/emoji-bn.bin",
        "emoji/emoji-bn-search.bin",
    )

    fun modelsDir(context: Context): File = File(context.applicationContext.filesDir, "models")

    /** How long the last copy took, or null if nothing needed copying. For the About/debug tooling. */
    @Volatile var lastInstallMillis: Long? = null
        private set

    @Synchronized
    fun ensureInstalled(context: Context): File {
        lastInstallMillis = null
        val app = context.applicationContext
        val dir = modelsDir(app)
        val stamp = File(dir, ".installed")
        val version = app.packageManager.getPackageInfo(app.packageName, 0).let {
            "${it.versionName}:${PackageInfoCompat.getLongVersionCode(it)}"
        }
        val complete = FILES.all { File(dir, it).exists() }
        if (complete && stamp.exists() && stamp.readText() == version) return dir

        val started = android.os.SystemClock.elapsedRealtime()
        FILES.forEach { rel ->
            val target = File(dir, rel)
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".tmp")
            app.assets.open("$ASSET_ROOT/$rel").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "Could not install $rel" }
        }
        stamp.writeText(version)
        lastInstallMillis = android.os.SystemClock.elapsedRealtime() - started
        android.util.Log.i("ObadhModels", "installed ${FILES.size} artifacts in ${lastInstallMillis} ms")
        return dir
    }
}
