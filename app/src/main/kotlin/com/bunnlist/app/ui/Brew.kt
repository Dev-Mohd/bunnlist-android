package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewTaste
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.Experience
import com.bunnlist.core.Methods
import com.bunnlist.core.TasteFix

/**
 * «حضّر», as on iOS BrewView: which coffee (from your collection), the method, then the recipe — yours first,
 * then the community's for this coffee, then BunnList's starting ones — with last time's note, and «ابدأ التحضير».
 */
@Composable
fun BrewScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit, focusSlug: String?, initialMethod: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var method by remember(initialMethod) { mutableStateOf(initialMethod?.takeIf { it in BrewPreset.methods } ?: prefs.getString("method", "V60") ?: "V60") }
    var coffees by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember(focusSlug) { mutableStateOf(focusSlug) }
    var plans by remember { mutableStateOf<List<BrewPreset>?>(null) }
    var planId by remember { mutableStateOf<String?>(null) }
    var memory by remember { mutableStateOf<Experience?>(null) }
    val recent = remember { (prefs.getString("recent", "") ?: "").split(",").filter { it.isNotEmpty() } }
    LaunchedEffect(signedIn, nav.version) {
        if (!signedIn) return@LaunchedEffect
        try {
            val items = app.session.withToken { app.api.collection(it) }.filter { it.owned || it.tried }
            coffees = items.sortedBy { recent.indexOf(it.slug).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            if (selected == null) selected = coffees?.firstOrNull()?.slug
        } catch (e: Exception) { failed = true }
    }
    LaunchedEffect(method) { prefs.edit().putString("method", method).apply() }
    LaunchedEffect(selected, method) {
        val slug = selected ?: return@LaunchedEffect
        plans = null; memory = null
        val token = runCatching { app.session.accessToken() }.getOrNull()
        val mine = token?.let { t -> runCatching { app.api.myRecipes(t) }.getOrDefault(emptyList()) }.orEmpty()
            .filter { it.methodCode == method && it.state != "deleted" }.sortedBy { if (it.lot?.slug == slug) 0 else 1 }.take(3)
        val community = runCatching { app.api.exploreRecipes(slug, token) }.getOrDefault(emptyList())
            .filter { !it.isMine && it.methodCode == method }.sortedByDescending { it.likeCount ?: 0 }.take(3)
        plans = mine.map { it.toPreset() } + community.map { it.toPreset() } + BrewPreset.presets(method)
        planId = plans?.firstOrNull()?.id
        memory = token?.let { t -> runCatching { app.api.myExperiences(slug, t) }.getOrNull() }
            ?.filter { it.methodCode == method && it.log != null && it.state != "deleted" }?.maxByOrNull { it.brewedAt }
    }
    val coffee = coffees?.firstOrNull { it.slug == selected }
    val plan = plans?.firstOrNull { it.id == planId } ?: plans?.firstOrNull()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 100.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("حضّر", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (!signedIn) {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("حضّر قهوتك خطوة بخطوة", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("سجّل دخولك عشان نجيب قهوات مجموعتك ووصفاتك، ونحفظ تجاربك.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PillButton("سجّل الدخول", filled = true, onClick = onSignIn)
                }
                MethodSection(method) { method = it }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("وصفات البداية", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    BrewPreset.presets(method).forEach { p -> PlanCard(p, selected = false) { nav.push(Route.Brewing(p, null, null)) } }
                }
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("وش بتحضّر؟", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 16.dp))
                val list = coffees
                when {
                    list == null && failed -> Text("ما قدرنا نجيب مجموعتك.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    list == null -> Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBlock(Modifier.size(112.dp, 150.dp), 16.dp) } }
                    list.isEmpty() -> Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("مجموعتك فاضية. أضف القهوة اللي عندك وابدأ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { nav.openTab(Tab.Discover) }) { Icon(Icons.Filled.Search, null); Text(" ابحث عنها") }
                    }
                    else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.lotId }) { item ->
                            val on = item.slug == selected
                            Column(Modifier.width(112.dp).graphicsLayer { val s = if (on) 1.03f else 1f; scaleX = s; scaleY = s }.pressable { selected = item.slug }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LotPhotoWell(item.imageUrl, emptyList(), Modifier.size(112.dp).clip(RoundedCornerShape(16.dp))
                                    .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier), 4.dp,
                                    roaster = item.roasterNameAr, name = item.nameAr, origin = item.originNameAr, originCode = item.originCode)
                                Text(item.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Text(if (recent.firstOrNull() == item.slug) "آخر وحدة حضّرتها" else item.roasterNameAr, fontSize = 11.sp, maxLines = 1,
                                    color = if (recent.firstOrNull() == item.slug) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            val olive = MaterialTheme.colorScheme.primary
                            Column(Modifier.size(112.dp).drawBehind { drawRoundRect(olive.copy(alpha = 0.4f), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 12f)))) }
                                .pressable { nav.openTab(Tab.Discover) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.Add, null, tint = olive); Text("قهوة ثانية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = olive)
                            }
                        }
                    }
                }
            }
            if (coffee != null) {
                MethodSection(method) { method = it }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    memory?.let { LastBrewCard(it) }
                    Text("الوصفة", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    val ps = plans
                    if (ps == null) repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(110.dp), 18.dp) }
                    else ps.forEach { p -> PlanCard(p, selected = p.id == plan?.id) { planId = p.id } }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolButton("الوصفات", Icons.AutoMirrored.Filled.MenuBook, Modifier.weight(1f)) { nav.push(Route.Recipes(coffee?.slug, coffee?.nameAr)) }
                ToolButton("محوّل الطحن", Icons.Filled.Tune, Modifier.weight(1f)) { nav.push(Route.GrindConverter) }
            }
        }
        if (signedIn && plan != null && coffee != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomInset.current + 8.dp).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp).shadow(12.dp, CircleShape, ambientColor = Brand.Olive.copy(alpha = 0.3f), spotColor = Brand.Olive.copy(alpha = 0.3f))
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary).pressable {
                        prefs.edit().putString("recent", (listOf(coffee.slug) + recent.filter { it != coffee.slug }).take(20).joinToString(",")).apply()
                        nav.push(Route.Brewing(plan, coffee.nameAr, coffee.slug))
                    }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(" ابدأ التحضير", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun MethodSection(method: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الطريقة", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrewPreset.methods.forEach { m ->
                val on = m == method
                Text(Methods.nameAr(m), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface).pressable { onPick(m) }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** «آخر مرة»: last brew of this coffee and method, and the one change to try. */
@Composable
private fun LastBrewCard(e: Experience) {
    val taste = e.log?.taste?.let { t -> BrewTaste.entries.firstOrNull { it.code == t } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Gold.copy(alpha = 0.12f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, null, tint = Brand.Gold, modifier = Modifier.size(18.dp))
            Text("  آخر مرة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("★".repeat(e.stars), color = Brand.Gold)
        }
        Text(listOfNotNull(e.log?.recipeTitle, e.log?.brewSeconds?.let { Methods.clock(it) }, taste?.nameAr).joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        taste?.let { t -> val fix = TasteFix.suggest(t, e.methodCode, e.log?.doseGrams, e.log?.waterGrams); Text("المرة الجاية: ${fix.titleAr}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
    }
}

@Composable
internal fun PlanCard(p: BrewPreset, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier).pressable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(p.sourceAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        RecipeNumbers(p)
        if (p.grindAr.isNotBlank()) Text("الطحنة: ${p.grindAr}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(" $title", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun RecipeNumbers(p: BrewPreset) {
    val items = listOfNotNull(
        p.doseGrams.takeIf { it > 0 }?.let { "${Methods.grams(it)}غ" to "بن" },
        p.hotWaterGrams?.let { "${Methods.grams(it)}غ" to "ماء" },
        p.iceGrams?.let { "${Methods.grams(it)}غ" to "ثلج" },
        p.yieldGrams?.let { "${Methods.grams(it)}غ" to "الناتج" },
        p.waterTempC.takeIf { it > 0 }?.let { "${Methods.grams(it)}°" to "الحرارة" },
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

/** «محوّل الطحن»: a click count on one grinder, in microns and on the others (approximate). */
@Composable
fun GrindConverterScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var grinderId by remember { mutableStateOf(prefs.getString("grinder", null)?.takeIf { id -> com.bunnlist.core.Grinder.convertible.any { it.id == id } } ?: com.bunnlist.core.Grinder.convertible.first().id) }
    var clicks by remember { mutableIntStateOf(24) }
    var menu by remember { mutableStateOf(false) }
    val g = com.bunnlist.core.Grinder.convertible.first { it.id == grinderId }
    LaunchedEffect(grinderId) { prefs.edit().putString("grinder", grinderId).apply() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("محوّل الطحن", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مطحنتك", fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(g.name) }
                    DropdownMenu(menu, { menu = false }) { com.bunnlist.core.Grinder.convertible.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { menu = false; grinderId = c.id }) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الإعداد", modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (clicks > 1) clicks-- }) { Icon(Icons.Filled.Remove, "أقل") }
                    Text("$clicks كليك", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (clicks < 120) clicks++ }) { Icon(Icons.Filled.Add, "أكثر") }
                }
            }
            g.microns(clicks.toDouble())?.let { m ->
                val level = com.bunnlist.core.GrindLevel.of(m)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text("بالميكرون (تقريبي)", modifier = Modifier.weight(1f)); Text("≈ $m", fontWeight = FontWeight.Bold) }
                    Row { Text("الدرجة", modifier = Modifier.weight(1f)); Text(level.nameAr, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    Text("تناسب عادة: ${level.usedForAr}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("نفس الطحنة على مطاحن ثانية", fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp)) {
                    g.equivalents(clicks.toDouble()).forEach { (o, c) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { Text(o.name, modifier = Modifier.weight(1f)); Text("≈ $c كليك", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            Text("تحويل تقريبي محسوب من الصفر (التصاق الشفرات) وما ينشره كل مصنّع. ابدأ منه واضبط بالتذوق.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
