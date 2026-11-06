package com.bunnlist.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.bunnlist.core.BrewSceneKind
import com.bunnlist.core.BrewSceneState
import com.bunnlist.core.SceneStage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The brewer drawn live while you brew: water, bloom, drips, the plunger, the shot, the dalla on its
// flame. A line-for-line port of the iOS BrewSceneView, drawn in a 600×640 space and scaled to fit.

@Composable
fun BrewSceneCanvas(kind: BrewSceneKind, state: BrewSceneState, clock: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        fit {
            when (kind) {
                is BrewSceneKind.PourOver -> PourOver(state, clock, kind.iced).draw(this)
                BrewSceneKind.AeroPress -> AeroPress(state, clock).draw(this)
                BrewSceneKind.Arabic -> Dalla(state, clock).draw(this)
                else -> {}
            }
        }
    }
}

@Composable
fun EspressoSceneCanvas(elapsed: Double, running: Boolean, target: IntRange, clock: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) { fit { Espresso(elapsed, running, target, clock).draw(this) } }
}

private inline fun DrawScope.fit(block: DrawScope.() -> Unit) {
    val s = min(size.width / 600f, size.height / 640f)
    withTransform({ translate((size.width - 600f * s) / 2f, (size.height - 640f * s) / 2f); scale(s, s, Offset.Zero) }, block)
}

// MARK: Shared bits

private object Ink {
    val gold = Color(0.78f, 0.60f, 0.24f)
    val cream = Color(0.97f, 0.95f, 0.91f)
    val paper = Color(0.94f, 0.89f, 0.81f)
    val groundsDry = Color(0.48f, 0.32f, 0.21f)
    val groundsWet = Color(0.31f, 0.20f, 0.13f)
    val coffeeTop = Color(0.48f, 0.29f, 0.13f)
    val coffee = Color(0.16f, 0.09f, 0.05f)
    val water = Color(0.62f, 0.42f, 0.22f)
    val stream = Color(0.92f, 0.88f, 0.80f)
    val ice = Color(0.88f, 0.93f, 0.96f)
}

private fun clamp(v: Double, a: Double = 0.0, b: Double = 1.0) = min(b, max(a, v))
private fun lerp(a: Double, b: Double, p: Double) = a + (b - a) * p
private fun easeOut(p: Double) = 1 - (1 - p).pow(3)
private fun easeInOut(p: Double) = if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
private fun Double.f() = toFloat()
private fun frac(x: Double) = x - floor(x)

/** A fixed pseudo-random sequence, so particles sit still between frames. */
private fun noise(i: Int, salt: Int = 0): Double { val x = sin((i * 127 + salt * 311) * 12.9898) * 43758.5453; return x - floor(x) }

private fun pt(x: Double, y: Double) = Offset(x.f(), y.f())
private fun circle(c: Offset, r: Double) = Path().apply { addOval(Rect(c, r.f())) }
private fun oval(x: Double, y: Double, w: Double, h: Double) = Path().apply { addOval(Rect(x.f(), y.f(), (x + w).f(), (y + h).f())) }
private fun rect(x: Double, y: Double, w: Double, h: Double) = Path().apply { addRect(Rect(x.f(), y.f(), (x + w).f(), (y + h).f())) }
private fun rrect(x: Double, y: Double, w: Double, h: Double, r: Double) =
    Path().apply { addRoundRect(RoundRect(Rect(x.f(), y.f(), (x + w).f(), (y + h).f()), CornerRadius(r.f()))) }
private fun Path.m(x: Double, y: Double) = apply { moveTo(x.f(), y.f()) }
private fun Path.l(x: Double, y: Double) = apply { lineTo(x.f(), y.f()) }
private fun Path.q(x: Double, y: Double, cx: Double, cy: Double) = apply { quadraticTo(cx.f(), cy.f(), x.f(), y.f()) }
private fun Path.c(x: Double, y: Double, c1x: Double, c1y: Double, c2x: Double, c2y: Double) = apply { cubicTo(c1x.f(), c1y.f(), c2x.f(), c2y.f(), x.f(), y.f()) }

private fun DrawScope.fill(p: Path, color: Color) = drawPath(p, color)
private fun DrawScope.fill(p: Path, brush: Brush) = drawPath(p, brush)
private fun DrawScope.stroke(p: Path, color: Color, width: Double, cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter) =
    drawPath(p, color, style = Stroke(width.f(), cap = cap, join = join))
private fun vgrad(c1: Color, c2: Color, y1: Double, y2: Double) = Brush.verticalGradient(listOf(c1, c2), y1.f(), y2.f())

/** Draws [block] as one layer at [alpha], like SwiftUI's context opacity. */
private inline fun DrawScope.layer(alpha: Double, block: DrawScope.() -> Unit) {
    if (alpha >= 0.999) { block(); return }
    if (alpha <= 0.001) return
    drawIntoCanvas { it.saveLayer(Rect(-200f, -400f, 900f, 900f), Paint().apply { this.alpha = alpha.f() }) }
    block()
    drawIntoCanvas { it.restore() }
}

private fun DrawScope.steam(xs: List<Double>, baseY: Double, t: Double, alpha: Double) {
    xs.forEachIndexed { n, x ->
        val q = frac(t * 0.6 + n * 0.33)
        val y0 = baseY - q * 110
        val p = Path().m(x, y0).q(x, y0 - 60, x + 18, y0 - 30).q(x, y0 - 120, x - 18, y0 - 90)
        stroke(p, Ink.cream.copy(alpha = (0.5 * alpha * sin(q * PI)).coerceIn(0.0, 1.0).f()), 6.0, StrokeCap.Round)
    }
}

/** The gooseneck kettle, tilted by [tilt] (0…1); returns the spout tip. */
private fun DrawScope.kettle(tilt: Double, show: Double): Offset {
    val px = 560.0; val py = 60.0
    val angleDeg = -14 * tilt
    val angle = angleDeg * PI / 180
    val sx = (1 - show) * 260; val sy = (1 - show) * -120
    withTransform({ translate(sx.f(), sy.f()); rotate(angleDeg.f(), pt(px, py)) }) {
        val body = Path().m(520.0, -10.0).c(640.0, 110.0, 600.0, -10.0, 640.0, 40.0).l(640.0, 140.0).l(480.0, 140.0).l(480.0, 60.0)
            .c(520.0, -10.0, 480.0, 20.0, 495.0, -10.0)
        fill(body, Color(0.17f, 0.15f, 0.13f))
        stroke(body, Ink.cream.copy(alpha = 0.5f), 4.0)
        stroke(Path().m(486.0, 110.0).c(366.0, 50.0, 430.0, 110.0, 420.0, 60.0), Ink.cream.copy(alpha = 0.6f), 12.0, StrokeCap.Round)
    }
    val tx = 366 - px; val ty = 50 - py
    return pt(px + tx * cos(angle) - ty * sin(angle) + sx, py + tx * sin(angle) + ty * cos(angle) + sy)
}

private fun DrawScope.pourStream(tip: Offset, hit: Offset, width: Double, t: Double) {
    stroke(Path().apply { moveTo(tip.x, tip.y); quadraticTo(tip.x - 30, tip.y + 50, hit.x, hit.y) }, Ink.stream.copy(alpha = 0.85f), width, StrokeCap.Round)
    for (n in 0 until 3) {
        val q = frac(t * 2 + n / 3.0)
        val rx = 8 + q * 46; val ry = 3 + q * 10
        stroke(oval(hit.x - rx, hit.y + 3 - ry, rx * 2, ry * 2), Color(1f, 0.94f, 0.82f).copy(alpha = (0.6 * (1 - q)).f()), 3.0)
    }
}

// MARK: V60 (hot and iced)

private class PourOver(val state: BrewSceneState, val t: Double, val iced: Boolean) {
    private val cone get() = Path().m(168.0, 168.0).l(432.0, 168.0).l(312.0, 392.0).l(288.0, 392.0).apply { close() }
    private val server get() = Path().m(196.0, 438.0).c(222.0, 618.0, 186.0, 520.0, 190.0, 590.0).l(378.0, 618.0).c(404.0, 438.0, 410.0, 590.0, 414.0, 520.0).apply { close() }

    fun draw(d: DrawScope) = with(d) {
        val stage = state.stage
        val done = stage == SceneStage.Done
        val lift = if (done) easeInOut(clamp(state.progress)) else 0.0

        val fillLevel = state.drained * 0.86
        val surface = 618 - 172 * fillLevel
        clipPath(server) {
            fill(rect(180.0, 420.0, 240.0, 210.0), Color.White.copy(alpha = 0.06f))
            if (iced) drawIce(this, surface, front = false)
            fill(rect(180.0, surface, 240.0, 618 - surface), vgrad(Ink.coffeeTop, Ink.coffee, surface, 618.0))
            if (iced) drawIce(this, surface, front = true)
        }
        val rim = Path().m(196.0, 438.0).c(222.0, 618.0, 186.0, 520.0, 190.0, 590.0).l(378.0, 618.0).c(404.0, 438.0, 410.0, 590.0, 414.0, 520.0)
        stroke(rim, Ink.cream.copy(alpha = 0.55f), 5.0)
        stroke(Path().m(404.0, 470.0).c(400.0, 566.0, 450.0, 470.0, 456.0, 560.0), Ink.cream.copy(alpha = 0.4f), 10.0, StrokeCap.Round)
        fill(rrect(186.0, 426.0, 228.0, 14.0, 7.0), Ink.cream.copy(alpha = 0.5f))
        if (iced && (done || state.drained > 0.7)) drawBeads(this, if (done) 1.0 else (state.drained - 0.7) / 0.3)

        val dripping = !done && state.poured > 0.05 && state.standing > 0.01
        if (dripping) for (n in 0 until 3) {
            val q = frac(t / 0.45 + n / 3.0)
            val y = lerp(420.0, surface, q * q)
            fill(oval(295.0, y - 8, 10.0, 16.0), Ink.coffeeTop)
        }

        layer(1 - lift) {
            withTransform({
                translate(0f, (-lift * 420).f())
                if (stage == SceneStage.Swirl) rotate((sin(t * 7) * 4).f(), pt(300.0, 300.0))
            }) { drawDripper(this) }
        }

        val pouring = (stage == SceneStage.Bloom && state.progress < 0.4) || (stage == SceneStage.Pour && state.progress < 0.85)
        val tilt = when (stage) {
            SceneStage.Bloom -> sin(clamp(state.progress / 0.4) * PI)
            SceneStage.Pour -> min(1.0, sin(clamp(state.progress / 0.85) * PI) * 3)
            else -> 0.0
        }
        if (stage == SceneStage.Bloom || stage == SceneStage.Pour) {
            val tip = kettle(tilt, 1.0)
            if (pouring && tilt > 0.3) {
                val radius = if (stage == SceneStage.Pour) 20 + 26 * abs(sin(t * 1.3)) else 6.0
                pourStream(tip, pt(300 + radius * sin(t * 7), waterTop - 2), if (stage == SceneStage.Bloom) 5.0 else 8.0, t)
            }
        }
        if (done && !iced) steam(listOf(260.0, 300.0, 340.0), 420.0, t, clamp(state.progress / 0.4))
    }

    private val bedTop get() = if (state.poured > 0) 324.0 else 328.0
    private val dome get() = if (state.stage != SceneStage.Bloom) 0.0 else easeOut(clamp((state.progress - 0.15) / 0.5)) * 16
    private val waterTop get() = bedTop - dome - clamp(state.standing / 0.5) * (bedTop - 206)

    private fun drawDripper(d: DrawScope) = with(d) {
        clipPath(cone) {
            fill(rect(150.0, 160.0, 300.0, 240.0), Ink.paper)
            val bed = Path().m(140.0, bedTop + 8).q(460.0, bedTop + 8, 300.0, bedTop - dome * 2.2).l(460.0, 420.0).l(140.0, 420.0).apply { close() }
            fill(bed, if (state.poured > 0) Ink.groundsWet else Ink.groundsDry)
            for (i in 0 until 36) fill(circle(pt(180 + noise(i) * 240, bedTop + 10 + noise(i, 1) * (392 - bedTop)), 1.5 + noise(i, 2) * 2), Color.Black.copy(alpha = 0.3f))
            if (waterTop < bedTop - 1) fill(rect(140.0, waterTop, 320.0, bedTop + 12 - waterTop), vgrad(Ink.water.copy(alpha = 0.8f), Ink.coffeeTop.copy(alpha = 0.9f), waterTop, bedTop))
            if (state.stage == SceneStage.Bloom && state.progress > 0.2) for (i in 0 until 18) {
                val q = frac(t * 0.8 + noise(i, 3))
                stroke(circle(pt(236 + noise(i, 4) * 128, bedTop - dome * 1.6 + 4), (3 + noise(i, 5) * 6) * sin(q * PI)), Color(0.94f, 0.86f, 0.7f).copy(alpha = 0.7f), 2.0)
            }
        }
        val outline = Path().m(150.0, 160.0).l(450.0, 160.0).l(452.0, 172.0).l(322.0, 404.0).l(278.0, 404.0).l(148.0, 172.0).apply { close() }
        stroke(outline, Ink.gold, 7.0, join = StrokeJoin.Round)
        for ((a, b) in listOf(200.0 to 290.0, 400.0 to 310.0, 250.0 to 296.0, 350.0 to 304.0)) stroke(Path().m(a, 180.0).l(b, 380.0), Ink.gold.copy(alpha = 0.35f), 3.0)
        fill(rrect(236.0, 404.0, 128.0, 16.0, 8.0), Ink.gold)
    }

    private fun drawIce(d: DrawScope, surface: Double, front: Boolean) = with(d) {
        val melt = clamp(state.drained) * 0.22
        val cubes = listOf(214.0 to 12.0, 262.0 to -8.0, 312.0 to 15.0, 356.0 to -14.0, 236.0 to 4.0, 330.0 to -4.0)
        cubes.forEachIndexed { n, cube ->
            if ((n % 2 == 1) != front) return@forEachIndexed
            val size = 44 * (1 - melt)
            val floorY = 600 - (if (n > 3) 40 else 0) - size / 2
            val float = if (state.drained > 0.5) (state.drained - 0.5) * 70 else 0.0
            val bob = sin(t * 2 + n) * (if (state.drained > 0.3) 2 else 0)
            withTransform({ translate((cube.first + 22).f(), (floorY - float + bob).f()); rotate((cube.second + sin(t + n) * 3).f(), Offset.Zero) }) {
                val r = rrect(-size / 2, -size / 2, size, size, 9.0)
                fill(r, Ink.ice.copy(alpha = 0.8f)); stroke(r, Color.White, 2.0)
                fill(rrect(-size / 2 + 7, -size / 2 + 6, size * 0.3, size * 0.18, 4.0), Color.White.copy(alpha = 0.8f))
            }
        }
    }

    private fun drawBeads(d: DrawScope, alpha: Double) = with(d) {
        for (i in 0 until 26) {
            val slide = if (state.stage == SceneStage.Done) clamp((state.progress - noise(i, 6)) / 0.6) * 20 else 0.0
            val r = 2 + noise(i, 7) * 4
            fill(circle(pt(200 + noise(i, 8) * 200, 450 + noise(i, 9) * 160 + if (r > 4.5) slide else 0.0), r), Color.White.copy(alpha = (0.45 * clamp(alpha)).f()))
        }
    }
}

// MARK: AeroPress

private class AeroPress(val state: BrewSceneState, val t: Double) {
    fun draw(d: DrawScope) = with(d) {
        val stage = state.stage
        val pressing = stage == SceneStage.Serve || stage == SceneStage.Press
        val done = stage == SceneStage.Done
        val press = if (done) 1.0 else if (pressing) easeInOut(state.progress) else 0.0

        val cup = rrect(205.0, 470.0, 190.0, 150.0, 26.0)
        val cupFill = if (done) 0.8 else if (pressing) press * 0.8 else state.drained * 0.08
        clipPath(cup) { val top = 620 - 150 * cupFill; fill(rect(200.0, top, 200.0, 160.0), vgrad(Ink.coffeeTop, Ink.coffee, top, 620.0)) }
        stroke(cup, Ink.cream.copy(alpha = 0.55f), 5.0)
        stroke(Path().m(395.0, 500.0).c(395.0, 590.0, 450.0, 500.0, 450.0, 590.0), Ink.cream.copy(alpha = 0.4f), 10.0, StrokeCap.Round)

        val lift = if (done) easeInOut(clamp(state.progress / 0.5)) else 0.0
        val tubeL = 225.0; val tubeT = 180.0; val tubeW = 150.0; val tubeH = 270.0
        val bottom = tubeT + tubeH
        val waterLevel = clamp(state.poured) * (1 - press)
        val surface = bottom - 30 - waterLevel * 200
        layer(1 - lift) {
            withTransform({ translate(0f, (-lift * 420).f()); if (stage == SceneStage.Swirl) rotate((sin(t * 8) * 5).f(), pt(300.0, 460.0)) }) {
                val tube = rrect(tubeL, tubeT, tubeW, tubeH, 10.0)
                clipPath(tube) {
                    fill(rect(tubeL, tubeT, tubeW, tubeH), Color.White.copy(alpha = 0.06f))
                    val slurryTop = max(surface, bottom - 30 - 30 * (1 - press))
                    fill(rect(tubeL, bottom - 30, tubeW, 30.0), Ink.groundsWet)
                    if (waterLevel > 0.01) {
                        fill(rect(tubeL, surface, tubeW, bottom - 30 - surface), vgrad(Ink.water.copy(alpha = 0.85f), Ink.coffee.copy(alpha = 0.95f), surface, bottom))
                        for (i in 0 until 14) fill(circle(pt(tubeL + 10 + noise(i) * (tubeW - 20), slurryTop + 8 + noise(i, 1) * max(bottom - 40 - slurryTop, 1.0)), 2 + noise(i, 2) * 2), Ink.groundsDry.copy(alpha = 0.7f))
                    }
                    if (stage == SceneStage.Stir || (stage == SceneStage.Bloom && state.progress > 0.45)) {
                        val x = 300 + sin(t * 6) * 45
                        stroke(Path().m(x, 150.0).l(x + sin(t * 6) * 10, bottom - 50), Ink.cream.copy(alpha = 0.8f), 12.0, StrokeCap.Round)
                    }
                }
                stroke(tube, Ink.cream.copy(alpha = 0.6f), 5.0)
                for (n in 1..4) { val y = bottom - 30 - n * 50; stroke(Path().m(tubeL + 6, y).l(tubeL + 34, y), Ink.cream.copy(alpha = 0.35f), 3.0) }
                fill(rrect(205.0, bottom - 4, 190.0, 22.0, 8.0), Ink.gold)
                fill(rrect(238.0, bottom + 18, 124.0, 12.0, 4.0), Ink.gold.copy(alpha = 0.8f))

                val seated = stage in setOf(SceneStage.Wait, SceneStage.Swirl, SceneStage.Serve, SceneStage.Press, SceneStage.Done)
                if (seated) {
                    val top = 120 + press * 190
                    val plunger = rrect(236.0, top + 40, 128.0, 260.0, 8.0)
                    fill(plunger, Color(0.2f, 0.18f, 0.16f).copy(alpha = 0.9f)); stroke(plunger, Ink.cream.copy(alpha = 0.45f), 4.0)
                    fill(rrect(214.0, top + 20, 172.0, 26.0, 10.0), Ink.gold)
                    if (pressing) for (n in 0 until 2) {
                        val q = frac(t * 1.5 + n * 0.5); val y = top - 10 + q * 20
                        stroke(Path().m(280.0, y).l(300.0, y + 16).l(320.0, y), Ink.gold.copy(alpha = (1 - q).f()), 6.0, StrokeCap.Round, StrokeJoin.Round)
                    }
                }
            }
        }
        if (pressing) {
            stroke(Path().m(300.0, bottom + 30).l(300.0, 620 - 150 * cupFill), Ink.coffeeTop, 8 * (1 - state.progress * 0.6), StrokeCap.Round)
            if (state.progress > 0.85) for (n in 0 until 5) {
                val q = frac(t * 2 + n * 0.2)
                fill(circle(pt(300 + cos(n * 1.3) * (30 + q * 60), bottom + 30 - q * 30), 4 + q * 8), Ink.cream.copy(alpha = (0.4 * (1 - q)).f()))
            }
        }
        if (stage == SceneStage.Bloom || stage == SceneStage.Pour) {
            val span = if (stage == SceneStage.Bloom) 0.4 else 0.85
            val tilt = sin(clamp(state.progress / span) * PI)
            val tip = kettle(tilt, 1.0)
            if (state.progress < span && tilt > 0.3) pourStream(tip, pt(300 + sin(t * 5) * 20, surface), 7.0, t)
        }
        if (done) steam(listOf(270.0, 300.0, 330.0), 460.0, t, clamp(state.progress / 0.4))
    }
}

// MARK: Espresso

private class Espresso(val elapsed: Double, val running: Boolean, val target: IntRange, val t: Double) {
    fun draw(d: DrawScope) = with(d) {
        val goal = (target.first + target.last) / 2.0
        val p = clamp(elapsed / goal, 0.0, 1.25)
        val flowing = running && elapsed > 4

        val head = rrect(170.0, 40.0, 260.0, 70.0, 14.0)
        fill(head, Color(0.22f, 0.2f, 0.18f)); stroke(head, Ink.cream.copy(alpha = 0.4f), 4.0)
        fill(Path().m(190.0, 110.0).l(410.0, 110.0).c(190.0, 110.0, 400.0, 190.0, 200.0, 190.0), Ink.gold)
        stroke(Path().m(410.0, 130.0).l(560.0, 150.0), Color(0.24f, 0.16f, 0.1f), 28.0, StrokeCap.Round)
        for (x in listOf(276.0, 324.0)) fill(rrect(x - 10, 168.0, 20.0, 34.0, 6.0), Ink.gold.copy(alpha = 0.85f))

        val cup = Path().m(210.0, 420.0).l(390.0, 420.0).c(210.0, 420.0, 385.0, 600.0, 215.0, 600.0)
        val level = min(p, 1.0)
        val surface = 560 - 125 * level
        clipPath(cup) {
            fill(rect(200.0, surface, 200.0, 200.0), Ink.coffee)
            if (level > 0.05) {
                val crema = 6 + 14 * level
                fill(rect(200.0, surface, 200.0, crema), vgrad(Color(0.82f, 0.56f, 0.28f), Color(0.55f, 0.33f, 0.15f), surface, surface + crema))
                for (i in 0 until 10) fill(circle(pt(230 + noise(i) * 140, surface + 3 + noise(i, 1) * crema * 0.6), 2 + noise(i, 2) * 3), Color(0.95f, 0.78f, 0.5f).copy(alpha = 0.5f))
            }
        }
        stroke(cup, Ink.cream.copy(alpha = 0.6f), 5.0)
        stroke(Path().m(382.0, 450.0).c(360.0, 530.0, 440.0, 450.0, 430.0, 530.0), Ink.cream.copy(alpha = 0.4f), 10.0, StrokeCap.Round)
        fill(oval(160.0, 590.0, 280.0, 30.0), Ink.cream.copy(alpha = 0.15f))

        if (flowing) {
            val color = if (p < 0.25) Color(0.24f, 0.12f, 0.05f) else if (p < 0.9) Color(0.62f, 0.36f, 0.14f) else Color(0.86f, 0.66f, 0.38f)
            listOf(276.0, 324.0).forEachIndexed { n, x ->
                val wobble = sin(t * 9 + n) * 2
                stroke(Path().m(x, 202.0).q(300 + (x - 300) * 0.4 + wobble, surface, x + wobble, (202 + surface) / 2), color, if (p > 0.9) 4.0 else 6.0, StrokeCap.Round)
            }
        } else if (running) {
            for (x in listOf(276.0, 324.0)) { val r = 3 + clamp(elapsed / 4) * 5; fill(circle(pt(x, 206 + r / 2), r), Color(0.3f, 0.16f, 0.07f)) }
        }
        if (!running && elapsed > 0) steam(listOf(270.0, 300.0, 330.0), 400.0, t, 1.0)
    }
}

// MARK: Arabic coffee (the dalla)

private class Dalla(val state: BrewSceneState, val t: Double) {
    fun draw(d: DrawScope) = with(d) {
        val stage = state.stage
        val serving = stage == SceneStage.Serve
        val done = stage == SceneStage.Done
        val pourTilt = if (serving) easeInOut(clamp(state.progress / 0.3)) * (1 - easeInOut(clamp((state.progress - 0.85) / 0.15))) else 0.0
        val heat = when (stage) { SceneStage.Heat, SceneStage.AddCoffee -> 1.0; SceneStage.Simmer, SceneStage.AddCardamom -> 0.45; else -> 0.0 }

        fill(rrect(170.0, 560.0, 260.0, 18.0, 9.0), Color(0.25f, 0.22f, 0.2f))
        if (heat > 0 && !serving) flame(this, heat)
        if (serving || done) finjan(this, if (done) 1.0 else clamp((state.progress - 0.3) / 0.55))

        val pivot = pt(330.0, 420.0)
        layer(if (done) 0.9 else 1.0) { rotate((-38 * pourTilt).f(), pivot) { dalla(this) } }

        if (pourTilt > 0.6) {
            val angle = -38 * pourTilt * PI / 180
            val rx = 112 - 330.0; val ry = 214 - 420.0
            val sx = 330 + rx * cos(angle) - ry * sin(angle); val sy = 420 + rx * sin(angle) + ry * cos(angle)
            stroke(Path().m(sx, sy).q(150.0, 520.0, sx - 20, (sy + 520) / 2), Color(0.76f, 0.58f, 0.3f), 6.0, StrokeCap.Round)
        }
        if (stage == SceneStage.AddCoffee || stage == SceneStage.AddCardamom) {
            val cardamom = stage == SceneStage.AddCardamom
            for (i in 0 until if (cardamom) 9 else 26) {
                val q = clamp((state.progress - noise(i, 1) * 0.5) / 0.4)
                if (q <= 0 || q >= 1) continue
                val px = 300 + (noise(i) - 0.5) * 40; val py = lerp(90.0, 250.0, q * q)
                if (cardamom) withTransform({ translate(px.f(), py.f()); rotate((noise(i, 2) * 180 + q * 200).f(), Offset.Zero) }) {
                    fill(oval(-9.0, -5.0, 18.0, 10.0), Color(0.55f, 0.66f, 0.36f))
                } else fill(circle(pt(px, py), 2.5 + noise(i, 2) * 2), Ink.groundsDry)
            }
        }
        if (stage in setOf(SceneStage.Heat, SceneStage.AddCoffee, SceneStage.Simmer, SceneStage.AddCardamom)) {
            val boil = if (stage == SceneStage.Heat) clamp((state.progress - 0.5) / 0.5) else 1.0
            for (i in 0 until 10) {
                val q = frac(t * (0.9 + heat) + noise(i, 3))
                stroke(circle(pt(284 + noise(i, 4) * 32, 252 - q * 26), (2 + noise(i, 5) * 4) * sin(q * PI) * boil), Ink.cream.copy(alpha = 0.6f), 2.0)
            }
            if (stage == SceneStage.Simmer) {
                val rise = max(0.0, sin(state.progress * PI * 6)) * 14
                fill(oval(276.0, 246 - rise, 48.0, 14 + rise), Color(0.78f, 0.62f, 0.4f).copy(alpha = 0.85f))
            }
        }
        if (stage == SceneStage.Settle || done) steam(if (done) listOf(130.0, 160.0) else listOf(290.0, 320.0), if (done) 470.0 else 250.0, t, 0.8)
    }

    private fun flame(d: DrawScope, strength: Double) = with(d) {
        for (i in 0 until 7) {
            val x = 200 + i * 33.0
            val h = (26 + 34 * strength) * (0.75 + 0.25 * sin(t * 11 + i * 1.7))
            val f = Path().m(x - 10, 560.0).q(x, 560 - h, x - 12, 560 - h * 0.5).q(x + 10, 560.0, x + 12, 560 - h * 0.5)
            fill(f, Brush.verticalGradient(listOf(Color(1f, 0.78f, 0.3f), Color(0.95f, 0.42f, 0.12f), Color(0.3f, 0.45f, 0.95f)), (560 - h).f(), 560f))
        }
    }

    private fun dalla(d: DrawScope) = with(d) {
        val brass = Brush.linearGradient(listOf(Color(0.93f, 0.78f, 0.45f), Ink.gold, Color(0.5f, 0.36f, 0.14f)), pt(200.0, 300.0), pt(420.0, 520.0))
        val body = Path().m(270.0, 300.0).c(220.0, 540.0, 200.0, 360.0, 190.0, 500.0).l(380.0, 540.0).c(330.0, 300.0, 410.0, 500.0, 400.0, 360.0).apply { close() }
        fill(body, brass)
        fill(Path().m(270.0, 300.0).l(282.0, 250.0).l(318.0, 250.0).l(330.0, 300.0).apply { close() }, brass)
        val open = state.stage == SceneStage.AddCoffee || state.stage == SceneStage.AddCardamom
        layer(if (open) 0.9 else 1.0) {
            translate(if (open) 70f else 0f, if (open) -70f else 0f) {
                fill(Path().m(276.0, 252.0).l(300.0, 190.0).l(324.0, 252.0).apply { close() }, brass)
                fill(circle(pt(300.0, 182.0), 10.0), brass)
            }
        }
        fill(Path().m(236.0, 360.0).c(112.0, 214.0, 180.0, 330.0, 140.0, 260.0).l(128.0, 210.0).c(252.0, 330.0, 160.0, 262.0, 200.0, 300.0).apply { close() }, brass)
        stroke(Path().m(336.0, 300.0).c(392.0, 470.0, 430.0, 300.0, 440.0, 420.0), Ink.gold, 12.0, StrokeCap.Round)
        for (y in listOf(330.0, 500.0)) stroke(Path().m(220.0, y).l(380.0, y), Color(0.4f, 0.28f, 0.1f).copy(alpha = 0.5f), 4.0)
        stroke(body, Color(0.98f, 0.88f, 0.6f).copy(alpha = 0.5f), 2.0)
    }

    private fun finjan(d: DrawScope, fill: Double) = with(d) {
        val cup = Path().m(110.0, 520.0).l(190.0, 520.0).c(110.0, 520.0, 186.0, 590.0, 114.0, 590.0)
        clipPath(cup) {
            fill(rect(100.0, 520.0, 100.0, 80.0), Ink.cream)
            fill(rect(100.0, 572 - 40 * fill, 100.0, 80.0), Color(0.76f, 0.58f, 0.3f))
        }
        stroke(cup, Ink.gold, 4.0)
        fill(oval(420.0, 585.0, 120.0, 22.0), Ink.cream.copy(alpha = 0.25f))
        for (x in listOf(450.0, 490.0)) fill(oval(x, 566.0, 34.0, 22.0), Color(0.36f, 0.18f, 0.08f))
    }
}

@Suppress("unused") private val keepSize = Size.Zero
