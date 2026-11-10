package com.bunnlist.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.Methods
import com.bunnlist.core.MilkDrink

// Drawn pictures of the real tools and drinks, in place of emoji that only hinted at them:
// the V60 on its server, an espresso cup with crema, the Chemex with its wooden collar, the
// French press, a steaming cup, an iced glass, and each milk drink as a glass whose layers
// are the drink's own proportions (espresso, milk, foam). Same drawings as iOS BrewIcons.

private val InkC = Color(0xFF171411)
private val CoffeeC = Color(0xFF4A3428)
private val CremaC = Color(0xFFC79A5C)
private val MilkC = Color(0xFFF5EDDE)
private val FoamC = Color(0xFFFFFDF8)
private val GlassC = Color(0xFFDBE6E6)
private val WoodC = Color(0xFF987024)
private val LatteC = Color(0xFFCCA378)

enum class BrewIconKind { V60, Espresso, Chemex, FrenchPress, Hot, Iced, Milk, Black }

private fun path(build: Path.() -> Unit) = Path().apply(build)
private fun poly(vararg p: Pair<Float, Float>) = path { moveTo(p[0].first, p[0].second); p.drop(1).forEach { lineTo(it.first, it.second) }; close() }
private val Line = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.ink(p: Path) = drawPath(p, InkC, style = Line)
private fun DrawScope.steam(x: Float, y: Float) = drawPath(
    path { moveTo(x, y); cubicTo(x - 4, y - 3, x + 4, y - 7, x, y - 10) }, InkC.copy(alpha = 0.45f), style = Stroke(1.8f, cap = StrokeCap.Round))
private fun DrawScope.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
    drawArc(InkC, start, sweep, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Line)

/** A brewer or a serving, drawn on a 64-point grid and scaled to `size`. */
@Composable
fun BrewIcon(kind: BrewIconKind, size: Dp = 56.dp) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 64f, pivot = Offset.Zero) {
            when (kind) {
                BrewIconKind.V60 -> {
                    drawPath(poly(17.5f to 50f, 46.5f to 50f, 48f to 58f, 16f to 58f), CoffeeC)
                    ink(poly(20f to 38f, 44f to 38f, 48f to 58f, 16f to 58f))
                    val cone = poly(12f to 10f, 52f to 10f, 36f to 32f, 28f to 32f)
                    drawPath(cone, FoamC); ink(cone)
                    drawPath(path { moveTo(22f, 12f); lineTo(30f, 30f); moveTo(32f, 12f); lineTo(32f, 30f); moveTo(42f, 12f); lineTo(34f, 30f) },
                        InkC.copy(alpha = 0.3f), style = Stroke(1.2f, cap = StrokeCap.Round))
                    ink(path { moveTo(22f, 34f); lineTo(42f, 34f) })
                    arc(52f, 17f, 5f, -80f, 160f)
                }
                BrewIconKind.Espresso, BrewIconKind.Black -> {
                    val cup = path { moveTo(16f, 28f); lineTo(44f, 28f); quadraticTo(44f, 44f, 37f, 48f); lineTo(23f, 48f); quadraticTo(16f, 44f, 16f, 28f) }
                    drawPath(cup, FoamC)
                    drawOval(if (kind == BrewIconKind.Black) CoffeeC else CremaC, Offset(17f, 25f), Size(26f, 6f))
                    ink(cup)
                    drawOval(InkC, Offset(17f, 25f), Size(26f, 6f), style = Line)
                    arc(45f, 35f, 5f, -70f, 160f)
                    ink(path { moveTo(10f, 53f); quadraticTo(30f, 58f, 50f, 53f) })
                    steam(25f, 20f); steam(34f, 20f)
                }
                BrewIconKind.Chemex -> {
                    val body = path { moveTo(18f, 6f); lineTo(46f, 6f); lineTo(35f, 28f); lineTo(49f, 54f); quadraticTo(32f, 62f, 15f, 54f); lineTo(29f, 28f); close() }
                    drawPath(body, FoamC)
                    drawPath(path { moveTo(21.5f, 42f); lineTo(42.5f, 42f); lineTo(49f, 54f); quadraticTo(32f, 62f, 15f, 54f); close() }, CoffeeC)
                    ink(body)
                    drawRoundRect(WoodC, Offset(25f, 23f), Size(14f, 9f), CornerRadius(2f))
                    drawRoundRect(InkC, Offset(25f, 23f), Size(14f, 9f), CornerRadius(2f), style = Line)
                    drawOval(WoodC, Offset(39f, 25.5f), Size(4f, 4f))
                }
                BrewIconKind.FrenchPress -> {
                    drawRoundRect(FoamC, Offset(16f, 18f), Size(26f, 38f), CornerRadius(3f))
                    drawRoundRect(CoffeeC, Offset(17f, 34f), Size(24f, 21f), CornerRadius(2f))
                    drawRoundRect(InkC, Offset(16f, 18f), Size(26f, 38f), CornerRadius(3f), style = Line)
                    ink(path { moveTo(29f, 8f); lineTo(29f, 33f) })
                    drawOval(InkC, Offset(25f, 4f), Size(8f, 6f))
                    ink(path { moveTo(14f, 18f); lineTo(44f, 18f) })
                    ink(path { moveTo(18f, 33.5f); lineTo(40f, 33.5f) })
                    ink(path { moveTo(42f, 24f); quadraticTo(56f, 36f, 42f, 48f) })
                }
                BrewIconKind.Hot -> {
                    val mug = poly(14f to 24f, 44f to 24f, 41f to 54f, 17f to 54f)
                    drawPath(mug, FoamC)
                    drawOval(CoffeeC, Offset(15f, 21f), Size(28f, 6f))
                    ink(mug)
                    drawOval(InkC, Offset(15f, 21f), Size(28f, 6f), style = Line)
                    arc(45f, 36f, 7f, -75f, 160f)
                    steam(22f, 16f); steam(30f, 14f); steam(38f, 16f)
                }
                BrewIconKind.Iced -> {
                    val glass = poly(16f to 10f, 48f to 10f, 43f to 58f, 21f to 58f)
                    drawPath(glass, GlassC.copy(alpha = 0.6f))
                    drawPath(poly(17.5f to 24f, 46.5f to 24f, 43f to 58f, 21f to 58f), CoffeeC.copy(alpha = 0.85f))
                    listOf(Rect(22f, 26f, 32f, 36f), Rect(33f, 31f, 42f, 40f), Rect(25f, 40f, 34f, 49f)).forEach { r ->
                        drawRoundRect(FoamC.copy(alpha = 0.75f), r.topLeft, r.size, CornerRadius(2f))
                        drawRoundRect(InkC.copy(alpha = 0.35f), r.topLeft, r.size, CornerRadius(2f), style = Stroke(1.2f))
                    }
                    ink(glass)
                    ink(path { moveTo(40f, 4f); lineTo(34f, 44f) })
                }
                BrewIconKind.Milk -> {
                    val jug = poly(18f to 16f, 42f to 16f, 48f to 11f, 44f to 22f, 46f to 56f, 16f to 56f)
                    drawPath(jug, GlassC)
                    drawPath(poly(17.3f to 30f, 44.6f to 30f, 46f to 56f, 16f to 56f), MilkC)
                    ink(jug)
                    ink(path { moveTo(17.3f, 30f); lineTo(44.6f, 30f) })
                    arc(15f, 36f, 8f, 100f, 160f)
                }
            }
        }
    }
}

/** A milk drink as a glass: espresso at the bottom, milk, then foam, in the drink's own proportions. */
@Composable
fun MilkDrinkGlass(drink: MilkDrink, size: Dp = 56.dp) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 64f, pivot = Offset.Zero) {
            val h = 26f + (drink.cupMl.coerceIn(90, 300) - 90) / 210f * 26f
            val top = 58f - h
            val bottom = 58f
            fun x(y: Float, left: Boolean): Float { val t = (y - top) / h; return if (left) 18f + 3f * t else 46f - 3f * t }
            fun band(y0: Float, y1: Float) = poly(x(y0, true) to y0, x(y0, false) to y0, x(y1, false) to y1, x(y1, true) to y1)
            val espresso = 36f
            val milk = drink.typicalMilkGrams.toFloat()
            val foam = milk * when (drink) { MilkDrink.Cappuccino -> 0.45f; MilkDrink.Macchiato -> 0.8f; else -> 0.18f }
            val total = espresso + milk + foam
            val inner = h - 3f
            val eH = inner * espresso / total
            val mH = inner * milk / total
            drawPath(band(bottom - eH, bottom), CoffeeC)
            drawPath(band(bottom - eH - mH, bottom - eH), LatteC)
            drawPath(band(top + 3f, bottom - eH - mH), FoamC)
            drawPath(band(top, bottom), InkC, style = Line)
            listOf(bottom - eH, bottom - eH - mH).forEach { y ->
                drawLine(InkC.copy(alpha = 0.25f), Offset(x(y, true), y), Offset(x(y, false), y), 1f)
            }
        }
    }
}

/** A milk drink explained: its glass, the amounts, the foam, and how to steam the milk. */
@Composable
fun MilkGuideCard(drink: MilkDrink, milkGrams: Double? = null, onDark: Boolean = false, showSteps: Boolean = true) {
    val primary = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (onDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (onDark) Color(0xFFE2B75C) else Tone.olive
    val tile = if (onDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (onDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F1E8)).padding(6.dp)) { MilkDrinkGlass(drink, 60.dp) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(drink.nameAr, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primary)
                Text(drink.summaryAr, fontSize = 13.sp, color = secondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("36غ" to "إسبريسو", "${Methods.grams(milkGrams ?: drink.typicalMilkGrams)}غ" to "حليب", "${drink.cupMl} مل" to "الكوب", "55–65°" to "حرارة الحليب").forEach { (v, k) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(tile).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primary)
                    Text(k, fontSize = 10.sp, color = secondary)
                }
            }
        }
        Text(drink.textureAr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
        if (showSteps) {
            Text("طريقة تبخير الحليب", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primary)
            drink.steamingAr.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (onDark) Color.Black else Color.White)
                    }
                    Text(step, fontSize = 13.sp, color = secondary, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
