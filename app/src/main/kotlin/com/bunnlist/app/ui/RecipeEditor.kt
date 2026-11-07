package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewStep
import com.bunnlist.core.Methods
import com.bunnlist.core.RecipeDraft
import com.bunnlist.core.StepKind
import kotlinx.coroutines.launch

/** Write or edit a recipe: start from a proven one, change the numbers and pours, save as a draft or publish it. */
@Composable
fun RecipeEditorScreen(app: BunnListApp, initial: RecipeDraft, nav: Nav) {
    var d by remember { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var presetMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problems = d.problemsAr()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        TopBar(if (initial.title.isBlank() && initial.sourceId == null) "وصفة جديدة" else "عدّل الوصفة", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(d.title, { d = d.copy(title = it.take(80)) }, label = { Text("اسم الوصفة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("الطريقة", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrewPreset.methods.forEach { m -> FilterChip(selected = d.methodCode == m, onClick = { d = d.copy(methodCode = m) }, label = { Text(Methods.nameAr(m)) }) }
            }
            Box {
                OutlinedButton(onClick = { presetMenu = true }) { Text("ابدأ من وصفة جاهزة") }
                DropdownMenu(presetMenu, { presetMenu = false }) {
                    BrewPreset.presets(d.methodCode).forEach { p ->
                        DropdownMenuItem(text = { Text(p.nameAr) }, onClick = { presetMenu = false; d = RecipeDraft.from(p, d.lotSlug).copy(clientId = d.clientId, title = d.title, notes = d.notes, sourceId = d.sourceId, publish = d.publish) })
                    }
                }
            }
            if (d.methodCode == "V60" || d.methodCode == "COLD_BREW") Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بارد (على ثلج)", modifier = Modifier.weight(1f)); Switch(d.iced, { d = d.copy(iced = it) })
            }
            Card("الأرقام") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("البن (غ)", d.doseGrams, Modifier.weight(1f)) { d = d.copy(doseGrams = it) }
                    if (d.isEspresso) NumField("الناتج (غ)", d.yieldGrams, Modifier.weight(1f)) { d = d.copy(yieldGrams = it) }
                    else NumField("الماء الحار (غ)", d.hotWaterGrams, Modifier.weight(1f)) { d = d.copy(hotWaterGrams = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.iced) NumField("الثلج (غ)", d.iceGrams, Modifier.weight(1f)) { d = d.copy(iceGrams = it) }
                    NumField("الحرارة (°م)", d.waterTempC, Modifier.weight(1f)) { d = d.copy(waterTempC = it) }
                }
                val water = (if (d.isEspresso) d.yieldGrams else (d.hotWaterGrams ?: 0.0) + (d.iceGrams ?: 0.0)) ?: 0.0
                if ((d.doseGrams ?: 0.0) > 0 && water > 0) Text("النسبة 1:${Methods.grams(Math.round(water / d.doseGrams!! * 10) / 10.0)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Card("الطحن والأدوات") {
                OutlinedTextField(d.grindSetting, { d = d.copy(grindSetting = it.take(60)) }, label = { Text("درجة الطحن") }, placeholder = { Text("مثل 24 أو متوسط ناعم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.grinder, { d = d.copy(grinder = it.take(60)) }, label = { Text("المطحنة") }, placeholder = { Text("مثل Comandante") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.brewer, { d = d.copy(brewer = it.take(40)) }, label = { Text("الأداة") }, placeholder = { Text("مثل V60 02") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Card("الخطوات") {
                if (!d.isEspresso && (d.hotWaterGrams ?: 0.0) > 0) Text("صبّيت ${Methods.grams(d.pouredGrams)} من ${Methods.grams(d.hotWaterGrams!!)}غ",
                    color = if (Math.abs(d.pouredGrams - d.hotWaterGrams!!) <= 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 13.sp)
                d.steps.forEachIndexed { i, s ->
                    StepEditor(i, s, isFirst = i == 0, isLast = i == d.steps.lastIndex,
                        onChange = { n -> d = d.copy(steps = d.steps.toMutableList().also { it[i] = n }) },
                        onMove = { dir -> val j = i + dir; d = d.copy(steps = d.steps.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }) },
                        onDelete = { d = d.copy(steps = d.steps.toMutableList().also { it.removeAt(i) }) })
                }
                if (d.steps.size < 20) TextButton(onClick = { d = d.copy(steps = d.steps + BrewStep(StepKind.Pour, null, 30, "")) }) { Icon(Icons.Filled.Add, null); Text(" أضف خطوة") }
            }
            OutlinedTextField(d.notes, { d = d.copy(notes = it.take(2000)) }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("انشرها للناس", fontWeight = FontWeight.SemiBold); Text("تطلع في «وصفات هذه القهوة» ويقدر الناس يحضّرون بها", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(d.publish, { d = d.copy(publish = it) })
            }
            if (d.publish && problems.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Gold.copy(alpha = 0.14f)).padding(12.dp)) {
                Text("عشان تنشرها:", fontWeight = FontWeight.Bold); problems.forEach { Text("• $it", fontSize = 13.sp) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving && d.title.isNotBlank() && !(d.publish && problems.isNotEmpty()), onClick = {
                saving = true; error = null
                scope.launch {
                    try { app.session.withToken { app.api.saveRecipe(d, it) }; nav.pop() }
                    catch (e: ApiError) { error = when (e) { is ApiError.Unprocessable -> "في الوصفة شي ناقص، راجعها."; ApiError.Forbidden -> "حسابك موقوف عن النشر حاليًا."; else -> "ما انحفظت، جرّب مرة ثانية." } }
                    saving = false
                }
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)) {
                Text(if (saving) "نحفظ…" else if (d.publish) "احفظ وانشر" else "احفظ كمسودة", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold); content()
    }
}

@Composable
private fun NumField(label: String, value: Double?, modifier: Modifier, onChange: (Double?) -> Unit) {
    var text by remember(value == null) { mutableStateOf(value?.let { Methods.grams(it) } ?: "") }
    OutlinedTextField(text, { t ->
        val clean = t.replace('٫', '.').replace(',', '.').filter { it.isDigit() || it == '.' }.let { s -> s.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("") }
        text = clean; onChange(clean.toDoubleOrNull())
    }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

@Composable
private fun StepEditor(index: Int, s: BrewStep, isFirst: Boolean, isLast: Boolean, onChange: (BrewStep) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    var kindMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
            Box {
                AssistChip(onClick = { kindMenu = true }, label = { Text(s.kind.nameAr) })
                DropdownMenu(kindMenu, { kindMenu = false }) {
                    StepKind.entries.forEach { k -> DropdownMenuItem(text = { Text(k.nameAr) }, onClick = { kindMenu = false; onChange(s.copy(kind = k, addedWaterGrams = if (k.addsWater) s.addedWaterGrams else null)) }) }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = !isFirst, onClick = { onMove(-1) }) { Icon(Icons.Filled.ArrowUpward, "فوق") }
            IconButton(enabled = !isLast, onClick = { onMove(1) }) { Icon(Icons.Filled.ArrowDownward, "تحت") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "احذف", tint = MaterialTheme.colorScheme.error) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.kind.addsWater) NumField("ماء (غ)", s.addedWaterGrams, Modifier.weight(1f)) { onChange(s.copy(addedWaterGrams = it)) }
            NumField("ثواني", s.seconds?.toDouble(), Modifier.weight(1f)) { onChange(s.copy(seconds = it?.toInt())) }
        }
        OutlinedTextField(s.note, { onChange(s.copy(note = it.take(300))) }, placeholder = { Text("وش تسوي في هالخطوة؟") }, modifier = Modifier.fillMaxWidth())
    }
}
