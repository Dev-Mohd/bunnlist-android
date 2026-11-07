package com.bunnlist.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.AdvisorAnswers
import com.bunnlist.core.AdvisorStep
import com.bunnlist.core.ApiError
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.RemoteAdvisorResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed interface AdvisorPhase { data object Asking : AdvisorPhase; data object Loading : AdvisorPhase; data class Done(val r: RemoteAdvisorResult) : AdvisorPhase; data object Failed : AdvisorPhase }

/** «ساعدني أختار» state, kept by the route so a coffee opened from the results comes back to them. */
class AdvisorSession {
    var answers by mutableStateOf(AdvisorAnswers())
    var index by mutableIntStateOf(0)
    internal var phase by mutableStateOf<AdvisorPhase>(AdvisorPhase.Asking)
    var forward by mutableStateOf(true)
    /** Result cards already revealed, so they don't animate in again on return. */
    var shown by mutableIntStateOf(0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorScreen(app: BunnListApp, session: AdvisorSession, nav: Nav) {
    val st = session
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val steps = st.answers.steps
    val step = steps[st.index.coerceAtMost(steps.lastIndex)]
    var addTo by remember { mutableStateOf<String?>(null) }
    fun tap() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    fun advance() { scope.launch { delay(320); st.forward = true; st.index = (st.index + 1).coerceAtMost(st.answers.steps.lastIndex) } }
    fun back() { st.forward = false; st.index = (st.index - 1).coerceAtLeast(0) }
    fun restart() { st.answers = AdvisorAnswers(); st.index = 0; st.forward = false; st.shown = 0; st.phase = AdvisorPhase.Asking }
    fun submit() {
        st.phase = AdvisorPhase.Loading; st.shown = 0
        scope.launch {
            val started = System.currentTimeMillis()
            st.phase = try {
                val token = if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null
                val r = app.api.advise(st.answers, token)
                delay((1800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                tap()
                AdvisorPhase.Done(r)
            } catch (e: ApiError) { AdvisorPhase.Failed }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.Close, "إغلاق") }
            Spacer(Modifier.weight(1f))
            if (st.phase == AdvisorPhase.Asking && st.index > 0) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        }
        AnimatedContent(st.phase, label = "phase", contentKey = { it::class }, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }) { phase ->
            when (phase) {
                AdvisorPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AdvisorLoading() }
                AdvisorPhase.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📡", fontSize = 44.sp)
                        Text("ما قدرنا نختار الحين", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { submit() }) { Text("حاول مرة ثانية") }
                    }
                }
                is AdvisorPhase.Done -> AdvisorResults(phase.r, st, signedIn = app.session.isSignedIn, onOpen = { nav.push(Route.Lot(it)) },
                    onAddTo = { addTo = it }, onAgain = { restart() })
                AdvisorPhase.Asking -> Column(Modifier.fillMaxSize()) {
                    // Progress: fills from the right, with «N من M» like iOS.
                    val progress by animateFloatAsState((st.index + 1f) / steps.size, spring(dampingRatio = 0.8f, stiffness = 200f), label = "progress")
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Brand.Gold.copy(alpha = 0.18f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                        }
                        Text(if (step == AdvisorStep.Text) "  ✨ اختياري" else "  ${st.index + 1} من ${steps.size - 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedContent(step, label = "step", transitionSpec = {
                        val fwd = st.forward
                        (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { if (fwd) -it else it } + fadeIn()) togetherWith
                            (slideOutHorizontally(tween(250)) { if (fwd) it else -it } + fadeOut(tween(200)))
                    }) { s ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            val a = st.answers
                            when (s) {
                                AdvisorStep.Mood -> {
                                    Question("وش جوّك اليوم؟", "على حسب جوّك نختار")
                                    OptionGrid(listOf(Opt("☀️", "أبي أصحصح", "قوية ومنعشة", "wake"), Opt("🛋️", "روقان", "أبي أهدأ", "calm"),
                                        Opt("👥", "عندي ضيوف", "ترضي الكل", "guests"), Opt("🧭", "شي جديد", "فاجئني", "new")), a.mood) { tap(); st.answers = a.copy(mood = it); advance() }
                                }
                                AdvisorStep.Method -> {
                                    Question("وش بتستخدم؟", "نختار اللي تطلع حلوة فيه")
                                    OptionGrid(listOf(Opt("💧", "V60", null, "V60"), Opt("☕", "إسبريسو", null, "ESPRESSO"), Opt("⚗️", "كيمكس", null, "CHEMEX"),
                                        Opt("🫖", "فرنش برس", null, "FRENCH_PRESS")), a.method) { tap(); st.answers = a.copy(method = it, milk = if (it == "ESPRESSO") a.milk else null); advance() }
                                }
                                AdvisorStep.Milk -> {
                                    Question("تشربها مع حليب؟", "لاتيه، كورتادو، فلات وايت…")
                                    OptionGrid(listOf(Opt("🥛", "إيه، مع حليب", "نختار اللي تحلى معه", "yes"), Opt("☕", "لا، سادة", "إسبريسو صافي", "no")),
                                        when (a.milk) { true -> "yes"; false -> "no"; null -> null }) { tap(); st.answers = a.copy(milk = it == "yes"); advance() }
                                }
                                AdvisorStep.Flavors -> {
                                    Question("وش يشدّك في الطعم؟", "اختر اللي تبي، أو تخطّ")
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FlavorFamily.advisorChoices.forEach { f ->
                                            val on = f in a.families
                                            val c = Color(0xFF000000 or f.rgb.toLong())
                                            val sc by animateFloatAsState(if (on) 1.07f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "chip")
                                            Row(Modifier.scale(sc).clip(RoundedCornerShape(50)).background(if (on) c else MaterialTheme.colorScheme.surface)
                                                .pressable { tap(); st.answers = a.copy(families = if (on) a.families - f else a.families + f) }.padding(horizontal = 16.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(if (on) Color.White else c))
                                                Text("  ${f.shortNameAr}", fontWeight = FontWeight.Bold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                    NextButton(if (a.families.isEmpty()) "أي شي، تخطّي" else "التالي") { advance() }
                                }
                                AdvisorStep.Serve -> {
                                    Question("حار ولا بارد؟", null)
                                    Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ServeHalf("☕", "حار", a.serve, "HOT", listOf(Color(0xFFF5D6B8), Color(0xFFE8A86E)), Color(0xFF592E12)) { tap(); st.answers = a.copy(serve = "HOT"); advance() }
                                        ServeHalf("🧊", "بارد", a.serve, "ICED", listOf(Color(0xFFDBEDF2), Color(0xFF9BC7D9)), Color(0xFF173B4A)) { tap(); st.answers = a.copy(serve = "ICED"); advance() }
                                    }
                                }
                                AdvisorStep.Adventure -> {
                                    Question("مغامر ولا تبي المضمون؟", "اسحب")
                                    AdventureSlider(a.adventure.toFloat()) { v ->
                                        val old = a.adventure
                                        if ((v < 0.34) != (old < 0.34) || (v < 0.67) != (old < 0.67)) tap()
                                        st.answers = st.answers.copy(adventure = v.toDouble())
                                    }
                                    NextButton("التالي") { advance() }
                                }
                                AdvisorStep.Text -> {
                                    Question("تبي تقول شي زيادة؟", "اكتب بكلامك، ونفهمك")
                                    val signedIn = app.session.isSignedIn
                                    OutlinedTextField(a.text, { st.answers = st.answers.copy(text = it.take(300)) }, enabled = signedIn,
                                        placeholder = { Text("مثل: أبي شي يشبه قهوة جربتها في مقهى، طعمها توت وما فيها مرارة") },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), shape = RoundedCornerShape(20.dp))
                                    if (!signedIn) Text("سجّل دخول عشان نقرأ كلامك. تقدر تكمل بدونه.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    NextButton("طلّع لي قهوتي ✨") { submit() }
                                    TextButton(onClick = { st.answers = st.answers.copy(text = ""); submit() }, modifier = Modifier.fillMaxWidth()) {
                                        Text("تخطّي", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    addTo?.let { slug -> AddToListSheet(app, slug, onDismiss = { addTo = null }) }
}

private data class Opt(val emoji: String, val title: String, val sub: String?, val value: String)

/** Two-column choice cards: the emoji tilts up and the card springs when chosen (iOS optionGrid). */
@Composable
private fun OptionGrid(items: List<Opt>, selected: String?, pick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { o ->
                    val on = selected == o.value
                    val sc by animateFloatAsState(if (on) 1.03f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "card")
                    val emo by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "emoji")
                    Column(Modifier.weight(1f).heightIn(min = 130.dp).scale(sc).clip(RoundedCornerShape(22.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                        .border(2.5.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(22.dp))
                        .pressable { pick(o.value) }.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        Text(o.emoji, fontSize = 40.sp, modifier = Modifier.graphicsLayer { scaleX = 1 + 0.18f * emo; scaleY = 1 + 0.18f * emo; rotationZ = -6f * emo })
                        Text(o.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        o.sub?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.ServeHalf(emoji: String, label: String, current: String?, value: String, colors: List<Color>, ink: Color, onClick: () -> Unit) {
    val on = current == value
    val dim = current != null && !on
    val sc by animateFloatAsState(if (on) 1.04f else if (dim) 0.94f else 1f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "serve")
    Column(Modifier.weight(1f).fillMaxHeight().scale(sc).graphicsLayer { alpha = if (dim) 0.4f else 1f }
        .shadow(if (on) 14.dp else 0.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(colors)).pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text(emoji, fontSize = 72.sp)
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

/** The adventure track: gold to olive, a white knob with the face (iOS AdvisorAdventureSlider). */
@Composable
private fun AdventureSlider(value: Float, onChange: (Float) -> Unit) {
    val face = if (value < 0.34) "🛡️" else if (value < 0.67) "🙂" else "🚀"
    val line = if (value < 0.34) "أبي المضمون 👌" else if (value < 0.67) "وسط، فاجئني شوي" else "مغامر 😎"
    Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(50.dp)) {
                val w = constraints.maxWidth.toFloat()
                val knob = with(LocalDensity.current) { 50.dp.toPx() }
                val current by rememberUpdatedState(value)
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Brand.Gold, Brand.Gold.copy(alpha = 0.2f), Brand.Olive))))
                Box(Modifier.offset { IntOffset((value * (w - knob)).toInt(), 0) }.size(50.dp).shadow(6.dp, CircleShape).background(Color.White, CircleShape)
                    .pointerInput(w) {
                        detectDragGestures { change, _ -> onChange(((change.position.x + current * (w - knob) - knob / 2) / (w - knob)).coerceIn(0f, 1f)) }
                    }, contentAlignment = Alignment.Center) { Text(face, fontSize = 24.sp) }
                Box(Modifier.matchParentSize().pointerInput(w) {
                    detectTapGestures { p -> onChange(((p.x - knob / 2) / (w - knob)).coerceIn(0f, 1f)) }
                })
            }
        }
        Row {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🚀 مغامر", fontWeight = FontWeight.Bold); Text("معالجة غريبة أو دولة جديدة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🛡️ مضمون", fontWeight = FontWeight.Bold); Text("الأعلى تقييمًا", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(line, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Question(title: String, subtitle: String?) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AdvisorLoading() {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "angle")
    val lines = listOf("نقرأ ذوقك…", "نقلّب في القهاوي عشانك…", "نختار الأنسب…")
    var line by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1100); line = (line + 1) % lines.size } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            val bean = MaterialTheme.colorScheme.tertiary
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().rotate(angle)) {
                repeat(5) { i ->
                    rotate(i * 72f) {
                        drawRoundRect(bean, topLeft = androidx.compose.ui.geometry.Offset(center.x - 11.dp.toPx(), center.y - 95.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(22.dp.toPx(), 30.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(11.dp.toPx()))
                    }
                }
            }
            Text("☕", fontSize = 60.sp)
        }
        AnimatedContent(line, label = "line", transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() }) {
            Text(lines[it], fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Choice(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    // A springy pop on the chosen card, like iOS.
    val sc by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "choice")
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), modifier = Modifier.scale(sc),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 32.sp)
            Text("  $label", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun NextButton(title: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(50)) { Text(title, fontSize = 17.sp) }
}

/** iOS AdvisorResults: header, «فهمنا منك» chips, cards that spring in one by one with a filling match ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvisorResults(r: RemoteAdvisorResult, st: AdvisorSession, signedIn: Boolean, onOpen: (String) -> Unit, onAddTo: (String) -> Unit, onAgain: () -> Unit) {
    LaunchedEffect(r) {
        for (i in st.shown + 1..maxOf(1, r.picks.size)) { delay(260); st.shown = i }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✨ قهوتك اليوم", fontWeight = FontWeight.Bold, color = Brand.Gold, fontSize = 15.sp)
            Text(if (r.picks.isEmpty()) "ما لقينا شي يناسب" else "لقينا لك ${r.picks.size} تناسبك", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        r.understood?.chips?.takeIf { it.isNotEmpty() }?.let { chips ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✨ فهمنا منك:", fontWeight = FontWeight.Bold, color = Brand.Gold, fontSize = 15.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEachIndexed { i, c ->
                        Text(c, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(listOf(Brand.Olive, Brand.Brown, Brand.Gold)[i % 3]).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
        r.picks.forEachIndexed { i, pick ->
            val visible = st.shown > i
            val enter by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 180f), label = "pick$i")
            Box(Modifier.graphicsLayer { alpha = enter; translationY = (1 - enter) * 40.dp.toPx() }) {
                PickCard(pick, first = i == 0, play = visible, onClick = { onOpen(pick.lot.slug) }, onAddTo = if (signedIn) ({ onAddTo(pick.lot.slug) }) else null)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 50.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onAgain),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp)); Text("  جرّب أسئلة ثانية", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PickCard(pick: com.bunnlist.core.RemoteAdvisorPick, first: Boolean, play: Boolean, onClick: () -> Unit, onAddTo: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
        .border(2.5.dp, if (first) Brand.Gold else Color.Transparent, RoundedCornerShape(22.dp)).pressable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(70.dp, 92.dp), contentAlignment = Alignment.Center) {
            if (pick.lot.imageUrl != null) coil.compose.AsyncImage(pick.lot.imageUrl, null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else CoffeeArtCard(pick.lot.roasterNameAr, pick.lot.nameAr, pick.lot.originNameAr, pick.lot.originCode, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pick.tag?.let {
                Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (first) Brand.Gold else Brand.Olive).padding(horizontal = 9.dp, vertical = 3.dp))
            }
            Text(pick.lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
            Text(pick.lot.roasterNameAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pick.reason, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 2)
            onAddTo?.let {
                Row(Modifier.padding(top = 2.dp).pressable(onClick = it), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(16.dp), tint = Brand.Brown)
                    Text(" أضفها لقائمة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Brown)
                }
            }
        }
        MatchRing(pick.score, play, 66.dp, MaterialTheme.colorScheme.primary)
    }
}

/** iOS MatchRing: the ring fills to the score after a beat, «N% تناسبك» inside. */
@Composable
internal fun MatchRing(score: Int, play: Boolean, size: androidx.compose.ui.unit.Dp, tint: Color) {
    val p by animateFloatAsState(if (play) score / 100f else 0f, tween(1400, delayMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            drawCircle(tint.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx()))
            drawArc(tint, -90f, 360f * p, false, style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score%", fontSize = (size.value * 0.27f).sp, fontWeight = FontWeight.Bold, color = tint, lineHeight = (size.value * 0.3f).sp)
            Text("تناسبك", fontSize = (size.value * 0.15f).sp, color = tint, lineHeight = (size.value * 0.17f).sp)
        }
    }
}
