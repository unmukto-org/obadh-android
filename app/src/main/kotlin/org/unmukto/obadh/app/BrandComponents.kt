package org.unmukto.obadh.app

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.unmukto.obadh.R

/** Honour the system "remove animations" switch, the Android equivalent of Reduce Motion. */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Two slow brand glows over a near-black (never pure black) base. Pure #000 reads as
 * "unstyled" on OLED; the charcoal from the icon reads as a decision. Everything is a
 * fraction of the container, so it lands the same on any screen size.
 */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val still = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "background")
    // Two incommensurate periods, so the pair never settles into a visible pulse.
    val a by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    )
    val b by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(13000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val driftA = if (still) 0f else a
    val driftB = if (still) 0f else b
    val secondary = if (dark) Brand.Deep else Brand.BlueTeal

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        if (dark) listOf(Brand.Charcoal, Brand.CharcoalDeep) else listOf(Brand.PaperWarm, Brand.Paper),
                    ),
                )
                val w = size.width
                val h = size.height
                fun glow(color: Color, alpha: Float, center: Offset, radius: Float) = drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha), color.copy(0f)), center, radius),
                    radius, center,
                )
                glow(
                    Brand.Teal, if (dark) 0.30f else 0.22f,
                    Offset(w * (0.56f + 0.05f * driftA), h * (0.20f + 0.025f * driftA)),
                    w * 1.35f / 2f * (1f + 0.06f * driftA),
                )
                glow(
                    secondary, if (dark) 0.34f else 0.18f,
                    Offset(w * (0.20f + 0.045f * driftB), h * (0.82f + 0.03f * driftB)),
                    w * 1.15f / 2f * (1f - 0.065f * driftB),
                )
            },
        content = content,
    )
}

/**
 * The app mark, lit so it separates from the background rather than dissolving into it, with
 * just enough life to feel physical: a slow float, a breathing pool of light behind it, and a
 * specular sweep that crosses once every few seconds. All of it off when animations are off.
 */
@Composable
fun BrandMark(size: Dp = 112.dp, modifier: Modifier = Modifier) {
    val still = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "mark")
    val float by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float",
    )
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    // A narrow band that sweeps once, then holds offscreen, so it reads as a glint, not a loop.
    val sweep by transition.animateFloat(
        -1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 6100
                -1f at 0
                1f at 1900 using FastOutSlowInEasing
                1f at 6100
            },
        ),
        label = "sweep",
    )
    val shape = RoundedCornerShape(size * 0.2237f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // The pool of light directly behind the mark. Without it the charcoal squircle sits on
        // charcoal and reads as a smudge.
        Box(
            Modifier
                .requiredSize(size * 2.7f)
                .graphicsLayer {
                    val p = if (still) 0.5f else pulse
                    scaleX = 0.94f + 0.12f * p; scaleY = scaleX
                    alpha = 0.85f + 0.15f * p
                }
                .drawBehind {
                    val r = this.size.minDimension / 2f
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Brand.Teal.copy(0.42f), Brand.Deep.copy(0.20f), Color.Transparent),
                            center, r * 0.75f,
                        ),
                        r, center,
                    )
                },
        )
        Image(
            painterResource(R.drawable.brand_icon), contentDescription = null,
            modifier = Modifier
                .size(size)
                .graphicsLayer { translationY = if (still) 0f else float * 4.dp.toPx() }
                .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Brand.Teal.copy(0.5f))
                .clip(shape)
                .border(1.dp, Color.White.copy(0.12f), shape)
                .drawWithSheen(if (still) 2f else sweep),
        )
    }
}

private fun Modifier.drawWithSheen(position: Float): Modifier = drawWithContent {
    drawContent()
    if (position in -1f..1f) {
        val w = size.width
        rotate(22f) {
            val band = w * 0.55f
            val x = size.width / 2f - band / 2f + position * w
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.White.copy(0f), Color.White.copy(0.22f), Color.White.copy(0f)),
                    startX = x, endX = x + band,
                ),
                Offset(x, -size.height * 0.6f), androidx.compose.ui.geometry.Size(band, size.height * 2.2f),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Staggered fade-and-rise. [index] orders the cascade; a step change re-triggers it. */
fun Modifier.reveal(index: Int, visible: Boolean): Modifier = composed {
    val still = rememberReduceMotion()
    val p by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(550, delayMillis = if (still) 0 else index * 80, easing = FastOutSlowInEasing),
        label = "reveal",
    )
    graphicsLayer {
        alpha = p
        translationY = if (still) 0f else (1f - p) * 14.dp.toPx()
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, tween(180), label = "press")
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(14.dp, CircleShape, ambientColor = Brand.Deep, spotColor = Brand.Deep.copy(0.55f))
            .clip(CircleShape)
            .background(Brand.action)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
    )
}
