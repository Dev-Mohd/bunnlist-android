package com.bunnlist.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.R
import kotlinx.coroutines.delay

private val parts = listOf(R.drawable.logo_part1, R.drawable.logo_part2, R.drawable.logo_part3, R.drawable.logo_part4, R.drawable.logo_part5)

/**
 * The BunnList logo drawn in piece by piece (the real logo split into its five shapes, as on iOS):
 * each shape wipes in from its leading edge, then the gold dot pops in. With [looping] it draws again, for waits.
 */
@Composable
fun BrandMark(height: Dp = 96.dp, animated: Boolean = true, looping: Boolean = false) {
    val still = LocalStill.current || !animated
    val progress = remember { List(5) { Animatable(if (still) 1f else 0f) } }
    LaunchedEffect(looping) {
        if (still) return@LaunchedEffect
        do {
            for (i in 0 until 4) { progress[i].animateTo(1f, tween(220, easing = FastOutSlowInEasing)); delay(0) }
            progress[4].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
            delay(350)
            if (!looping) break
            delay(900)
            progress.forEach { it.animateTo(0f, tween(250)) }
            delay(300)
        } while (true)
    }
    val ink = MaterialTheme.colorScheme.onBackground
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.size(height * (420f / 535f), height)) {
            parts.forEachIndexed { i, res ->
                val p = progress[i].value
                Image(painterResource(res), null,
                    colorFilter = if (i == 0 || i == 2) ColorFilter.tint(ink) else null,
                    modifier = Modifier.fillMaxSize().then(
                        if (i == 4) Modifier.graphicsLayer { scaleX = maxOf(p, 0.01f); scaleY = maxOf(p, 0.01f); alpha = p; transformOrigin = TransformOrigin(0.88f, 0.48f) }
                        else Modifier.drawWithContent { clipRect(right = size.width * p) { this@drawWithContent.drawContent() } }))
            }
        }
    }
}

@Composable
fun BrandWordmark(size: Int = 34) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text("BunnList", fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (size * 0.04).sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** The cream ground with a soft olive and gold glow, for the welcome and wait screens. */
fun Modifier.brandBackdrop(background: Color): Modifier = this.background(background).drawWithContent {
    drawRect(Brush.radialGradient(listOf(Brand.Olive.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width, 0f), radius = 420.dp.toPx()))
    drawRect(Brush.radialGradient(listOf(Brand.Gold.copy(alpha = 0.20f), Color.Transparent), center = Offset(0f, size.height), radius = 460.dp.toPx()))
    drawContent()
}

/** Full-screen wait: the mark drawing itself on the cream ground. */
@Composable
fun BrandLoadingView(message: String? = null) {
    Column(Modifier.fillMaxSize().brandBackdrop(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        BrandMark(110.dp, looping = true)
        BrandWordmark(26)
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** Google's four-colour «G», drawn (no bundled artwork needed). */
@Composable
fun GoogleMark(size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension; val line = s * 0.2f
        val rect = Rect(line / 2, line / 2, s - line / 2, s - line / 2)
        fun arc(from: Float, to: Float, c: Color) = drawArc(c, from, to - from, false, rect.topLeft, rect.size, style = Stroke(line))
        arc(-40f, 45f, Color(0xFF4285F4)); arc(45f, 135f, Color(0xFF34A853)); arc(135f, 200f, Color(0xFFFBBC05)); arc(200f, 320f, Color(0xFFEA4335))
        drawLine(Color(0xFF4285F4), rect.center, Offset(rect.right + line / 2, rect.center.y), line)
    }
}

/** A quiet placeholder block while a section loads, with a soft shimmer. */
@Composable
fun SkeletonBlock(modifier: Modifier, corner: Dp = 20.dp) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val hi = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.11f)
    Box(modifier.clip(RoundedCornerShape(corner)).background(base).drawWithContent {
        drawRect(Brush.linearGradient(listOf(Color.Transparent, hi, Color.Transparent), start = Offset(size.width * x - size.width / 2, 0f), end = Offset(size.width * x + size.width / 2, size.height)))
    })
}
