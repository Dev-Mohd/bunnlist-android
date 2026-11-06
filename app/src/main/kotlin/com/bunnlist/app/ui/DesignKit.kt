package com.bunnlist.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.HomeText

/** Space the floating tab bar takes at the bottom, for lists that scroll under it. */
val LocalBottomInset = compositionLocalOf { 0.dp }

fun FlavorFamily.color(): Color = Color(0xFF000000 or rgb.toLong())

/** iOS CardPressStyle: the card shrinks a little on a springy press, with a light tap. No ripple. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "press")
    LaunchedEffect(pressed) { if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(source, indication = null, enabled = enabled, onClick = onClick)
}

/** Draws this content multiplied onto what's behind it, so a bag shot on white sits on any colour. */
fun Modifier.multiply(): Modifier = drawWithContent {
    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { blendMode = BlendMode.Multiply }) }
    drawContent()
    drawIntoCanvas { it.restore() }
}

/** A coffee's photo on a soft wash of its main flavour, with a shadow under the bag (iOS LotPhotoWell). */
@Composable
fun LotPhotoWell(imageUrl: String?, notes: List<String>, modifier: Modifier = Modifier, padding: Dp = 8.dp) {
    val family = HomeText.dominant(notes)
    val wash = if (family == FlavorFamily.Other) Brand.Gold else family.color()
    Box(modifier.background(Color.White).background(wash.copy(alpha = 0.16f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = padding + 2.dp).size(70.dp, 10.dp).blur(5.dp).background(Color.Black.copy(alpha = 0.16f), CircleShape))
        AsyncImage(imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(padding).multiply())
    }
}

@Composable
fun HomeSectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            subtitle?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (action != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 44.dp)) {
            Text(action, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The iOS-style floating tab bar: a frosted capsule above the content, the active tab on a soft pill. */
@Composable
fun FloatingTabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Row(modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp).navigationBarsPadding().fillMaxWidth().height(70.dp)
        .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
        .clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
        .padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f) else Color.Transparent, label = "tabBg")
            val tint by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, label = "tabTint")
            val sc by animateFloatAsState(if (on) 1.06f else 1f, spring(dampingRatio = 0.5f), label = "tabScale")
            Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(bg)
                .clickable(remember { MutableInteractionSource() }, null) { if (!on) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(25.dp).scale(sc))
                Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
            }
        }
    }
}

/** True in previews and snapshot tests: show animated things already settled. */
val LocalStill = staticCompositionLocalOf { false }
