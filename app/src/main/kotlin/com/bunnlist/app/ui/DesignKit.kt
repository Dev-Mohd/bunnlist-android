package com.bunnlist.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Coffee
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
import androidx.compose.ui.graphics.luminance
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
fun Modifier.pressable(enabled: Boolean = true, pressedState: MutableState<Boolean>? = null, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    LaunchedEffect(pressed) { pressedState?.value = pressed }
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
fun LotPhotoWell(imageUrl: String?, notes: List<String>, modifier: Modifier = Modifier, padding: Dp = 8.dp, lifted: Boolean = false,
                 roaster: String = "", name: String = "", origin: String? = null, originCode: String? = null) {
    val family = HomeText.dominant(notes)
    val wash = if (family == FlavorFamily.Other) Brand.Gold else family.color()
    if (imageUrl == null) { CoffeeArtCard(roaster, name, origin, originCode, modifier); return }
    // Set while the card is held: the bag lifts off the card and its shadow spreads.
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
    Box(modifier.background(Color.White).background(wash.copy(alpha = 0.16f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = padding + 2.dp).size(70.dp, 10.dp).graphicsLayer { scaleX = 1f + 0.25f * lift; scaleY = 1f + 0.25f * lift }
            .blur((5 + 3 * lift).dp).background(Color.Black.copy(alpha = 0.16f - 0.06f * lift), CircleShape))
        var loaded by remember(imageUrl) { mutableStateOf(false) }
        val appear by animateFloatAsState(if (loaded) 1f else 0f, androidx.compose.animation.core.tween(350), label = "appear")
        if (!loaded) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = wash)
        AsyncImage(imageUrl, null, contentScale = ContentScale.Fit, onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize().padding(padding).graphicsLayer {
                alpha = appear; val sc = (0.96f + 0.04f * appear) * (1f + 0.06f * lift); scaleX = sc; scaleY = sc
                translationY = -6 * lift * density; rotationX = 4 * lift
            }.multiply())
    }
}

/** A coffee with no photo yet: a card in its region's colours with the roaster and name (iOS CoffeeArtCard). */
@Composable
fun CoffeeArtCard(roaster: String, name: String, origin: String?, originCode: String?, modifier: Modifier = Modifier) {
    val africa = setOf("ET", "KE", "RW", "BI", "UG", "TZ", "ZM", "MW", "CD", "CM"); val latin = setOf("CO", "BR", "PA", "SV", "GT", "HN", "CR", "PE", "MX", "NI", "EC", "BO", "JM", "HT", "DO", "CU")
    val arabia = setOf("YE", "SA"); val asia = setOf("ID", "IN", "NP", "CN", "VN", "TH", "PG", "MM", "LA", "TL", "PH")
    val code = originCode?.uppercase()
    val (top, bottom, ink) = when (code) {
        in africa -> Triple(Brand.Gold.copy(alpha = 0.55f), Brand.Brown.copy(alpha = 0.55f), Brand.Ink)
        in latin -> Triple(Brand.Olive.copy(alpha = 0.45f), Brand.Olive.copy(alpha = 0.8f), Color.White)
        in arabia -> Triple(Brand.Brown.copy(alpha = 0.75f), Brand.Ink.copy(alpha = 0.85f), Brand.Gold)
        in asia -> Triple(Brand.Olive.copy(alpha = 0.25f), Brand.Brown.copy(alpha = 0.5f), Brand.Ink)
        else -> Triple(Brand.Gold.copy(alpha = 0.2f), Brand.Olive.copy(alpha = 0.35f), Brand.Ink)
    }
    Column(modifier.background(Brush.verticalGradient(listOf(top, bottom))).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Icon(androidx.compose.material.icons.Icons.Filled.Coffee, null, tint = ink.copy(alpha = 0.7f))
        if (roaster.isNotEmpty()) Text(roaster, color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (name.isNotEmpty()) Text(name, color = ink.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
        origin?.let { Text(it, color = ink.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
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

/** iOS's segmented control: a soft track with the picked segment on a raised white thumb. */
@Composable
fun BrandSegmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    Row(modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)).padding(2.dp)) {
        options.forEachIndexed { i, title ->
            val on = i == selected
            Box(Modifier.weight(1f).fillMaxHeight()
                .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                    .background(if (dark) Color(0xFF5A4A40) else Color.White) else Modifier.clip(RoundedCornerShape(8.dp)))
                .pressable { onSelect(i) },
                contentAlignment = Alignment.Center) {
                Text(title, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            }
        }
    }
}

/** iOS's «ChoiceChip»: a capsule, ink when picked, a faint outline otherwise. */
@Composable
fun ChoiceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(Modifier.heightIn(min = 40.dp).clip(CircleShape)
        .background(if (selected) ink else ink.copy(alpha = 0.06f))
        .border(1.dp, if (selected) Color.Transparent else ink.copy(alpha = 0.22f), CircleShape)
        .pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.background else ink, maxLines = 1)
    }
}
