package org.unmukto.obadh.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The setting a search result pointed at; its row scrolls into view and flashes once. */
internal object SettingHighlight {
    var target by mutableStateOf<String?>(null)
}

/** Clears a target no row on the opened screen claimed, so it cannot flash on a later screen. */
@Composable
internal fun ExpireSettingHighlight() {
    val target = SettingHighlight.target
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        delay(4_000)
        if (SettingHighlight.target == target) SettingHighlight.target = null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.settingHighlight(key: String?): Modifier {
    val alpha = remember { Animatable(0f) }
    val requester = remember { BringIntoViewRequester() }
    val active = key != null && SettingHighlight.target == key
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        try {
            delay(350) // let the screen transition settle before scrolling
            requester.bringIntoView()
            repeat(2) {
                alpha.animateTo(.22f, tween(300))
                alpha.animateTo(0f, tween(500))
            }
            if (SettingHighlight.target == key) SettingHighlight.target = null
        } finally {
            withContext(NonCancellable) { alpha.snapTo(0f) }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    return bringIntoViewRequester(requester).drawWithContent {
        drawContent()
        if (alpha.value > 0f) drawRect(color.copy(alpha = alpha.value))
    }
}
