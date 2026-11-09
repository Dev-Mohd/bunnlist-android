package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// The recipe editor's step parts (approved mockup): the basics (coffee, water, temperature), the simple editor
// (how many pours, bloom, a draggable timeline), the advanced editor (one row per step, details inline, drag to
// reorder, «＋ صبّة» and «＋ إضافة خطوة»), the water check, and «مراجعة الوصفة».

private fun mmss(s: Int) = String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
private fun g(v: Double) = Methods.grams(v)

@Composable
private fun StepCard(title: String?, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); trailing?.invoke()
        }
        content()
    }
}

/** − value + in one row; the value can be typed by tapping it. */
@Composable
internal fun Stepper(value: String, minusLabel: String, plusLabel: String, onMinus: () -> Unit, onPlus: () -> Unit, size: Dp = 28.dp, onTapValue: (() -> Unit)? = null) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniRound(Icons.Filled.Remove, minusLabel, size, onMinus)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 52.dp).then(if (onTapValue != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTapValue) else Modifier))
            MiniRound(Icons.Filled.Add, plusLabel, size, onPlus)
        }
    }
}

@Composable
private fun MiniRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.background).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(size * 0.6f), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Typing a number: "0:50" for times (seconds), grams or degrees otherwise. */
@Composable
internal fun NumberDialog(title: String, initial: String, time: Boolean = false, onDismiss: () -> Unit, onDone: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(if (time) "0:50" else "") },
                keyboardOptions = KeyboardOptions(keyboardType = if (time) KeyboardType.Text else KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
        },
        confirmButton = {
            TextButton(onClick = {
                val v = if (time) text.split(':').let { p -> if (p.size == 2) (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0) else text.toIntOrNull() }?.toDouble()
                    else text.replace(',', '.').toDoubleOrNull()
                if (v != null) onDone(v); onDismiss()
            }) { Text("تم") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

// MARK: Basics

/** Coffee, water and water temperature side by side; the temperature may be «غير محددة». */
@Composable
internal fun BasicsCard(d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    var typing by remember { mutableStateOf<String?>(null) }
    val dose = d.doseGrams ?: if (d.isEspresso) 18.0 else 15.0
    val doseStep = if (d.isEspresso) 0.5 else 1.0
    StepCard(null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicBox("البن", "${g(dose)}غ", Modifier.weight(1f), { onChange(d.setDose(maxOf(dose - doseStep, 1.0))) }, { onChange(d.setDose(minOf(dose + doseStep, 1000.0))) }) { typing = "dose" }
            if (d.isEspresso) BasicBox("الشوت", "${g(d.yieldGrams ?: 36.0)}غ", Modifier.weight(1f),
                { onChange(d.copy(yieldGrams = maxOf((d.yieldGrams ?: 36.0) - 1, 1.0))) }, { onChange(d.copy(yieldGrams = (d.yieldGrams ?: 36.0) + 1)) }) { typing = "yield" }
            else BasicBox("الماء", "${g(d.hotWaterGrams ?: 0.0)}غ", Modifier.weight(1f),
                { onChange(d.setHotWater(maxOf((d.hotWaterGrams ?: 0.0) - 10, 10.0))) }, { onChange(d.setHotWater((d.hotWaterGrams ?: 0.0) + 10)) }) { typing = "water" }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicBox("حرارة الماء", d.waterTempC?.let { "${g(it)}°" } ?: "غير محددة", Modifier.fillMaxWidth(),
                    { onChange(d.copy(waterTempC = ((d.waterTempC ?: 93.0) - 1).coerceAtLeast(1.0))) },
                    { onChange(d.copy(waterTempC = ((d.waterTempC ?: 91.0) + 1).coerceAtMost(100.0))) }) { typing = "temp" }
                Text(if (d.waterTempC == null) "حدّدها" else "غير محددة؟", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Tone.gold,
                    modifier = Modifier.padding(top = 4.dp).clip(CircleShape).clickable { onChange(d.copy(waterTempC = if (d.waterTempC == null) 93.0 else null)) }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        val ratio = d.ratio?.let { "النسبة 1:${g(Math.round(it * 10) / 10.0)}" }
        Text(listOfNotNull(ratio, "الحرارة لكل الصبّات".takeIf { d.waterTempC != null && !d.isEspresso }).joinToString(" · "),
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Tone.olive, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    when (typing) {
        "dose" -> NumberDialog("البن (غ)", g(dose), onDismiss = { typing = null }) { if (it > 0) onChange(d.setDose(it)) }
        "water" -> NumberDialog("الماء (غ)", g(d.hotWaterGrams ?: 0.0), onDismiss = { typing = null }) { if (it > 0) onChange(d.setHotWater(it)) }
        "yield" -> NumberDialog("الشوت (غ)", g(d.yieldGrams ?: 36.0), onDismiss = { typing = null }) { if (it > 0) onChange(d.copy(yieldGrams = it)) }
        "temp" -> NumberDialog("حرارة الماء (°م)", d.waterTempC?.let { g(it) } ?: "", onDismiss = { typing = null }) { onChange(d.copy(waterTempC = it.coerceIn(1.0, 100.0))) }
    }
}

@Composable
private fun BasicBox(label: String, value: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit, onTap: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = if (value.length > 6) 13.sp else 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTap))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniRound(Icons.Filled.Remove, "أقل", 24.dp, onMinus); MiniRound(Icons.Filled.Add, "أكثر", 24.dp, onPlus)
            }
        }
    }
}

// MARK: Simple

/** «كم صبّة؟», bloom, and the timeline: drag a pour to move its start, tap it to set its start and what it adds. */
@Composable
internal fun SimpleStepsCard(d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pours = d.pourCount
    var selected by remember { mutableStateOf<Int?>(null) }
    val firstTime = remember { Tips.load(context); !Tips.isSeen(AppTip.TimelineDrag) }
    StepCard("كم صبّة؟", trailing = {
        Text(if (d.hasBloom) "$pours ${if (pours == 1) "صبّة" else "صبّات"} بعد التشريب" else "$pours ${if (pours == 1) "صبّة" else "صبّات"}",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tone.olive)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n ->
                val on = n == pours
                Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                    .pressable { selected = null; onChange(d.simplePours(n, d.hasBloom)) }, contentAlignment = Alignment.Center) {
                    Text("$n", fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("تشريب في البداية", fontWeight = FontWeight.Bold)
                if (d.hasBloom) Text("${g(d.steps[0].addedWaterGrams ?: 0.0)}غ · تعدّله من نقطة «تشريب»", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(d.hasBloom, { on -> selected = null; onChange(d.simplePours(maxOf(pours, 1), on)) })
        }
    }
    StepCard("شكل وصفتك") {
        Timeline(d, selected, onSelect = { selected = it; Tips.done(context, AppTip.TimelineDrag) }, onChange = onChange)
        if (firstTime && selected == null) Text("اسحب أي نقطة تغيّر وقتها، واضغطها تعدّل كميتها", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Olive).padding(10.dp))
        selected?.takeIf { it in d.steps.indices }?.let { i ->
            val s = d.steps[i]
            val title = if (s.kind == StepKind.Bloom) "التشريب" else "صبّة ${d.steps.take(i + 1).count { it.kind == StepKind.Pour }}"
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (i > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$title · يبدأ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Stepper(mmss(d.startTimes[i]), "أبكر", "أبطأ", { onChange(d.setStart(i, d.startTimes[i] - 5)) }, { onChange(d.setStart(i, d.startTimes[i] + 5)) })
                } else Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("أضف", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Stepper("${g(s.addedWaterGrams ?: 0.0)}غ", "أقل", "أكثر", { onChange(d.setAdded(i, (s.addedWaterGrams ?: 0.0) - 5)) }, { onChange(d.setAdded(i, (s.addedWaterGrams ?: 0.0) + 5)) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("وقت الانتهاء المتوقع", fontSize = 12.sp, modifier = Modifier.weight(1f))
            val lastStart = d.startTimes.lastOrNull() ?: 0
            Stepper(mmss(d.totalSeconds), "أبكر", "أبطأ", { onChange(d.setFinish(maxOf(d.totalSeconds - 5, lastStart))) }, { onChange(d.setFinish(minOf(d.totalSeconds + 5, 3600))) })
        }
    }
}

@Composable
private fun Timeline(d: RecipeDraft, selected: Int?, onSelect: (Int) -> Unit, onChange: (RecipeDraft) -> Unit) {
    val total = maxOf(d.totalSeconds, 1)
    val current by rememberUpdatedState(d)
    BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val usable = widthPx - with(density) { 44.dp.toPx() }
        Box(Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
        d.waterIndices.forEach { i ->
            val s = d.steps[i]
            val x = d.startTimes[i] / total.toFloat() * usable
            val on = i == selected
            val bloom = s.kind == StepKind.Bloom
            val pourNo = d.steps.take(i + 1).count { it.kind == StepKind.Pour }
            if (on) Text("أضف ${g(s.addedWaterGrams ?: 0.0)}غ · حتى يصل الميزان ${g(d.cumulativeWater[i])}غ", fontSize = 11.sp, color = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopStart).offset { IntOffset((x - with(density) { 60.dp.toPx() }).roundToInt().coerceAtLeast(0), 0) }
                    .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onBackground).padding(horizontal = 8.dp, vertical = 4.dp))
            var acc by remember(i) { mutableFloatStateOf(0f) }
            Column(Modifier.align(Alignment.CenterStart).offset { IntOffset(x.roundToInt(), 0) }.padding(top = 16.dp)
                .pointerInput(i, usable) {
                    detectHorizontalDragGestures(onDragStart = { onSelect(i); acc = 0f }) { change, dx ->
                        change.consume()
                        if (i == 0) return@detectHorizontalDragGestures
                        // RTL: time grows to the left, so a leftward drag (negative dx) is later.
                        acc += -dx / usable * total
                        val whole = acc.toInt()
                        if (whole != 0) { acc -= whole; onChange(current.setStart(i, current.startTimes[i] + whole)) }
                    }
                }.clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = if (bloom) 50.dp else if (on) 34.dp else 28.dp, height = if (on) 34.dp else 28.dp).shadow(3.dp, CircleShape).clip(RoundedCornerShape(16.dp))
                    .background(if (bloom) Brand.Gold else Brand.Olive).then(if (on) Modifier.border(3.dp, Brand.Olive.copy(alpha = 0.35f), RoundedCornerShape(16.dp)) else Modifier),
                    contentAlignment = Alignment.Center) { Text(if (bloom) "تشريب" else "$pourNo", fontSize = if (bloom) 10.sp else 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text(mmss(d.startTimes[i]), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// MARK: Advanced

/** One row per step: start, what it adds and the scale reading, its temperature; tap for details; drag ≡ to reorder. */
@Composable
internal fun AdvancedStepsCard(d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    var expanded by remember { mutableStateOf<Int?>(null) }
    var undo by remember { mutableStateOf<RecipeDraft?>(null) }
    var typingStart by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val current by rememberUpdatedState(d)
    val rowPx = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); undo = null } }
    StepCard("الخطوات", trailing = { Text("ينتهي ${mmss(d.totalSeconds)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
        val starts = d.startTimes; val cum = d.cumulativeWater
        var pourNo = 0
        d.steps.forEachIndexed { i, s ->
            if (s.kind == StepKind.Pour) pourNo++
            val number = pourNo
            val isDragging = dragging == i
            Column(Modifier.fillMaxWidth().graphicsLayer { if (isDragging) { translationY = dragY; shadowElevation = 12f } }
                .clip(RoundedCornerShape(14.dp)).background(if (isDragging) MaterialTheme.colorScheme.surface else Color.Transparent)) {
                Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == i) null else i }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DragHandle, "انقل الخطوة", Modifier.size(22.dp).pointerInput(i) {
                        detectDragGestures(onDragStart = { dragging = i; dragY = 0f; expanded = null },
                            onDragEnd = { dragging = null; dragY = 0f }, onDragCancel = { dragging = null; dragY = 0f }) { change, amount ->
                            change.consume()
                            dragY += amount.y
                            val from = dragging ?: return@detectDragGestures
                            val to = when { dragY > rowPx * 0.6f -> from + 1; dragY < -rowPx * 0.6f -> from - 1; else -> from }
                            if (to != from && to in current.steps.indices) {
                                if (undo == null) undo = current
                                onChange(current.moveStep(from, to)); dragging = to; dragY -= (to - from) * rowPx
                            }
                        }
                    }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    StepDot(s, number)
                    Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.titleAr(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            when {
                                s.kind.addsWater -> Stepper("${g(s.addedWaterGrams ?: 0.0)}غ", "أقل", "أكثر", { onChange(d.setAdded(i, (s.addedWaterGrams ?: 0.0) - 5)) }, { onChange(d.setAdded(i, (s.addedWaterGrams ?: 0.0) + 5)) }, 26.dp)
                                s.addition != null -> Stepper("${g(s.addedWaterGrams ?: 0.0)}غ", "أقل", "أكثر",
                                    { onChange(d.copy(steps = d.steps.toMutableList().also { it[i] = s.copy(addedWaterGrams = maxOf((s.addedWaterGrams ?: 0.0) - 5, 0.0)) })) },
                                    { onChange(d.copy(steps = d.steps.toMutableList().also { it[i] = s.copy(addedWaterGrams = (s.addedWaterGrams ?: 0.0) + 5) })) }, 26.dp)
                                else -> Stepper("${s.seconds ?: 0} ث", "أقل", "أكثر",
                                    { onChange(d.copy(steps = d.steps.toMutableList().also { it[i] = s.copy(seconds = maxOf((s.seconds ?: 0) - 5, 0)) })) },
                                    { onChange(d.copy(steps = d.steps.toMutableList().also { it[i] = s.copy(seconds = (s.seconds ?: 0) + 5) })) }, 26.dp)
                            }
                        }
                        val sub = when {
                            s.addition != null -> "بعد التحضير" + (if (s.addition == "dilute") " · " + (ADDITION_TEMPS.firstOrNull { it.first == s.additionTemp }?.second ?: "حرارة الغرفة") else "")
                            s.kind.addsWater -> "أضف ${g(s.addedWaterGrams ?: 0.0)}غ · حتى ${g(cum[i])}غ" + (d.stepTemp(i)?.let { " · ${g(it)}°" } ?: "")
                            else -> "المدة ${s.seconds ?: 0} ث"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("يبدأ ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(mmss(starts[i]), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable(enabled = i > 0) { typingStart = i }.padding(horizontal = 4.dp))
                            Text("  $sub", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (expanded == i) StepDetails(d, i, onChange) { expanded = null; onChange(d.removeStep(i)) }
            }
            if (i < d.steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }
        undo?.let { before ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onBackground).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("انتقلت الخطوة، والأوقات انحسبت من جديد", fontSize = 12.sp, color = MaterialTheme.colorScheme.background, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(before); undo = null }) { Text("تراجع", color = Tone.gold, fontWeight = FontWeight.Bold) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Olive).pressable { onChange(d.addPour()) }, contentAlignment = Alignment.Center) {
                Text("＋ صبّة", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background).pressable { menu = true }, contentAlignment = Alignment.Center) {
                    Text("＋ إضافة خطوة", fontWeight = FontWeight.Bold)
                }
                DropdownMenu(menu, { menu = false }) {
                    ExtraStep.entries.forEach { e ->
                        DropdownMenuItem(text = { Column { Text(e.titleAr, fontWeight = FontWeight.SemiBold); Text(e.hintAr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            onClick = { menu = false; onChange(d.addStep(e)); expanded = d.steps.size })
                    }
                }
            }
        }
    }
    typingStart?.let { i ->
        NumberDialog("يبدأ عند", mmss(d.startTimes[i]), time = true, onDismiss = { typingStart = null }) { onChange(d.setStart(i, it.toInt())) }
    }
}

@Composable
private fun StepDot(s: BrewStep, pourNo: Int) {
    val water = s.kind.addsWater
    Box(Modifier.padding(start = 4.dp).size(28.dp).clip(CircleShape).background(when {
        s.kind == StepKind.Bloom -> Brand.Gold; water -> Brand.Olive; else -> MaterialTheme.colorScheme.background
    }), contentAlignment = Alignment.Center) {
        Text(when {
            s.kind == StepKind.Bloom -> "ت"; water -> "$pourNo"; s.addition == "ice" -> "🧊"; s.addition == "dilute" -> "💧"
            s.kind == StepKind.Wait -> "⏳"; s.kind == StepKind.Press -> "⬇"; s.kind == StepKind.Serve -> "☕"; else -> "⟳"
        }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (water) Color.White else MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDetails(d: RecipeDraft, i: Int, onChange: (RecipeDraft) -> Unit, onDelete: () -> Unit) {
    val s = d.steps[i]
    fun set(step: BrewStep) = onChange(d.copy(steps = d.steps.toMutableList().also { it[i] = step }))
    @Composable fun chip(label: String, on: Boolean, onClick: () -> Unit) = Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(if (on) Brand.Olive else MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp))
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.kind.addsWater) {
            Text("حرارة هالصبّة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chip(d.waterTempC?.let { "حرارة الوصفة ${g(it)}°" } ?: "حرارة الوصفة (غير محددة)", s.tempC == null) { set(s.copy(tempC = null)) }
                chip(s.tempC?.let { "مخصصة: ${g(it)}°" } ?: "مخصصة", s.tempC != null) { set(s.copy(tempC = s.tempC ?: d.waterTempC ?: 90.0)) }
                if (s.tempC != null) Stepper("${g(s.tempC!!)}°", "أبرد", "أحر", { set(s.copy(tempC = (s.tempC!! - 1).coerceAtLeast(1.0))) }, { set(s.copy(tempC = (s.tempC!! + 1).coerceAtMost(100.0))) }, 24.dp)
            }
            Text("أسلوب الصب", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PourStyle.entries.forEach { p -> chip(p.nameAr, s.pourStyle == p.code) { set(s.copy(pourStyle = if (s.pourStyle == p.code) null else p.code)) } } }
            Text("السرعة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PourSpeed.entries.forEach { p -> chip(p.nameAr, s.pourSpeed == p.code) { set(s.copy(pourSpeed = if (s.pourSpeed == p.code) null else p.code)) } } }
        }
        if (s.addition == "dilute") {
            Text("حرارة ماء التخفيف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ADDITION_TEMPS.forEach { (code, label) -> chip(label, (s.additionTemp ?: "room") == code) { set(s.copy(additionTemp = code)) } } }
        }
        Text("ملاحظة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(s.note, { set(s.copy(note = it.take(300))) }, placeholder = { Text("مثال: صب من النص للخارج ببطء") }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
        TextButton(onClick = onDelete) { Text("حذف الخطوة", color = MaterialTheme.colorScheme.error) }
    }
}

// MARK: Water check

/** «ماء الخطوات 240غ، والمستهدف 250غ · متبقّي 10غ», with a fix either way. Never blocks a draft. */
@Composable
internal fun WaterCheck(d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    if (!d.waterMismatch) return
    val diff = d.waterShortfall
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF6E6)).border(1.5.dp, Brand.Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ماء الخطوات ${g(d.pouredGrams)}غ، والمستهدف ${g(d.hotWaterGrams ?: 0.0)}غ", fontWeight = FontWeight.Bold, color = Tone.gold, fontSize = 13.sp)
        Text(if (diff > 0) "متبقّي ${g(diff)}غ" else "زايد ${g(-diff)}غ", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (diff > 0) "أضف ${g(diff)}غ لآخر صبّة" else "انقص ${g(-diff)}غ من آخر صبّة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Brand.Gold).clickable { onChange(d.giveShortfallToLastPour()) }.padding(horizontal = 10.dp, vertical = 6.dp))
            Text("اعتمد إجمالي ${g(d.pouredGrams)}غ", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable { onChange(d.adoptPouredTotal()) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

// MARK: Review

/** «مراجعة الوصفة»: what to prepare, the numbers, the three waters, and the steps as they'll show while brewing. */
@Composable
internal fun RecipeReview(d: RecipeDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        d.prepLineAr?.takeIf { !d.isEspresso || true }?.let { line ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onBackground).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("♨︎ ", fontSize = 22.sp, color = MaterialTheme.colorScheme.background)
                Column {
                    Text(line, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.background)
                    Text(d.customTempsAr?.let { "$it، والباقي على ${g(d.waterTempC!!)}°" } ?: "نفس الحرارة لكل الصبّات", fontSize = 11.sp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("${g(d.doseGrams ?: 0.0)}غ" to "بن", (d.ratio?.let { "1:${g(Math.round(it * 10) / 10.0)}" } ?: "—") to "النسبة",
                (if (d.totalSeconds > 0) mmss(d.totalSeconds) else "—") to "المدة المتوقعة").forEach { (v, k) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(v, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    Text(k, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!d.isEspresso) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
            @Composable fun line(k: String, v: String?) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(k, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(v ?: "بدون", fontSize = 13.sp, fontWeight = if (v != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (v != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            line("ماء التحضير", "${g(d.pouredGrams.takeIf { it > 0 } ?: d.hotWaterGrams ?: 0.0)}غ")
            val ice = (if (d.iced) d.iceGrams ?: 0.0 else 0.0) + d.iceAddedGrams
            line("ثلج", ice.takeIf { it > 0 }?.let { "${g(it)}غ" })
            val dil = d.steps.firstOrNull { it.addition == "dilute" }
            line("ماء للتخفيف", d.dilutionGrams.takeIf { it > 0 }?.let { "${g(it)}غ · " + (ADDITION_TEMPS.firstOrNull { p -> p.first == dil?.additionTemp }?.second ?: "حرارة الغرفة") })
        }
        if (d.steps.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 4.dp)) {
            val starts = d.startTimes; val cum = d.cumulativeWater
            d.steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mmss(starts[i]), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                    Text(s.titleAr(), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    val right = when {
                        s.addition == "ice" -> "${g(s.addedWaterGrams ?: 0.0)}غ ثلج"
                        s.addition == "dilute" -> "${g(s.addedWaterGrams ?: 0.0)}غ ماء " + (ADDITION_TEMPS.firstOrNull { it.first == s.additionTemp }?.second ?: "حرارة الغرفة")
                        s.kind.addsWater -> "أضف ${g(s.addedWaterGrams ?: 0.0)}غ · حتى ${g(cum[i])}غ" + (s.tempC?.takeIf { it != d.waterTempC }?.let { " · ${g(it)}°" } ?: "")
                        else -> "${s.seconds ?: 0} ث"
                    }
                    Text(right, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (i < d.steps.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }
        }
    }
}
