package com.bunnlist.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.*
import kotlinx.coroutines.launch

/**
 * Write or edit a recipe in three steps (iOS ServerRecipeEditor): the tool (a barista recipe loads for it),
 * tuning (the dose drives the water and pours, strength, grind, steps), then the name, coffee and sharing.
 * Simple and pro walk the same steps; pro shows more inside them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(app: BunnListApp, initial: RecipeDraft, nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var d by remember { mutableStateOf(initial) }
    var pro by remember { mutableStateOf(prefs.getBoolean("recipeProMode", false)) }
    var page by remember { mutableStateOf(if (initial.isNew && initial.title.isBlank() && initial.sourceId == null) RecipeWizardStep.Tool else RecipeWizardStep.Tune) }
    val canTogglePublish = initial.existingState != "published" && initial.existingState != "unpublished"
    var publish by remember { mutableStateOf(initial.existingState == "published") }
    var pickingCoffee by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf<List<CollectionItem>>(emptyList()) }
    var lastRecipe by remember { mutableStateOf<RemoteRecipe?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!app.session.isSignedIn) return@LaunchedEffect
        if (d.isNew) lastRecipe = runCatching { app.session.withToken { app.api.myRecipes(it) } }.getOrNull()?.firstOrNull { it.state != "deleted" }
        collection = runCatching { app.session.withToken { app.api.collection(it) } }.getOrDefault(emptyList())
    }
    val blocking: String? = when {
        page == RecipeWizardStep.Finish && d.title.isBlank() -> "اكتب اسم للوصفة"
        publish && canTogglePublish -> d.problemsAr().firstOrNull()
        else -> null
    }

    fun save(thenTry: Boolean) {
        busy = true; error = null
        scope.launch {
            try {
                val saved = app.session.withToken { app.api.saveRecipe(d.copy(publish = publish && canTogglePublish), it) }
                nav.pop()
                if (thenTry) nav.push(Route.Brewing(saved.toPreset(), d.lotName, d.lotSlug))
            } catch (e: ApiError) {
                error = when (e) {
                    ApiError.Unauthorized -> "انتهت الجلسة. سجّل الدخول من جديد."
                    is ApiError.Unprocessable -> "الوصفة غير مكتملة للنشر. راجع الماء والخطوات."
                    ApiError.Conflict -> "هذه الوصفة حُذفت ولا يمكن تعديلها."
                    else -> "تعذّر حفظ الوصفة. تحقّق من اتصالك."
                }
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (page.ordinal > 0) page = RecipeWizardStep.entries[page.ordinal - 1] else nav.pop() }) {
                Text(if (page.ordinal > 0) "رجوع" else "إلغاء")
            }
            Text(if (d.isNew) "إنشاء وصفة" else "تعديل الوصفة", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(72.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Progress and the step's title.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecipeWizardStep.entries.forEach { st ->
                        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape)
                            .background(if (st.ordinal <= page.ordinal) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            .clickable { if (st.ordinal < page.ordinal || !d.isNew) page = st })
                    }
                }
                Text(page.titleAr, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text(page.subtitleAr, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "بسيط", true to "متقدم").forEachIndexed { i, (v, l) ->
                    SegmentedButton(selected = pro == v, onClick = { pro = v; prefs.edit().putBoolean("recipeProMode", v).apply() },
                        shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(l) }
                }
            }
            AnimatedContent(page, label = "page", transitionSpec = { fadeIn() togetherWith fadeOut() }) { p ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (p) {
                        RecipeWizardStep.Tool -> ToolPage(d, pro, lastRecipe, onChange = { d = it }, onFromLast = { r ->
                            d = RecipeDraft.from(r, copy = true).copy(lotSlug = d.lotSlug ?: r.lot?.slug, lotName = d.lotName ?: r.lot?.nameAr, title = "")
                            page = RecipeWizardStep.Tune
                        })
                        RecipeWizardStep.Tune -> TunePage(d, pro, onChange = { d = it })
                        RecipeWizardStep.Finish -> FinishPage(d, canTogglePublish, publish, onPublish = { publish = it }, onChange = { d = it }, onPickCoffee = { pickingCoffee = true })
                    }
                }
            }
        }
        // Bottom bar: «التالي», then save.
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page != RecipeWizardStep.Finish) {
                BigButton(if (page == RecipeWizardStep.Tune) "مراجعة الوصفة" else "التالي", dark = true, enabled = true) {
                    val next = RecipeWizardStep.entries[page.ordinal + 1]
                    if (next == RecipeWizardStep.Finish && d.title.isBlank()) d = d.copy(title = d.suggestedTitle)
                    page = next
                }
            } else {
                (error ?: blocking)?.let { Text(it, fontSize = 12.sp, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { BigButton(if (publish && canTogglePublish) "حفظ ونشر" else "حفظ", dark = false, enabled = blocking == null && !busy) { save(false) } }
                    Box(Modifier.weight(1f)) { BigButton(if (busy) "نحفظ…" else "▶ احفظ وجرّبها", dark = true, enabled = blocking == null && !busy) { save(true) } }
                }
            }
        }
    }

    if (pickingCoffee) CoffeePickerSheet(collection, d.lotSlug, onDismiss = { pickingCoffee = false }) { item ->
        d = d.copy(lotSlug = item.slug, lotName = item.nameAr); pickingCoffee = false
    }
}

// MARK: Pages

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolPage(d: RecipeDraft, pro: Boolean, last: RemoteRecipe?, onChange: (RecipeDraft) -> Unit, onFromLast: (RemoteRecipe) -> Unit) {
    fun pickTool(code: String) = onChange(if (d.isNew) d.start(code, d.iced) else d.copy(methodCode = code))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RecipeWizard.tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val on = d.methodCode == t.code
                    Column(Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, if (on) MaterialTheme.colorScheme.onBackground else Color.Transparent, RoundedCornerShape(18.dp))
                        .pressable { pickTool(t.code) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(toolIcon(t.code), null, tint = Brand.Gold)
                        Text(Methods.nameAr(t.code).let { if (t.code == "OTHER") "أخرى" else it }, fontWeight = FontWeight.Bold)
                        Text(t.blurb, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (d.methodCode !in listOf("ESPRESSO", "ARABIC", "COLD_BREW")) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "ساخن", true to "مثلج").forEachIndexed { i, (v, l) ->
                SegmentedButton(selected = d.iced == v, onClick = { onChange(if (d.isNew) d.start(d.methodCode, v) else d.setTemperature(v)) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(l) }
            }
        }
    }
    if (d.isNew && last != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).pressable { onFromLast(last) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("أو ابدأ من آخر وصفة لك", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(last.title.ifBlank { Methods.nameAr(last.methodCode) }, fontWeight = FontWeight.Bold, color = Brand.Olive, fontSize = 14.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (pro) {
        if (d.isNew) EditorCard("ابدأ من وصفة باريستا") {
            val shown = BrewPreset.presets(d.methodCode).ifEmpty { BrewPreset.all }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { p ->
                    Column(Modifier.width(190.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .pressable { onChange(d.apply(p)) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text((if (p.iced) "❄ " else "🔥 ") + p.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(p.summaryAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الطحن: ${p.grindAr}", fontSize = 11.sp, color = Brand.Olive)
                    }
                }
            }
            Hint("تختار وصفة وتعدّل عليها بذوقك. اسمك وقهوتك يبقون كما هم.")
        }
        EditorCard("الأداة") {
            Field("الأداة") {
                TextIn(d.brewer, "مثال: V60 02") { onChange(d.copy(brewer = it)) }
                Chips(RecipeWizard.brewers(d.methodCode), { it }, { d.brewer == it }) { onChange(d.copy(brewer = it)) }
            }
            if (!d.isEspresso) Field("الفلتر") {
                Chips(FilterType.entries, { it.nameAr }, { d.filterType == it }) { onChange(d.copy(filterType = if (d.filterType == it) null else it)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TunePage(d: RecipeDraft, pro: Boolean, onChange: (RecipeDraft) -> Unit) {
    // The basics: coffee, water and its temperature (which may stay «غير محددة»).
    BasicsCard(d, onChange)
    // Strength.
    EditorCard("القوة") {
        if (d.iced && !d.isEspresso) Field("الثلج (غ)") {
            NumIn(d.iceGrams, "100") { v -> val r = d.ratio; val nd = d.copy(iceGrams = v); onChange(if (r != null) nd.applyRatio(r) else nd) }
        }
        val range = if (d.isEspresso) 1f..4f else 10f..20f
        val r = (d.ratio ?: if (d.isEspresso) 2.0 else 16.7).toFloat().coerceIn(range.start, range.endInclusive)
        Row(verticalAlignment = Alignment.Bottom) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("1:${Methods.grams(Math.round(r * 10) / 10.0)}", fontSize = 38.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Text("  " + if (d.isEspresso) "إسبريسو" else Methods.nameAr(d.methodCode), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(bottom = 8.dp))
            Text("← ${Methods.grams(d.outputGrams ?: 0.0)}غ", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(value = r, onValueChange = { onChange(d.applyRatio(Math.round(it * 10) / 10.0)) }, valueRange = range,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onBackground, activeTrackColor = MaterialTheme.colorScheme.onBackground))
            Row { Text("قوي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.weight(1f)); Text("خفيف", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    // Grind.
    EditorCard("الطحن") {
        Field("درجة الطحن") {
            Chips(GrindLevel.entries, { it.nameAr }, { d.grindLevel == it }) { onChange(d.copy(grindLevel = it, grindMicrons = null)) }
            d.grindLevel?.let { Hint("تناسب عادة: ${it.usedForAr}") }
        }
        if (pro) {
            Field("المطحنة") {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Grinder.all.firstOrNull { it.id == d.grinderModel }?.name ?: d.grinder.ifBlank { "اختر مطحنتك" })
                    }
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text("بدون") }, onClick = { open = false; onChange(d.copy(grinderModel = null, grinder = "", grindMicrons = null)) })
                        Grinder.all.forEach { g -> DropdownMenuItem(text = { Text(g.name) }, onClick = { open = false; onChange(d.setGrind(g.id, d.grindSetting)) }) }
                        DropdownMenuItem(text = { Text("أخرى") }, onClick = { open = false; onChange(d.copy(grinderModel = null, grindMicrons = null, grinder = " ")) })
                    }
                }
                if (d.grinderModel == null && d.grinder.isNotEmpty()) TextIn(d.grinder.trim(), "اسم المطحنة") { onChange(d.copy(grinder = it)) }
            }
            Field("الإعداد (كليك / رقم)") {
                TextIn(d.grindSetting, "مثال: 24", KeyboardType.Decimal) { onChange(if (d.grinderModel != null) d.setGrind(d.grinderModel, it) else d.copy(grindSetting = it)) }
            }
            d.grindMicrons?.let {
                Text("≈ $it ميكرون · ${GrindLevel.of(it).nameAr}", fontSize = 12.sp, color = Brand.Olive)
                Hint("أي أحد يقرأ وصفتك بيشوف الدرجة محوّلة لمطحنته (تقريبيًا).")
            }
        }
    }
    // Steps: the simple editor (how many pours, bloom, the timeline) or the advanced one (a row per step).
    if (!d.isEspresso) {
        if (pro) AdvancedStepsCard(d, onChange) else SimpleStepsCard(d, onChange)
        WaterCheck(d, onChange)
    }
    // Espresso with milk.
    if (d.isEspresso) EditorCard("مشروب بالحليب") {
        Chips(listOf<MilkDrink?>(null) + MilkDrink.entries, { it?.nameAr ?: "شوت فقط" }, { d.milkDrink == it }) { onChange(d.setMilkDrink(it)) }
        d.milkDrink?.let { drink ->
            Text(drink.textureAr, fontSize = 12.sp, color = Brand.Olive)
            Field("الحليب") { Chips(MilkType.entries, { it.nameAr }, { d.milkType == it }) { onChange(d.copy(milkType = if (d.milkType == it) null else it)) } }
            Field("كمية الحليب (غ)") { NumIn(d.milkGrams, Methods.grams(drink.typicalMilkGrams)) { onChange(d.copy(milkGrams = it)) } }
            Hint("بخّر الحليب إلى ٥٥–٦٥°م؛ أسخن من كذا يفقد حلاوته.")
        }
    }
    // Barista tips.
    RecipeWizard.tips(d).takeIf { it.isNotEmpty() }?.let { tips ->
        EditorCard("نصائح الباريستا") {
            tips.forEach { tip -> Row { Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp), tint = Brand.Gold); Text("  $tip", fontSize = 14.sp) } }
        }
    }
    if (pro) {
        EditorCard("البن") {
            Field("درجة التحميص") {
                Chips(RoastLevel.entries, { it.nameAr }, { d.roastLevel == it }) { onChange(d.copy(roastLevel = if (d.roastLevel == it) null else it)) }
                d.roastLevel?.takeIf { d.waterTempC != it.suggestedTempC && d.methodCode != "COLD_BREW" }?.let { roast ->
                    TextButton(onClick = { onChange(d.copy(waterTempC = roast.suggestedTempC)) }) { Text("استخدم الحرارة المقترحة ${Methods.grams(roast.suggestedTempC)}°م", fontSize = 12.sp) }
                }
            }
            Field("عمر البن من التحميص") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Filled.Remove, "أقل", small = true) { onChange(d.copy(restDays = ((d.restDays ?: 0) - 1).takeIf { it > 0 })) }
                    Text(d.restDays?.let { "$it يوم" } ?: "غير محدد", modifier = Modifier.padding(horizontal = 12.dp))
                    RoundIcon(Icons.Filled.Add, "أكثر", small = true) { onChange(d.copy(restDays = minOf((d.restDays ?: 0) + 1, 90))) }
                }
                Hint(if (d.isEspresso) "الأفضل للإسبريسو ١٠–٤٠ يوم." else "الأفضل للفلتر ٧–٣٠ يوم.")
            }
        }
        EditorCard("الماء") {
            Field("نوع الماء") { Chips(WaterType.entries, { it.nameAr }, { d.waterType == it }) { onChange(d.copy(waterType = if (d.waterType == it) null else it)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { Field(if (d.waterType == WaterType.MineralRecipe) "الوصفة" else "الماركة") { TextIn(d.waterBrand, "مثال: نوفا") { onChange(d.copy(waterBrand = it)) } } }
                Box(Modifier.weight(1f)) { Field("TDS (ppm)") { NumIn(d.waterTdsPpm?.toDouble(), "110") { onChange(d.copy(waterTdsPpm = it?.toInt())) } } }
            }
            Hint("المكتوب على العبوة غالبًا «الأملاح الذائبة الكلية». المثالي للقهوة ٧٥–١٥٠ ppm.")
        }
        EditorCard("النتيجة (اختياري)") {
            val ty = RecipeWizard.typicalTds(d.methodCode)
            Field("TDS المشروب (٪)") { NumIn(d.beverageTds, if (d.isEspresso) "9.5" else "1.35") { onChange(d.copy(beverageTds = it)) } }
            Hint("المعتاد ${ty.start}–${ty.endInclusive}٪. لمن عنده مقياس انكسار (Refractometer).")
            d.extractionYield?.let { ey ->
                Row { Text("نسبة الاستخلاص", modifier = Modifier.weight(1f)); Text("${String.format(java.util.Locale.ROOT, "%.1f", ey)}٪", fontWeight = FontWeight.Bold,
                    color = if (ey in 18.0..22.0) Brand.Olive else Color(0xFFE08A1E)) }
            }
        }
    }
}

@Composable
private fun FinishPage(d: RecipeDraft, canTogglePublish: Boolean, publish: Boolean, onPublish: (Boolean) -> Unit, onChange: (RecipeDraft) -> Unit, onPickCoffee: () -> Unit) {
    // «مراجعة الوصفة»: what to prepare and the steps as they'll show while brewing; then name, coffee and publishing.
    RecipeReview(d)
    WaterCheck(d, onChange)
    EditorCard("معلومات الوصفة") {
        Field("اسم الوصفة") { TextIn(d.title, d.suggestedTitle) { onChange(d.copy(title = it.take(80))) } }
        Field("ملاحظات") { TextIn(d.notes, "ملاحظات اختيارية عن الوصفة…", minLines = 3) { onChange(d.copy(notes = it)) } }
    }
    EditorCard("القهوة") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = d.lotSlug != null, onClick = onPickCoffee, label = { Text("من مجموعتي") })
            FilterChip(selected = d.lotSlug == null, onClick = { onChange(d.copy(lotSlug = null, lotName = null)) }, label = { Text("عامة") })
        }
        if (d.lotSlug != null) {
            Text("🌿 ${d.lotName ?: d.lotSlug}", color = Brand.Olive, fontSize = 14.sp)
            TextButton(onClick = onPickCoffee) { Text("تغيير القهوة", fontSize = 13.sp) }
        } else Hint("وصفة عامة تصلح لأي قهوة.")
    }
    if (canTogglePublish) EditorCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("نشر في الملف الشخصي", fontWeight = FontWeight.Bold)
                Text("شارك هذه الوصفة في «استكشف» ليجرّبها غيرك.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(publish, onPublish)
        }
    }
}

// MARK: Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoffeePickerSheet(items: List<CollectionItem>, selected: String?, onDismiss: () -> Unit, onPick: (CollectionItem) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("اختر قهوة", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (items.isEmpty()) Text("مجموعتك فاضية. أضف قهوة من «اكتشف» أولًا.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(items.size) { i ->
                    val it = items[i]
                    Row(Modifier.fillMaxWidth().clickable { onPick(it) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(it.nameAr); Text(it.roasterNameAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (it.slug == selected) Icon(Icons.Filled.Check, null, tint = Brand.Olive)
                    }
                }
            }
        }
    }
}

// MARK: Pieces

private fun toolIcon(code: String): ImageVector = when (code) {
    "V60" -> Icons.Filled.WaterDrop; "ESPRESSO" -> Icons.Filled.Coffee; "AEROPRESS" -> Icons.Filled.Compress; "CHEMEX" -> Icons.Filled.HourglassEmpty
    "FRENCH_PRESS" -> Icons.Filled.LocalCafe; "COLD_BREW" -> Icons.Filled.AcUnit; "ARABIC" -> Icons.Filled.LocalFireDepartment; else -> Icons.Filled.MoreHoriz
}

private fun mmss(s: Int) = String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)

@Composable
private fun EditorCard(title: String?, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            trailing?.let { Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp)); Text(" $it", fontFamily = FontFamily.Monospace, fontSize = 14.sp) }
        }
        content()
    }
}

@Composable
private fun Field(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = selected(item)
            Text(label(item), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background)
                    .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    .clickable { onClick(item) }.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun TextIn(value: String, placeholder: String, type: KeyboardType = KeyboardType.Text, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, placeholder = { Text(placeholder) }, singleLine = minLines == 1, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = type), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
}

/** A number field: empty or 0 clears it. */
@Composable
private fun NumIn(value: Double?, placeholder: String, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(value?.let { Methods.grams(it) } ?: "") }
    // Follow outside changes (the dose rescaling the water, a preset) without fighting the typing ("1." stays "1.").
    LaunchedEffect(value) { if (text.replace(',', '.').toDoubleOrNull() != value) text = value?.let { Methods.grams(it) } ?: "" }
    OutlinedTextField(text, { t -> text = t; onChange(t.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }) }, placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, small: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(if (small) 34.dp else 44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(if (small) 18.dp else 22.dp)) }
}

@Composable
private fun BigButton(text: String, dark: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (!enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else if (dark) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(bg).then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) {
        Text(text, fontWeight = FontWeight.Bold, color = if (dark && enabled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface)
    }
}
