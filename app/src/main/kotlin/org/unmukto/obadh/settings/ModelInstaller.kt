package org.unmukto.obadh.settings

import android.content.Context
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
    )

    fun modelsDir(context: Context): File = File(context.applicationContext.filesDir, "models")

    @Synchronized
    fun ensureInstalled(context: Context): File {
        val app = context.applicationContext
        val dir = modelsDir(app)
        val stamp = File(dir, ".installed")
        val version = app.packageManager.getPackageInfo(app.packageName, 0).let {
            "${it.versionName}:${it.longVersionCode}"
        }
        val complete = FILES.all { File(dir, it).exists() }
        if (complete && stamp.exists() && stamp.readText() == version) return dir

        FILES.forEach { rel ->
            val target = File(dir, rel)
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".tmp")
            app.assets.open("$ASSET_ROOT/$rel").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "Could not install $rel" }
        }
        stamp.writeText(version)
        return dir
    }
}
