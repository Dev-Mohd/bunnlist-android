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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.bunnlist.core.BrewVoice
import com.bunnlist.core.StepKind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
private fun DarkTop(onClose: () -> Unit, finish: (() -> Unit)?, ink: Color = Color.White, accent: Color = Gold, extras: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = ink) }
        extras()
        Spacer(Modifier.weight(1f))
        if (finish != null) TextButton(onClick = finish, modifier = Modifier.heightIn(min = 48.dp)) { Text("خلصت", color = accent, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
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
    // Hands-free: spoken steps and tones (the speaker button mutes them) and the glance layout (the eye button).
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var voiceOn by remember { mutableStateOf(prefs.getBoolean("voice", true)) }
    var glance by remember { mutableStateOf(prefs.getBoolean("glance", true)) }
    var spokenSecond by remember { mutableIntStateOf(-1) }
    // «استعد»: seconds left before the clock starts, so the phone can be put down first.
    var prepLeft by remember { mutableStateOf<Int?>(null) }
    val inspecting = LocalInspectionMode.current
    val speaker = remember { if (inspecting) null else BrewSpeaker(context) }
    DisposableEffect(Unit) { onDispose { speaker?.release() } }
    val titles = remember(preset) { BrewVoice.titles(preset) }

    val elapsed = startedAt?.let { pausedElapsed + (System.nanoTime() - it) / 1e9 + frame * 0 } ?: pausedElapsed
    val running = startedAt != null
    val state = BrewScene.state(preset, elapsed, running || elapsed > 0)
    val pos = timeline.position(elapsed.toInt())

    // The finishing line plays on; a pause mid-brew cuts the sentence.
    fun pause() { pausedElapsed = elapsed; startedAt = null; if (!pos.isFinished) speaker?.stop() }
    fun reset() { prepLeft = null; startedAt = null; pausedElapsed = 0.0; lastStep = -1; cuedStep = -1; spokenSecond = -1; speaker?.stop() }
    fun begin() { startedAt = System.nanoTime(); if (lastStep < 0) lastStep = 0 }
    // A fresh brew gets «استعد» and a short count first; resuming starts at once.
    fun start() {
        if (pos.isFinished) reset()
        if (pausedElapsed == 0.0) { prepLeft = BrewVoice.PREP_SECONDS; if (voiceOn) speaker?.play(BrewVoice.Cue.Step(0, BrewVoice.READY_LINE, BrewVoice.Mood.Act), BrewVoice.readyClips) } else begin()
    }
    fun cancelPrep() { prepLeft = null; speaker?.stop() }
    LaunchedEffect(prepLeft != null) {
        while (true) {
            val left = prepLeft ?: break
            kotlinx.coroutines.delay(1000)
            if (prepLeft == null) break
            val next = left - 1
            if (next <= 0) { prepLeft = null; begin(); break }
            prepLeft = next
            if (next in 1..3 && voiceOn) speaker?.play(BrewVoice.Cue.Count(next), BrewVoice.countClips(next))
        }
    }
    fun skip() { val target = timeline.start(pos.stepIndex + 1).toDouble(); if (running) startedAt = System.nanoTime(); pausedElapsed = target }

    // One cue a second at most; a late frame past the end still says the finish.
    val second = minOf(elapsed.toInt(), timeline.totalSeconds)
    LaunchedEffect(second, running) {
        if (!running || second == spokenSecond) return@LaunchedEffect
        spokenSecond = second
        if (voiceOn) BrewVoice.cue(timeline, second, titles)?.let { speaker?.play(it, BrewVoice.clips(timeline, second, titles, preset.id)) }
    }

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

    // The glance colours the whole screen by what the hands are doing. Gold is the only light one: everything on it turns dark.
    val mood: BrewVoice.Mood? = when {
        pos.isFinished -> if (elapsed > 0) BrewVoice.Mood.Done else null
        running || elapsed > 0 -> BrewVoice.mood(preset.steps[pos.stepIndex], titles.getOrNull(pos.stepIndex))
        else -> null
    }
    val onGold = glance && mood == BrewVoice.Mood.Pour
    val ink = if (onGold) Color.Black else Color.White
    val accent = if (onGold) Color.Black else Gold
    val glanceBg by animateColorAsState(glanceColor(mood), tween(350), label = "glance")
    val finish: (() -> Unit)? = if (elapsed > 0) ({ if (onRate != null) onRate(elapsed.toInt()) else onClose() }) else null

    Box(if (glance) Modifier.fillMaxSize().background(glanceBg)
        else Modifier.fillMaxSize().background(Color.Black).background(Brush.radialGradient(listOf(tint.copy(alpha = warmth), Color.Transparent), radius = 1100f))) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DarkTop(onClose, finish, ink, accent) {
                IconButton(onClick = { voiceOn = !voiceOn; prefs.edit().putBoolean("voice", voiceOn).apply(); if (!voiceOn) speaker?.stop() }) {
                    Icon(if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, if (voiceOn) "إيقاف الصوت" else "تشغيل الصوت", tint = ink)
                }
                IconButton(onClick = { glance = !glance; prefs.edit().putBoolean("glance", glance).apply() }) {
                    Icon(if (glance) Icons.Filled.Visibility else Icons.Outlined.Visibility, if (glance) "عرض التفاصيل" else "عرض النظرة السريعة", tint = ink)
                }
            }
            if (!glance && prepLeft != null) Text("${BrewVoice.READY_LINE} $prepLeft", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (glance) {
                // Wet hands miss small buttons: the whole board takes a double tap for «next».
                GlanceBoard(preset, pos, elapsed, mood, titles.getOrNull(pos.stepIndex) ?: state.titleAr, voiceOn, speaker?.hasArabicVoice, ink,
                    onRate?.let { rate -> { rate(elapsed.toInt()) } },
                    Modifier.weight(1f).pointerInput(running, pos.stepIndex, pos.isFinished) {
                        detectTapGestures(onDoubleTap = { if (running && !pos.isFinished) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); skip() } })
                    }, prepLeft = prepLeft, hint = running && !pos.isFinished)
            } else {
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
            }
            // «التالي», lighting up and growing a little in the last three seconds.
            val next = preset.steps.getOrNull(pos.stepIndex + 1)
            if (!pos.isFinished && next != null) {
                val cue = state.isCueing
                val bg by animateColorAsState(if (cue) accent.copy(alpha = 0.35f) else ink.copy(alpha = 0.1f), label = "cue")
                val sc by animateFloatAsState(if (cue) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "cueScale")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).scale(sc).clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("التالي", color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text("  ${titles.getOrNull(pos.stepIndex + 1) ?: next.kind.nameAr}", color = ink, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.Refresh, "إعادة", enabled = elapsed > 0, ink = ink) { reset() }
                Button(onClick = { if (prepLeft != null) cancelPrep() else if (running) pause() else start() }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (onGold) Gold else Color.Black),
                    modifier = Modifier.weight(1f).height(60.dp), shape = CircleShape) {
                    Icon(if (prepLeft != null) Icons.Filled.Close else if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                    Text(if (prepLeft != null) "  إلغاء" else if (running) "  إيقاف مؤقت" else if (elapsed > 0 && !pos.isFinished) "  متابعة" else "  ابدأ", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                RoundButton(Icons.Filled.SkipNext, "الخطوة التالية", enabled = !pos.isFinished, ink = ink) { skip() }
            }
        }
    }
}

internal fun glanceColor(mood: BrewVoice.Mood?): Color = when (mood) {
    BrewVoice.Mood.Pour -> Gold
    BrewVoice.Mood.Act -> Color(0xFF4A3428)
    BrewVoice.Mood.Done -> Color(0xFF536344)
    BrewVoice.Mood.Wait, null -> Color(0xFF171411)
}

/**
 * The glance layout: what to do in two words, and one number big enough to read from the
 * counter. Pouring shows the scale reading; anything else the seconds left.
 */
@Composable
internal fun GlanceBoard(preset: BrewPreset, pos: BrewTimeline.Position, elapsed: Double, mood: BrewVoice.Mood?, titleAr: String?,
                         voiceOn: Boolean, hasArabicVoice: Boolean?, ink: Color, onRate: (() -> Unit)?, modifier: Modifier = Modifier,
                         prepLeft: Int? = null, hint: Boolean = false) {
    val soft = ink.copy(alpha = 0.75f)
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(preset.nameAr, color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (mood != null && !pos.isFinished) Text("خطوة ${pos.stepIndex + 1} من ${preset.steps.size} · ${clock(elapsed.toInt())}", color = ink.copy(alpha = 0.7f), fontSize = 14.sp)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val step = preset.steps.getOrNull(pos.stepIndex)
            when {
                prepLeft != null -> {
                    Text(BrewVoice.READY_LINE, color = ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(prepLeft.toString(), color = ink, fontSize = 150.sp, lineHeight = 160.sp, fontWeight = FontWeight.Black)
                    Text("حط الجوال وامسك الغلاية", color = soft, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                mood == BrewVoice.Mood.Done -> {
                    Icon(Icons.Filled.Coffee, null, tint = ink, modifier = Modifier.size(64.dp))
                    Text("خلصت، بالعافية", color = ink, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("تذوّقها وهي دافئة ثم وهي تبرد؛ النكهات تتغير.", color = soft, textAlign = TextAlign.Center)
                    if (onRate != null) Button(onClick = onRate, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                        modifier = Modifier.padding(top = 12.dp).heightIn(min = 52.dp)) { Text("كيف طلعت؟", fontWeight = FontWeight.Bold) }
                }
                mood == null || step == null -> {
                    Text("جاهز؟", color = ink, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
                    if (preset.waterTempC > 0) Text("جهّز الماء على ${Methods.grams(preset.waterTempC)}°م", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (voiceOn) "حط الجوال قدامك واضغط «ابدأ». بنقول لك كل خطوة بالصوت، وما تحتاج تلمسه."
                        else "الصوت مقفّل. شغّله من زر السماعة فوق عشان نقول لك كل خطوة.",
                        color = soft, textAlign = TextAlign.Center, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
                    if (voiceOn && hasArabicVoice == false) Text("جوالك ما فيه صوت عربي، فبتسمع النغمات بس. تقدر تنزّل صوت عربي من إعدادات الجوال (تحويل النص إلى كلام).",
                        color = ink.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
                else -> {
                    val pourTo = pos.pourTo.takeIf { mood == BrewVoice.Mood.Pour }
                    val left = pos.secondsLeftInStep
                    Text(if (pourTo != null) (if (step.kind == StepKind.Bloom) "تفتيح · صبّ لين" else "صبّ لين") else titleAr ?: glanceVerb(step.kind),
                        color = ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(if (pourTo != null) Methods.grams(pourTo) else if (left >= 60) clock(left) else left.toString(),
                        color = ink, fontSize = 150.sp, lineHeight = 160.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                    Text(if (pourTo != null) "غرام على الميزان" else if (left >= 60) "باقي" else "ثانية باقية", color = soft, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (pourTo != null) Text("باقي ${clock(left)}", color = ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                    else if (step.addition != null) Text(BrewVoice.line(step, null), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    if (step.note.isNotEmpty()) Text(step.note, color = ink.copy(alpha = 0.8f), textAlign = TextAlign.Center, lineHeight = 22.sp, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (hint) Text("ضغطتين على الشاشة تنقلك للخطوة التالية", color = ink.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

private fun glanceVerb(kind: StepKind) = when (kind) {
    StepKind.Wait -> "انتظر"
    StepKind.Swirl -> "حرّك القمع"
    StepKind.Stir -> "قلّب"
    StepKind.Press -> "اضغط بهدوء"
    StepKind.Serve -> "قدّم القهوة"
    StepKind.Bloom, StepKind.Pour -> "صبّ"
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, ink: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(60.dp).clip(CircleShape).background(ink.copy(alpha = if (enabled) 0.12f else 0.05f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, tint = ink.copy(alpha = if (enabled) 1f else 0.35f)) }
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
    // The shot speaks twice: when the window opens and when it ends (the timer's speaker setting).
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var voiceOn by remember { mutableStateOf(prefs.getBoolean("voice", true)) }
    val inspecting = LocalInspectionMode.current
    val speaker = remember { if (inspecting) null else BrewSpeaker(context) }
    DisposableEffect(Unit) { onDispose { speaker?.release() } }
    val second = elapsed.toInt()
    LaunchedEffect(second, running) {
        if (!running || !voiceOn) return@LaunchedEffect
        BrewVoice.espressoLine(second, target)?.let {
            speaker?.play(BrewVoice.Cue.Step(0, it, if (second == target.last) BrewVoice.Mood.Wait else BrewVoice.Mood.Pour), BrewVoice.espressoClips(second, target))
        }
    }

    var prepDone by remember { mutableStateOf(setOf<Int>()) }
    Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DarkTop(onClose, null) {
            IconButton(onClick = { voiceOn = !voiceOn; prefs.edit().putBoolean("voice", voiceOn).apply(); if (!voiceOn) speaker?.stop() }) {
                Icon(if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, if (voiceOn) "إيقاف الصوت" else "تشغيل الصوت", tint = Color.White)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(preset.nameAr, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(lotName, "${Methods.grams(preset.doseGrams)}غ بن ← ${Methods.grams(preset.yieldGrams ?: 0.0)}غ في الكوب" +
            (preset.milkDrink?.let { " + ${Methods.grams(preset.milkGrams ?: it.typicalMilkGrams)}غ حليب" } ?: "")).joinToString(" · "), color = White70, fontSize = 13.sp)
        // Before the shot: getting the puck ready, as a list to tick through.
        if (startedAt == null && stopped == null && preset.prepAr.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قبل الاستخلاص", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                preset.prepAr.forEachIndexed { i, step ->
                    val on = i in prepDone
                    Row(Modifier.fillMaxWidth().pressable { prepDone = if (on) prepDone - i else prepDone + i }, verticalAlignment = Alignment.Top) {
                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (on) Gold else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        Text(step, color = Color.White.copy(alpha = if (on) 0.5f else 0.85f), fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
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
        // After the shot (and to read ahead): the milk for this drink.
        preset.milkDrink?.let { Box(Modifier.padding(horizontal = 16.dp)) { MilkGuideCard(it, preset.milkGrams, onDark = true) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (stopped != null) {
                RoundButton(Icons.Filled.Refresh, "إعادة", true) { startedAt = null; stopped = null }
                Button(onClick = { if (onRate != null) onRate(Math.round(stopped ?: 0.0).toInt()) else onClose() }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).height(60.dp), shape = CircleShape) { Text("خلصت", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            } else Button(onClick = {
                if (startedAt == null) {
                    startedAt = System.nanoTime(); view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (voiceOn) speaker?.play(BrewVoice.Cue.Step(0, BrewVoice.ESPRESSO_START_LINE, BrewVoice.Mood.Pour), BrewVoice.espressoStartClips)
                } else {
                    stopped = elapsed; view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    // The verdict, and the one change to try, without looking at the screen.
                    if (voiceOn) BrewVoice.shot(Math.round(elapsed).toInt(), target).let { speaker?.play(BrewVoice.Cue.Finished(it.lineAr), listOf(it.clip)) }
                }
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
    // Reads the step to do now, then the next one as each is ticked (the timer's speaker setting).
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var voiceOn by remember { mutableStateOf(prefs.getBoolean("voice", true)) }
    val inspecting = LocalInspectionMode.current
    val speaker = remember { if (inspecting) null else BrewSpeaker(context) }
    DisposableEffect(Unit) { onDispose { speaker?.release() } }
    fun speak(i: Int) {
        val s = preset.steps.getOrNull(i) ?: return
        if (voiceOn) speaker?.play(BrewVoice.Cue.Step(i, BrewVoice.checklistLine(s), BrewVoice.Mood.Act), BrewVoice.stepClips(preset.id, i))
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); speak(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar(preset.nameAr, onBack = onClose) {
            IconButton(onClick = { voiceOn = !voiceOn; prefs.edit().putBoolean("voice", voiceOn).apply(); if (voiceOn) speak(preset.steps.indices.firstOrNull { it !in done } ?: 0) else speaker?.stop() }) {
                Icon(if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, if (voiceOn) "إيقاف الصوت" else "تشغيل الصوت")
            }
            TextButton(enabled = done.isNotEmpty(), onClick = { if (onRate != null) onRate(0) else onClose() }) { Text("خلصت", fontWeight = FontWeight.Bold) }
        }
        Text(listOfNotNull(lotName, "${Methods.grams(preset.doseGrams)}غ بن", preset.hotWaterGrams?.let { "${Methods.grams(it)}غ ماء" }, "طحنة ${preset.grindAr}").joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(preset.steps) { i, s ->
                val on = i in done
                Card(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (on) done = done - i else {
                        done = done + i
                        // The next step still to do; the finish when none is left.
                        val next = preset.steps.indices.firstOrNull { it > i && it !in done }
                        if (next != null) speak(next)
                        else if (done.size == preset.steps.size && voiceOn) speaker?.play(BrewVoice.Cue.Finished(BrewVoice.FINISHED_LINE), listOf("done"))
                    }
                }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                        val sc by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = 0.45f), label = "check")
                        Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.scale(sc))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(when (s.addition) { "ice" -> "ثلج"; "dilute" -> "تخفيف"; else -> s.kind.nameAr }, fontWeight = FontWeight.Bold)
                            s.addedWaterGrams?.takeIf { it > 0 }?.let { g ->
                                Text("${Methods.grams(g)}غ " + if (s.addition == "ice") "ثلج" else "ماء", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Tone.olive)
                            }
                            if (s.note.isNotEmpty()) Text(s.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
