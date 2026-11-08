package com.bunnlist.app.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/** One-time tips («فقاعات الشرح»), the same set and wording as iOS. `after` makes a tip wait for another on the same screen. */
enum class AppTip(val title: String, val message: String, val icon: ImageVector, val after: AppTip? = null) {
    Advisor("محتار وش تشتري؟", "جاوب كم سؤال ونطلع لك قهوة على ذوقك.", Icons.Filled.AutoAwesome),
    Scan("صوّر الكيس", "صوّر كيس قهوتك ونطلّعها لك من الدليل، وتضيفها لمجموعتك بضغطة.", Icons.Filled.DocumentScanner, after = Advisor),
    Save("ودّي أجرّب", "احفظ القهوة وترجع لها من مجموعتي، ومعها المفضلة والقوائم.", Icons.Filled.Bookmark),
    Rate("قيّمها", "كل تقييم يعلّمنا ذوقك، ويخلّي «كم تناسبك» أدق.", Icons.Filled.Star, after = Save),
    Passport("جواز قهوتك", "كل قهوة تضيفها تختم دولتها في جوازك. كم دولة تقدر تجمع؟", Icons.Filled.Public),
    Brew("حضّر معنا", "اختر قهوتك والطريقة، والمؤقت يمشي معك صبّة صبّة.", Icons.Filled.Timer),
    Updates("صوتك يوصلنا", "هنا تحديثات BunnList، ومنها ترسل اقتراحاتك وأي مشكلة تواجهك.", Icons.Filled.Campaign),
    /** Shown inline once in the simple recipe editor, not as a floating bubble. */
    TimelineDrag("اسحب وعدّل", "اسحب أي نقطة تغيّر وقتها، واضغطها تعدّل كميتها.", Icons.Filled.Timer),
}

/** False under a pushed screen: tabs stay composed beneath it, and a tip there would float over the page on top. */
val LocalTipsEnabled = staticCompositionLocalOf { true }

/** Which tips were seen (kept on the phone), and the one on screen now, so only one shows at a time. */
object Tips {
    private val seen = mutableStateMapOf<AppTip, Boolean>()
    var showing by mutableStateOf<AppTip?>(null)
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences("tips", 0)
        AppTip.entries.forEach { seen[it] = p.getBoolean(it.name, false) }
        loaded = true
    }

    fun isSeen(t: AppTip) = seen[t] == true

    /** Shown and closed, or the person did what it suggests. */
    fun done(context: Context, t: AppTip) {
        seen[t] = true
        if (showing == t) showing = null
        context.getSharedPreferences("tips", 0).edit().putBoolean(t.name, true).apply()
    }

    /** «اعرض التلميحات من جديد». */
    fun reset(context: Context) {
        context.getSharedPreferences("tips", 0).edit().clear().apply()
        AppTip.entries.forEach { seen[it] = false }
        showing = null
    }

    fun eligible(t: AppTip) = !isSeen(t) && (t.after == null || isSeen(t.after)) && (showing == null || showing == t)
}

/**
 * Wraps the thing a tip explains. Once its turn comes, a bubble springs in above or below it with an arrow
 * pointing at it; tapping the bubble or ✕ closes it for good.
 */
@Composable
fun TipAnchor(tip: AppTip, above: Boolean = false, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Tips.load(context) }
    var anchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val still = LocalStill.current
    val enabled = LocalTipsEnabled.current
    var ready by remember { mutableStateOf(still) }
    val eligible = enabled && (Tips.eligible(tip) && !still || (still && !Tips.isSeen(tip)))
    LaunchedEffect(eligible) {
        if (!eligible) return@LaunchedEffect
        delay(700) // let the screen settle first
        if (Tips.eligible(tip)) { Tips.showing = tip; ready = true }
    }
    LaunchedEffect(enabled) {
        if (!enabled && Tips.showing == tip) { Tips.showing = null; ready = false }
    }
    DisposableEffect(Unit) { onDispose { if (Tips.showing == tip) Tips.showing = null } }
    Box(modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
        content()
        val a = anchor
        if (enabled && ready && a != null && (Tips.showing == tip || still) && !Tips.isSeen(tip)) {
            val density = LocalDensity.current
            val gap = with(density) { 8.dp.roundToPx() }
            val margin = with(density) { 16.dp.roundToPx() }
            var arrowX by remember { mutableFloatStateOf(0f) }
            Popup(popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                    val fitsBelow = anchorBounds.bottom + gap + popupContentSize.height < windowSize.height
                    val y = if (above || !fitsBelow) anchorBounds.top - gap - popupContentSize.height else anchorBounds.bottom + gap
                    return IntOffset(x, y)
                }
            }, properties = PopupProperties(focusable = false)) {
                TipBubble(tip, pointsDown = above, anchorCenterX = a.center.x) { Tips.done(context, tip) }
            }
        }
    }
}

@Composable
internal fun TipBubble(tip: AppTip, pointsDown: Boolean, anchorCenterX: Float, onClose: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown || LocalStill.current) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "tip")
    var left by remember { mutableFloatStateOf(0f) }
    val ink = Brand.Ink
    val bubble = MaterialTheme.colorScheme.surface
    val arrowW = with(LocalDensity.current) { 18.dp.toPx() }; val arrowH = with(LocalDensity.current) { 9.dp.toPx() }
    Column(Modifier.widthIn(max = 320.dp).onGloballyPositioned { left = it.boundsInWindow().left }
        .graphicsLayer { scaleX = 0.85f + 0.15f * s; scaleY = 0.85f + 0.15f * s; alpha = s }) {
        if (!pointsDown) Arrow(anchorCenterX - left, arrowW, arrowH, up = true, color = bubble)
        Row(Modifier.shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = ink.copy(alpha = 0.25f), spotColor = ink.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(18.dp)).background(bubble).pressable(onClick = onClose).padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Brand.Olive), contentAlignment = Alignment.Center) {
                Icon(tip.icon, null, Modifier.size(20.dp), tint = Color.White)
            }
            Column(Modifier.padding(horizontal = 10.dp).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tip.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(tip.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            Icon(Icons.Filled.Close, "إغلاق", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (pointsDown) Arrow(anchorCenterX - left, arrowW, arrowH, up = false, color = bubble)
    }
}

@Composable
private fun Arrow(centerX: Float, w: Float, h: Float, up: Boolean, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { h.toDp() })) {
        val cx = centerX.coerceIn(w, size.width - w)
        val p = Path().apply {
            if (up) { moveTo(cx - w / 2, size.height); lineTo(cx, 0f); lineTo(cx + w / 2, size.height) }
            else { moveTo(cx - w / 2, 0f); lineTo(cx, size.height); lineTo(cx + w / 2, 0f) }
            close()
        }
        drawPath(p, color)
    }
}
