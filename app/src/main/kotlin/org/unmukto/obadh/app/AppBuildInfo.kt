package org.unmukto.obadh.app

import org.unmukto.obadh.BuildConfig
import org.unmukto.obadh.engine.EngineInfo

/**
 * App identity from the build; engine identity from its own C ABI, so comparing the two
 * confirms what is actually installed. The stamp lives in About: it matters, but it is
 * something you go looking for, not something you read every day.
 */
object AppBuildInfo {
    val version: String = BuildConfig.VERSION_NAME
    val build: String = BuildConfig.VERSION_CODE.toString()
    val gitRevision: String = BuildConfig.GIT_REVISION
    val buildTime: String = BuildConfig.BUILD_TIME

    /** The linked engine's own semver, read without creating an engine or loading any model. */
    val engineVersion: String by lazy {
        runCatching { EngineInfo.version().ifBlank { null } }.getOrNull() ?: "Unavailable"
    }

    val summary: String
        get() = listOfNotNull(
            "$version ($build)", "Engine $engineVersion",
            gitRevision.takeIf { it.isNotEmpty() }, buildTime.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
}
