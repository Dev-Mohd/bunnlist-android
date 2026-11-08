package com.bunnlist.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewScene
import com.bunnlist.core.BrewSceneKind
import com.bunnlist.core.BrewTimeline
import com.bunnlist.core.Methods
import java.util.Locale

private val Gold = Color(0xFFC79A3D)
private val White70 = Color.White.copy(alpha = 0.7f)

/** Seconds since this composable appeared, ticking every frame (for the scene's small motions). */
@Composable
private fun rememberFrameClock(): State<Double> {
    val clock = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.doubleValue = (it - start) / 1e9 }
    }
    return clock
}

/** The brewing itself, shaped by the method (as on iOS): the scene timer, a shot timer for espresso, a checklist for cold brew. */
@Composable
fun BrewSessionScreen(preset: BrewPreset, lotName: String?, onClose: () -> Unit, onRate: ((Int) -> Unit)? = null) {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    when {
        preset.methodCode == "ESPRESSO" -> EspressoShot(preset, lotName, onClose, onRate)
        !preset.usesTimer || preset.steps.isEmpty() -> Checklist(preset, lotName, onClose, onRate)
        else -> TimerBrew(preset, lotName, onClose, onRate)
    }
}

@Composable
private fun DarkTop(onClose: () -> Unit, finish: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = Color.White) }
        Spacer(Modifier.weight(1f))
        if (finish != null) TextButton(onClick = finish, modifier = Modifier.heightIn(min = 48.dp)) { Text("خلصت", color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}

private fun clock(s: Int) = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)

@Composable
private fun TimerBrew(preset: BrewPreset, lotName: String?, onClose: () -> Unit, onRate: ((Int) -> Unit)?) {
    val timeline = remember(preset) { BrewTimeline(preset.steps) }
    val kind = remember(preset) { BrewSceneKind.of(preset) }
    val totalWater = remember(preset) { preset.steps.sumOf { if (it.kind.addsWater) it.addedWaterGrams ?: 0.0 else 0.0 } }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var pausedElapsed by remember { mutableDoubleStateOf(0.0) }
    var lastStep by remember { mutableIntStateOf(-1) }
    var cuedStep by remember { mutableIntStateOf(-1) }
    val frame by rememberFrameClock()
    val view = LocalView.current

    val elapsed = startedAt?.let { pausedElapsed + (System.nanoTime() - it) / 1e9 + frame * 0 } ?: pausedElapsed
    val running = startedAt != null
    val state = BrewScene.state(preset, elapsed, running || elapsed > 0)
    val pos = timeline.position(elapsed.toInt())

    fun pause() { pausedElapsed = elapsed; startedAt = null }
    fun reset() { startedAt = null; pausedElapsed = 0.0; lastStep = -1; cuedStep = -1 }
    fun start() { if (pos.isFinished) reset(); startedAt = System.nanoTime(); if (lastStep < 0) lastStep = 0 }
    fun skip() { val target = timeline.start(pos.stepIndex + 1).toDouble(); if (running) startedAt = System.nanoTime(); pausedElapsed = target }

    // Haptics like iOS: a light tap three seconds before a step, a firmer one on the change, success at the end.
    LaunchedEffect(pos.stepIndex, pos.isFinished, state.isCueing, running) {
        if (!running) return@LaunchedEffect
        if (state.isCueing && cuedStep != pos.stepIndex) { cuedStep = pos.stepIndex; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        if (pos.stepIndex != lastStep || pos.isFinished) {
            if (lastStep >= 0) view.performHapticFeedback(if (pos.isFinished) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
            lastStep = if (pos.isFinished) preset.steps.size else pos.stepIndex
        }
        if (pos.isFinished) pause()
    }

    // Black, warming (or cooling, iced) as the brew goes on.
    val warmth by animateFloatAsState((0.12 + minOf(elapsed / maxOf(timeline.totalSeconds, 1), 1.0) * 0.3).toFloat(), tween(1000), label = "warmth")
    val iced = kind == BrewSceneKind.PourOver(true)
    val tint = if (iced) Color(0.35f, 0.55f, 0.7f) else Color(0.62f, 0.4f, 0.16f)

    Box(Modifier.fillMaxSize().background(Color.Black).background(Brush.radialGradient(listOf(tint.copy(alpha = warmth), Color.Transparent), radius = 1100f))) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DarkTop(onClose, if (elapsed > 0) ({ if (onRate != null) onRate(elapsed.toInt()) else onClose() }) else null)
            Text(preset.nameAr, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(lotName, Methods.nameAr(preset.methodCode), "${Methods.grams(preset.doseGrams)}غ بن", "${Methods.grams(preset.waterTempC)}°م").joinToString(" · "),
                color = White70, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (kind == BrewSceneKind.Ring) Dial(elapsed, timeline.totalSeconds)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrewSceneCanvas(kind, state, frame, Modifier.fillMaxWidth().height(270.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(clock(elapsed.toInt()), color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        if (totalWater > 0) Text("${Methods.grams(Math.round(state.poured * totalWater).toDouble())} / ${Methods.grams(totalWater)}غ",
                            color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(if (pos.isFinished && elapsed > 0) -1 else pos.stepIndex, label = "step",
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 4 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 4 }) },
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) { idx ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (idx == -1) {
                        Icon(Icons.Filled.Coffee, null, tint = Gold, modifier = Modifier.size(44.dp))
                        Text("قهوتك جاهزة، بالعافية", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("تذوّقها وهي دافئة ثم وهي تبرد؛ النكهات تتغير.", color = White70, textAlign = TextAlign.Center)
                        if (onRate != null) Button(onClick = { onRate(elapsed.toInt()) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                            modifier = Modifier.padding(top = 8.dp).heightIn(min = 52.dp)) { Text("كيف طلعت؟", fontWeight = FontWeight.Bold) }
                    } else {
                        val step = preset.steps[idx]
                        Text(state.titleAr ?: step.kind.nameAr, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        pos.pourTo?.takeIf { pos.stepIndex == idx }?.let { Text("صب حتى ${Methods.grams(it)}غ", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                        // Before starting: the water to prepare. While pouring: a pour's own temperature.
                        if (!running && elapsed == 0.0 && preset.waterTempC > 0) Text("جهّز الماء على ${Methods.grams(preset.waterTempC)}°م", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
                        else if (step.kind.addsWater && step.tempC != null) Text("الماء على ${Methods.grams(step.tempC!!)}°", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
                        if (step.addition != null) Text(if (step.addition == "ice") "أضف ${Methods.grams(step.addedWaterGrams ?: 0.0)}غ ثلج"
                            else "أضف ${Methods.grams(step.addedWaterGrams ?: 0.0)}غ ماء " + (com.bunnlist.core.ADDITION_TEMPS.firstOrNull { it.first == step.additionTemp }?.second ?: "حرارة الغرفة"),
                            color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (step.note.isNotEmpty()) Text(step.note, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, lineHeight = 22.sp)
                        if (running || elapsed > 0) Text("باقي ${clock(pos.secondsLeftInStep)}", color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                    }
                }
            }
            // «التالي», lighting up and growing a little in the last three seconds.
            val next = preset.steps.getOrNull(pos.stepIndex + 1)
            if (!pos.isFinished && next != null) {
                val cue = state.isCueing
                val bg by animateColorAsState(if (cue) Gold.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), label = "cue")
                val sc by animateFloatAsState(if (cue) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "cueScale")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).scale(sc).clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("التالي", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("  ${next.kind.nameAr}", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.Refresh, "إعادة", enabled = elapsed > 0) { reset() }
                Button(onClick = { if (running) pause() else start() }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).height(60.dp), shape = CircleShape) {
                    Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                    Text(if (running) "  إيقاف مؤقت" else if (elapsed > 0 && !pos.isFinished) "  متابعة" else "  ابدأ", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                RoundButton(Icons.Filled.SkipNext, "الخطوة التالية", enabled = !pos.isFinished) { skip() }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f)) }
}

@Composable
private fun Dial(elapsed: Double, total: Int) {
    val progress by animateFloatAsState(minOf(elapsed / maxOf(total, 1), 1.0).toFloat(), tween(250), label = "dial")
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            drawCircle(Color.White.copy(alpha = 0.12f), style = Stroke(14.dp.toPx()))
            drawArc(Gold, -90f, 360f * progress, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(elapsed.toInt(), label = "secs", transitionSpec = { (fadeIn(tween(150)) + slideInVertically { -it / 3 }) togetherWith fadeOut(tween(150)) }) {
                Text(clock(it), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Text("من ${clock(total)}", color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
        }
    }
}

/** Espresso: dose in, target out, and a shot timer against the target window. */
@Composable
private fun EspressoShot(preset: BrewPreset, lotName: String?, onClose: () -> Unit, onRate: ((Int) -> Unit)?) {
    val target = remember(preset) { BrewScene.espressoTarget(preset) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var stopped by remember { mutableStateOf<Double?>(null) }
    val frame by rememberFrameClock()
    val view = LocalView.current
    val elapsed = stopped ?: startedAt?.let { (System.nanoTime() - it) / 1e9 + frame * 0 } ?: 0.0
    val running = startedAt != null && stopped == null

    Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DarkTop(onClose, null)
        Text(preset.nameAr, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(lotName, "${Methods.grams(preset.doseGrams)}غ بن ← ${Methods.grams(preset.yieldGrams ?: 0.0)}غ في الكوب").joinToString(" · "), color = White70, fontSize = 13.sp)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            EspressoSceneCanvas(elapsed, running, target, frame, Modifier.fillMaxWidth().height(240.dp))
            Text(String.format(Locale.ROOT, "%.1f", elapsed), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            // 0–40 s, the target window shaded, a marker at the shot's time.
            Column(Modifier.fillMaxWidth().padding(horizontal = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val w = size.width; val h = 10.dp.toPx(); val y = (size.height - h) / 2
                    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(0f, y), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawRoundRect(Brand.Olive.copy(alpha = 0.8f), Offset(w * target.first / 40f, y), androidx.compose.ui.geometry.Size(w * (target.last - target.first) / 40f, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                    drawCircle(Gold, 8.dp.toPx(), Offset(minOf(w * (elapsed / 40).toFloat(), w), size.height / 2))
                }
                Text("الهدف ${target.first}–${target.last} ث", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        stopped?.let {
            val s = Math.round(it).toInt()
            if (s in target) Text("✓ ضمن الهدف", color = Brand.Sage, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            else {
                Text(if (s < target.first) "أسرع من الهدف" else "أبطأ من الهدف", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (s < target.first) "جرّب المرة الجاية طحنة أنعم شوي. غيّر شي واحد بس عشان تعرف أثره." else "جرّب المرة الجاية طحنة أخشن شوي. غيّر شي واحد بس عشان تعرف أثره.",
                    color = White70, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
            }
        } ?: preset.steps.firstOrNull()?.note?.takeIf { it.isNotEmpty() }?.let { Text(it, color = White70, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp)) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (stopped != null) {
                RoundButton(Icons.Filled.Refresh, "إعادة", true) { startedAt = null; stopped = null }
                Button(onClick = { if (onRate != null) onRate(Math.round(stopped ?: 0.0).toInt()) else onClose() }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).height(60.dp), shape = CircleShape) { Text("خلصت", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            } else Button(onClick = {
                if (startedAt == null) { startedAt = System.nanoTime(); view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                else { stopped = elapsed; view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
            }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black), modifier = Modifier.weight(1f).height(60.dp), shape = CircleShape) {
                Icon(if (startedAt == null) Icons.Filled.PlayArrow else Icons.Filled.Stop, null)
                Text(if (startedAt == null) "  ابدأ الاستخلاص" else "  وقّف", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

/** Methods measured in hours (cold brew): the steps as a checklist, no countdown. */
@Composable
private fun Checklist(preset: BrewPreset, lotName: String?, onClose: () -> Unit, onRate: ((Int) -> Unit)?) {
    var done by remember { mutableStateOf(setOf<Int>()) }
    val view = LocalView.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar(preset.nameAr, onBack = onClose) {
            TextButton(enabled = done.isNotEmpty(), onClick = { if (onRate != null) onRate(0) else onClose() }) { Text("خلصت", fontWeight = FontWeight.Bold) }
        }
        Text(listOfNotNull(lotName, "${Methods.grams(preset.doseGrams)}غ بن", preset.hotWaterGrams?.let { "${Methods.grams(it)}غ ماء" }, "طحنة ${preset.grindAr}").joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(preset.steps) { i, s ->
                val on = i in done
                Card(onClick = { done = if (on) done - i else done + i; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                        val sc by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = 0.45f), label = "check")
                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.scale(sc))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(s.kind.nameAr, fontWeight = FontWeight.Bold)
                            if (s.note.isNotEmpty()) Text(s.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
