package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewTimeline
import com.bunnlist.core.Methods
import kotlinx.coroutines.delay

/** «حضّر»: pick a method, then a starting recipe. */
@Composable
fun BrewScreen(nav: Nav, initialMethod: String?, lotName: String?, onLotCleared: () -> Unit) {
    var method by remember(initialMethod) { mutableStateOf(initialMethod?.takeIf { it in BrewPreset.methods } ?: "V60") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text("حضّر", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        lotName?.let {
            InputChip(selected = true, onClick = onLotCleared, label = { Text("تحضّر: $it") },
                trailingIcon = { Icon(Icons.Filled.Close, "إزالة", Modifier.size(16.dp)) }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrewPreset.methods.forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(Methods.nameAr(m)) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(BrewPreset.presets(method), key = { it.id }) { p ->
                Card(onClick = { nav.push(Route.Brewing(p, lotName)) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.nameAr, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(p.summaryAr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        RecipeNumbers(p)
                        Text("وصفة بداية من BunnList", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeNumbers(p: BrewPreset) {
    val items = listOfNotNull(
        "${Methods.grams(p.doseGrams)}غ" to "بن",
        p.hotWaterGrams?.let { "${Methods.grams(it)}غ" to "ماء" },
        p.iceGrams?.let { "${Methods.grams(it)}غ" to "ثلج" },
        p.yieldGrams?.let { "${Methods.grams(it)}غ" to "الناتج" },
        "${Methods.grams(p.waterTempC)}°" to "الحرارة",
        p.ratioText?.let { it to "النسبة" },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (v, k) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The guided brew: a countdown per step with the scale reading to pour to. Keeps the screen on. */
@Composable
fun BrewSessionScreen(preset: BrewPreset, lotName: String?, onClose: () -> Unit) {
    val timeline = remember(preset) { BrewTimeline(preset.steps) }
    var elapsed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<Int>()) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    LaunchedEffect(running) {
        val startAt = System.currentTimeMillis() - elapsed * 1000L
        while (running) {
            delay(200)
            val now = ((System.currentTimeMillis() - startAt) / 1000).toInt()
            if (now != elapsed) {
                val before = timeline.position(elapsed).stepIndex
                elapsed = now
                val pos = timeline.position(now)
                if (pos.stepIndex != before || pos.isFinished) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (pos.isFinished) running = false
            }
        }
    }
    val pos = timeline.position(elapsed)
    val listState = rememberLazyListState()
    LaunchedEffect(pos.stepIndex) { if (preset.usesTimer) listState.animateScrollToItem(pos.stepIndex) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar(preset.nameAr, onBack = onClose)
        lotName?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp)) }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            RecipeNumbers(preset)
            Text("الطحنة: ${preset.grindAr} · ${preset.brewer}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        if (preset.usesTimer) {
            Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                val oc = MaterialTheme.colorScheme.onPrimary
                Text(if (pos.isFinished) "خلصت! بالعافية ☕" else preset.steps[pos.stepIndex].kind.nameAr, color = oc, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(clock(elapsed), color = oc, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                if (!pos.isFinished) {
                    Text("باقي ${pos.secondsLeftInStep} ث على الخطوة", color = oc.copy(alpha = 0.85f))
                    pos.pourTo?.let { Text("صب لين الميزان يقرأ ${Methods.grams(it)}غ", color = oc, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                }
                LinearProgressIndicator(progress = { (elapsed.toFloat() / timeline.totalSeconds).coerceIn(0f, 1f) },
                    color = oc, trackColor = oc.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledIconButton(onClick = { if (pos.isFinished) { elapsed = 0 }; running = !running },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = oc, contentColor = MaterialTheme.colorScheme.primary), modifier = Modifier.size(60.dp)) {
                        Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (running) "إيقاف" else "ابدأ", Modifier.size(32.dp))
                    }
                    if (elapsed > 0) FilledIconButton(onClick = { running = false; elapsed = 0 },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = oc.copy(alpha = 0.25f), contentColor = oc), modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Filled.Refresh, "من جديد")
                    }
                }
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(preset.steps) { i, s ->
                val current = preset.usesTimer && !pos.isFinished && i == pos.stepIndex && elapsed > 0
                val done = if (preset.usesTimer) (pos.isFinished || i < pos.stepIndex) && elapsed > 0 else i in checked
                Card(onClick = { if (!preset.usesTimer) checked = if (i in checked) checked - i else checked + i },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (current) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                        Text(if (done) "✓" else "${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                        Column(Modifier.weight(1f)) {
                            val head = listOfNotNull(s.kind.nameAr, s.addedWaterGrams?.let { "+${Methods.grams(it)}غ" }, s.seconds?.let { Methods.clock(it) })
                            Text(head.joinToString(" · "), fontWeight = FontWeight.Bold)
                            Text(s.note, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun clock(s: Int) = String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
