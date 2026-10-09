package org.unmukto.obadh.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources

/** Material You on Android 12+, with a complete Material palette on older devices. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ObadhTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFF8BD1CF), onPrimary = Color(0xFF003737),
            primaryContainer = Color(0xFF005050), onPrimaryContainer = Color(0xFFA7EEEB),
            secondary = Color(0xFFB0CCCB), onSecondary = Color(0xFF1B3534),
            secondaryContainer = Color(0xFF324B4A), onSecondaryContainer = Color(0xFFCCE8E6),
            tertiary = Color(0xFFB2C8E8), onTertiary = Color(0xFF1B324D),
            tertiaryContainer = Color(0xFF334965), onTertiaryContainer = Color(0xFFD3E4FF),
        )
        else -> lightColorScheme(
            primary = Color(0xFF006A69), onPrimary = Color.White,
            primaryContainer = Color(0xFFA7EEEB), onPrimaryContainer = Color(0xFF002020),
            secondary = Color(0xFF4A6362), onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCE8E6), onSecondaryContainer = Color(0xFF051F1E),
            tertiary = Color(0xFF4B607E), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C35),
        )
    }
    val nativeColors = if (Build.VERSION.SDK_INT >= 34) colors.copy(
        surface = Color(resources.getColor(if (dark) android.R.color.system_surface_container_lowest_dark else android.R.color.system_surface_container_light, context.theme)),
        surfaceContainerLow = Color(resources.getColor(if (dark) android.R.color.system_surface_container_low_dark else android.R.color.system_surface_container_low_light, context.theme)),
    ) else colors
    MaterialExpressiveTheme(colorScheme = nativeColors, typography = Typography()) {
        Surface(Modifier.fillMaxSize(), color = nativeColors.surface, content = content)
    }
}
