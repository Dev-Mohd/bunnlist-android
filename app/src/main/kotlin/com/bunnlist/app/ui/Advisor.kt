package com.bunnlist.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

private sealed interface Phase { data object Asking : Phase; data object Loading : Phase; data class Done(val r: RemoteAdvisorResult) : Phase; data object Failed : Phase }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisorScreen(app: BunnListApp, nav: Nav) {
    var answers by remember { mutableStateOf(AdvisorAnswers()) }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf<Phase>(Phase.Asking) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val steps = answers.steps
    val step = steps[index.coerceAtMost(steps.lastIndex)]

    fun advance() { scope.launch { delay(280); index = (index + 1).coerceAtMost(answers.steps.lastIndex) } }
    fun submit() {
        phase = Phase.Loading
        scope.launch {
            val started = System.currentTimeMillis()
            phase = try {
                val token = if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null
                val r = app.api.advise(answers, token)
                delay((1600 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                Phase.Done(r)
            } catch (e: ApiError) { Phase.Failed }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.Close, "إغلاق") }
            if (phase == Phase.Asking) {
                LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                if (index > 0) TextButton(onClick = { index-- }) { Text("رجوع") } else Spacer(Modifier.width(64.dp))
            }
        }
        when (val p = phase) {
            Phase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("☕", fontSize = 64.sp); CircularProgressIndicator(); Text("نقلّب في القهاوي عشانك…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Phase.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ما قدرنا نختار الحين", fontSize = 18.sp)
                    Button(onClick = { submit() }) { Text("حاول مرة ثانية") }
                }
            }
            is Phase.Done -> AdvisorResult(p.r, onOpen = { nav.push(Route.Lot(it)) }, onAgain = { answers = AdvisorAnswers(); index = 0; phase = Phase.Asking })
            Phase.Asking -> AnimatedContent(step, label = "step") { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        AdvisorStep.Mood -> {
                            Question("وش جوّك اليوم؟", "على حسب جوّك نختار")
                            listOf(Triple("☀️", "أبي أصحصح", "wake"), Triple("🛋️", "روقان", "calm"), Triple("👥", "عندي ضيوف", "guests"), Triple("🧭", "شي جديد", "new"))
                                .forEach { (e, l, v) -> Choice(e, l, answers.mood == v) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); answers = answers.copy(mood = v); advance() } }
                        }
                        AdvisorStep.Method -> {
                            Question("وش بتستخدم؟", "نختار اللي تطلع حلوة فيه")
                            listOf(Triple("💧", "V60", "V60"), Triple("☕", "إسبريسو", "ESPRESSO"), Triple("⚗️", "كيمكس", "CHEMEX"), Triple("🫖", "فرنش برس", "FRENCH_PRESS"))
                                .forEach { (e, l, v) -> Choice(e, l, answers.method == v) { answers = answers.copy(method = v, milk = if (v == "ESPRESSO") answers.milk else null); advance() } }
                        }
                        AdvisorStep.Milk -> {
                            Question("تشربها مع حليب؟", "لاتيه، كورتادو، فلات وايت…")
                            Choice("🥛", "إيه، مع حليب", answers.milk == true) { answers = answers.copy(milk = true); advance() }
                            Choice("☕", "لا، سادة", answers.milk == false) { answers = answers.copy(milk = false); advance() }
                        }
                        AdvisorStep.Flavors -> {
                            Question("وش يشدّك في الطعم؟", "اختر اللي تبي، أو تخطّ")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FlavorFamily.advisorChoices.forEach { f ->
                                    val on = f in answers.families
                                    val c = Color(0xFF000000 or f.rgb.toLong())
                                    FilterChip(selected = on, onClick = { answers = answers.copy(families = if (on) answers.families - f else answers.families + f) },
                                        label = { Text(f.shortNameAr, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(if (on) Color.White else c)) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c, selectedLabelColor = Color.White))
                                }
                            }
                            NextButton(if (answers.families.isEmpty()) "أي شي، تخطّي" else "التالي") { advance() }
                        }
                        AdvisorStep.Serve -> {
                            Question("حار ولا بارد؟", null)
                            Choice("☕", "حار", answers.serve == "HOT") { answers = answers.copy(serve = "HOT"); advance() }
                            Choice("🧊", "بارد", answers.serve == "ICED") { answers = answers.copy(serve = "ICED"); advance() }
                        }
                        AdvisorStep.Adventure -> {
                            Question("مغامر ولا تبي المضمون؟", "اسحب")
                            val v = answers.adventure
                            Text(if (v < 0.34) "🛡️ أبي المضمون" else if (v < 0.67) "🙂 وسط، فاجئني شوي" else "🚀 مغامر",
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Slider(value = v.toFloat(), onValueChange = { answers = answers.copy(adventure = it.toDouble()) })
                            Row { Text("🛡️ الأعلى تقييمًا", fontSize = 13.sp); Spacer(Modifier.weight(1f)); Text("🚀 معالجة غريبة أو دولة جديدة", fontSize = 13.sp) }
                            NextButton("التالي") { advance() }
                        }
                        AdvisorStep.Text -> {
                            Question("تبي تقول شي زيادة؟", "اكتب بكلامك، ونفهمك")
                            val signedIn = app.session.isSignedIn
                            OutlinedTextField(answers.text, { answers = answers.copy(text = it.take(300)) }, enabled = signedIn,
                                placeholder = { Text("مثل: أبي شي يشبه قهوة جربتها في مقهى، طعمها توت وما فيها مرارة") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), shape = RoundedCornerShape(20.dp))
                            if (!signedIn) Text("سجّل دخول عشان نقرأ كلامك. تقدر تكمل بدونه.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NextButton("طلّع لي قهوتي ✨") { submit() }
                            TextButton(onClick = { answers = answers.copy(text = ""); submit() }, modifier = Modifier.fillMaxWidth()) { Text("تخطّي") }
                        }
                    }
                }
            }
        }
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
private fun Choice(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvisorResult(r: RemoteAdvisorResult, onOpen: (String) -> Unit, onAgain: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✨ قهوتك اليوم", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 18.sp)
        r.understood?.chips?.takeIf { it.isNotEmpty() }?.let { chips ->
            Text("فهمنا منك:", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { AssistChip(onClick = {}, label = { Text(it) }) } }
        }
        if (r.picks.isEmpty()) Text("ما لقينا شي يناسب، جرّب إجابات ثانية.")
        r.picks.forEach { pick ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pick.tag?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("توافق ${pick.score}٪", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                LotRow(pick.lot, onClick = { onOpen(pick.lot.slug) })
                Text(pick.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("جرّب من جديد") }
    }
}
