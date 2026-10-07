package org.unmukto.obadh.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The icon's palette, reused so the app and the mark read as one thing.
 * Gradient #16506F to #3CBFBC on #1E2124 charcoal. Same tokens as obadh-ios Brand.swift.
 */
object Brand {
    val Deep = Color(0xFF16506F)
    val Teal = Color(0xFF3CBFBC)
    val TealLight = Color(0xFF7FE3DF)
    val Charcoal = Color(0xFF1E2124)
    val CharcoalDeep = Color(0xFF15181A)
    val Paper = Color(0xFFF3F5F6)
    val PaperWarm = Color(0xFFFFFFFF)
    val ActionEnd = Color(0xFF26839A)
    val BlueTeal = Color(0xFF2E9FBF)
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9F0A)

    /** Runs light-to-dark on paper and dark-to-light on charcoal, so the wordmark never fades into its background. */
    fun wordmark(dark: Boolean): Brush =
        Brush.linearGradient(if (dark) listOf(TealLight, Teal) else listOf(Deep, Teal))

    /** Stops short of the bright teal end: white label text needs the darker half to stay legible. */
    val action: Brush = Brush.horizontalGradient(listOf(Deep, ActionEnd))
}

@Composable
fun ObadhTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Brand.Teal, onPrimary = Brand.Charcoal,
            background = Brand.CharcoalDeep, onBackground = Color(0xFFECEFF1),
            surface = Color(0xFF25282C), onSurface = Color(0xFFECEFF1),
            surfaceVariant = Color(0xFF2F3338), onSurfaceVariant = Color(0xFF9AA3A9),
            outlineVariant = Color(0x1FFFFFFF),
        )
    } else {
        lightColorScheme(
            primary = Brand.Deep, onPrimary = Color.White,
            background = Brand.Paper, onBackground = Color(0xFF15181A),
            surface = Color.White, onSurface = Color(0xFF15181A),
            surfaceVariant = Color(0xFFE6EAEC), onSurfaceVariant = Color(0xFF5F6A70),
            outlineVariant = Color(0x14000000),
        )
    }
    // No Surface sits at the root, so nothing would otherwise set the default text colour and
    // every Text would render black on the dark background.
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
