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
import com.bunnlist.core.BrewGroups
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.brewMinutes
import com.bunnlist.core.stepsFor
import com.bunnlist.core.suggestedMicrons
import com.bunnlist.core.BrewTaste
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.Experience
import androidx.compose.material.icons.filled.Coffee
import com.bunnlist.core.Methods
import com.bunnlist.core.TasteFix

private const val ANY_COFFEE = "__any__"

/**
 * «حضّر», as on iOS BrewView: which coffee (from your collection), the method, then the recipe from the tabs
 * (★ for this coffee: the roaster's and the community's; BunnList's starting and champion recipes; yours; the
 * community's), swiped between, with «كم كوب؟» scaling it, last time's note, and «ابدأ التحضير».
 */
@Composable
fun BrewScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit, focusSlug: String?, initialMethod: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brew", 0) }
    var method by remember(initialMethod) { mutableStateOf(initialMethod?.takeIf { it in BrewPreset.methods } ?: prefs.getString("method", "V60") ?: "V60") }
    var coffees by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember(focusSlug) { mutableStateOf(focusSlug) }
    var groups by remember { mutableStateOf<BrewGroups?>(null) }
    var tab by remember { mutableStateOf(BrewGroups.Tab.BunnList) }
    var planId by remember { mutableStateOf<String?>(null) }
    var cups by remember { mutableIntStateOf(1) }
    // Your grinder from «دليل المطاحن», for a suggested setting on each recipe.
    var guideGrinder by remember { mutableStateOf<com.bunnlist.core.GuideGrinder?>(null) }
    LaunchedEffect(nav.version) {
        val id = prefs.getString("guideGrinder", null) ?: return@LaunchedEffect
        guideGrinder = runCatching { app.api.grinders() }.getOrNull()?.firstOrNull { it.id == id }
    }
    var memory by remember { mutableStateOf<Experience?>(null) }
    val recent = remember { (prefs.getString("recent", "") ?: "").split(",").filter { it.isNotEmpty() } }
    LaunchedEffect(signedIn, nav.version) {
        if (!signedIn) return@LaunchedEffect
        try {
            val items = app.session.withToken { app.api.collection(it) }.filter { it.owned || it.tried }
            coffees = items.sortedBy { recent.indexOf(it.slug).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            // An empty collection still brews: «أي قهوة» with BunnList's recipes (and yours).
            if (selected == null) selected = coffees?.firstOrNull()?.slug ?: ANY_COFFEE
        } catch (e: Exception) { failed = true }
    }
    LaunchedEffect(method) { prefs.edit().putString("method", method).apply() }
    LaunchedEffect(selected, method) {
        val slug = selected ?: return@LaunchedEffect
        groups = null; memory = null
        val token = runCatching { app.session.accessToken() }.getOrNull()
        val mine = token?.let { t -> runCatching { app.api.myRecipes(t) }.getOrDefault(emptyList()) }.orEmpty()
        val any = slug == ANY_COFFEE
        val lotCommunity = if (any) emptyList() else runCatching { app.api.exploreRecipes(slug, token) }.getOrDefault(emptyList())
        val methodCommunity = runCatching { app.api.exploreRecipes(null, token) }.getOrDefault(emptyList())
        val detail = if (any) null else runCatching { app.api.lot(slug, token) }.getOrNull()
        val last = token?.takeIf { !any }?.let { t -> runCatching { app.api.myExperiences(slug, t) }.getOrNull() }
            ?.filter { it.methodCode == method && it.log != null && it.state != "deleted" }?.maxByOrNull { it.brewedAt }
        val g = BrewGroups.build(method, slug.takeIf { !any }, detail?.roasterNameAr ?: coffees?.firstOrNull { it.slug == slug }?.roasterNameAr ?: "المحمصة",
            detail?.roasterRecipes.orEmpty(), mine, lotCommunity, methodCommunity)
        // Last time's recipe leads again when it's still on offer; otherwise this coffee's own, else BunnList's.
        val lastId = last?.log?.recipeId?.let { id -> g.all.firstOrNull { it.id == id || it.recipeId == id }?.id }
        tab = g.tabOf(lastId) ?: g.defaultTab
        planId = lastId ?: g.plans(tab).firstOrNull()?.id
        cups = g.all.firstOrNull { it.id == planId }?.baseCups ?: 1
        groups = g
        memory = last
    }
    val coffee = coffees?.firstOrNull { it.slug == selected }
    /** Brewing a bag that isn't in the collection: nothing is logged against a coffee. */
    val isAny = selected == ANY_COFFEE
    // The selected recipe within the open tab; an empty tab (e.g. «وصفاتي») selects nothing, so no start button.
    val basePlan = groups?.let { g -> g.plans(tab).firstOrNull { it.id == planId } ?: g.plans(tab).firstOrNull() }
    /** What gets brewed: the selected recipe at the chosen cups. */
    val plan = basePlan?.scaled(cups)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 100.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (signedIn) "وش بتحضّر؟" else "حضّر", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                val list = coffees
                when {
                    list == null && failed -> Text("ما قدرنا نجيب مجموعتك.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    list == null -> Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBlock(Modifier.size(112.dp, 150.dp), 16.dp) } }
                    list.isEmpty() -> Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("مجموعتك فاضية. تقدر تحضّر على طول من تحت، أو أضف قهوتك عشان نحفظ تحضيراتك ونضبط لك الطحنة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Column(Modifier.width(112.dp).graphicsLayer { val s = if (isAny) 1.03f else 1f; scaleX = s; scaleY = s }.pressable { selected = ANY_COFFEE }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).background(if (isAny) olive else olive.copy(alpha = 0.1f)),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val on = if (isAny) MaterialTheme.colorScheme.onPrimary else olive
                                    Icon(Icons.Filled.Coffee, null, tint = on); Text("أي قهوة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = on)
                                }
                                Text("بدون اختيار", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Text("حضّر على طول", fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            val olive = MaterialTheme.colorScheme.primary
                            Column(Modifier.size(112.dp).drawBehind { drawRoundRect(olive.copy(alpha = 0.4f), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 12f)))) }
                                .pressable { nav.push(Route.AddCoffee) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.Add, null, tint = olive); Text("قهوة ثانية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = olive)
                            }
                        }
                    }
                }
            }
            if (coffee != null || isAny) {
                MethodSection(method) { method = it }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    memory?.let { Box(Modifier.padding(horizontal = 16.dp)) { LastBrewCard(it) } }
                    val g = groups
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("الوصفة", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        if ((g?.plans(tab)?.size ?: 0) > 1) Text("اسحب لوصفات ثانية ←", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    if (g == null) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(110.dp), 18.dp) }
                    }
                    else RecipePicker(g, tab, basePlan?.id, cups,
                        onTab = { t -> tab = t; g.plans(t).firstOrNull()?.let { planId = it.id; cups = it.baseCups } },
                        onPlan = { p -> if (p.id != planId) { planId = p.id; cups = p.baseCups } },
                        onCups = { cups = it },
                        onCreate = { nav.push(Route.RecipeEditor(com.bunnlist.core.RecipeDraft.from(BrewPreset.presets(method).firstOrNull() ?: BrewPreset.all.first(), coffee?.slug, coffee?.nameAr).copy(title = ""))) })
                    basePlan?.let { p ->
                        Box(Modifier.padding(horizontal = 16.dp)) { GrindCard(p, guideGrinder, onPickGrinder = { nav.push(Route.Grinders) }, onConverter = { nav.push(Route.GrindConverter) }) }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolButton("الوصفات", Icons.AutoMirrored.Filled.MenuBook, Modifier.weight(1f)) { nav.push(Route.Recipes(coffee?.slug, coffee?.nameAr)) }
                ToolButton("محوّل الطحن", Icons.Filled.Tune, Modifier.weight(1f)) { nav.push(Route.GrindConverter) }
            }
        }
        if (signedIn && plan != null && (coffee != null || isAny)) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            TipAnchor(AppTip.Brew, above = true, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomInset.current + 8.dp).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp).shadow(12.dp, CircleShape, ambientColor = Brand.Olive.copy(alpha = 0.3f), spotColor = Brand.Olive.copy(alpha = 0.3f))
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary).pressable {
                        if (coffee != null) prefs.edit().putString("recent", (listOf(coffee.slug) + recent.filter { it != coffee.slug }).take(20).joinToString(",")).apply()
                        Tips.done(ctx, AppTip.Brew)
                        nav.push(Route.Brewing(plan, coffee?.nameAr, coffee?.slug))
                    }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(" ابدأ التحضير", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** «طحنتك»: a suggested setting for this recipe on your grinder (clicks when it converts, else the level), adjustable. */
@Composable
internal fun GrindCard(p: BrewPreset, grinder: com.bunnlist.core.GuideGrinder?, onPickGrinder: () -> Unit, onConverter: () -> Unit) {
    val microns = p.suggestedMicrons
    val suggested = if (grinder != null && microns != null) grinder.stepsFor(microns) else null
    var setting by remember(p.id, grinder?.id) { mutableStateOf(suggested) }
    val unit = if (grinder?.adjustment == "stepped") "درجة" else "كليك"
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(grinder?.let { "طحنتك على ${it.brand} ${it.title}" } ?: "طحنتك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val s = setting
            if (s != null) Text("$s $unit" + if (s == suggested) "  · مقترحة لهالوصفة" else "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            else Text(com.bunnlist.core.suggestedGrindAr(p, grinder) ?: "اختر درجة الطحن", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("محوّل الطحن ←", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.pressable(onClick = onConverter))
                if (grinder == null) Text("اختر مطحنتك", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tone.gold, modifier = Modifier.pressable(onClick = onPickGrinder))
                else if (suggested == null) Text("مطحنتك ما تنشر مقاس الدرجة، فنقترح الدرجة بالكلام", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (setting != null) Stepper("", "أنعم", "أخشن", { setting = (setting ?: 0) - 1 }, { setting = (setting ?: 0) + 1 })
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
            Text("  آخر مرة", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("★".repeat(e.stars), color = Tone.gold)
        }
        Text(listOfNotNull(e.log?.recipeTitle, e.log?.brewSeconds?.let { Methods.clock(it) }, taste?.nameAr).joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        taste?.let { t -> val fix = TasteFix.suggest(t, e.methodCode, e.log?.doseGrams, e.log?.waterGrams); Text("المرة الجاية: ${fix.titleAr}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
    }
}

/** The tabs and the swipeable recipe cards (iOS recipeTabs/recipeCards); the card in view is the selected one. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun RecipePicker(g: BrewGroups, tab: BrewGroups.Tab, selectedId: String?, cups: Int,
                          onTab: (BrewGroups.Tab) -> Unit, onPlan: (BrewPreset) -> Unit, onCups: (Int) -> Unit, onCreate: (() -> Unit)? = null) {
    // «وصفاتي» always shows (with «＋ سوّ وصفة» when empty) so your recipes have a home here.
    val tabs = BrewGroups.Tab.entries.filter { it in g.tabs || (it == BrewGroups.Tab.Mine && onCreate != null) }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { t ->
            val on = t == tab
            val gold = t == BrewGroups.Tab.ForCoffee
            val ink = MaterialTheme.colorScheme.onBackground
            Text("${t.titleAr} ${g.plans(t).size}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (on) MaterialTheme.colorScheme.background else if (gold) Brand.Gold else ink,
                modifier = Modifier.clip(CircleShape).background(if (on) (if (gold) Brand.Gold else ink) else if (gold) Brand.Gold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                    .pressable { onTab(t) }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
    val options = g.plans(tab)
    if (options.isEmpty() && onCreate != null) { Box(Modifier.padding(horizontal = 16.dp)) { CreateRecipeCard(onCreate) }; return }
    // Each tab gets its own pager, opening on its selected card; swiping selects the card in view.
    key(tab) {
    val start = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val pager = androidx.compose.foundation.pager.rememberPagerState(start) { options.size }
    LaunchedEffect(pager.settledPage) { options.getOrNull(pager.settledPage)?.let(onPlan) }
    androidx.compose.foundation.pager.HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top) { page ->
        val p = options[page]
        val isSelected = p.id == selectedId
        PlanCard(if (isSelected) p.scaled(cups) else p, selected = isSelected, base = p, cups = if (isSelected) cups else p.baseCups, onCups = onCups) { onPlan(p) }
    }
    if (options.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        options.forEachIndexed { i, _ ->
            val on = i == pager.currentPage
            Box(Modifier.padding(horizontal = 2.5.dp).height(6.dp).width(if (on) 16.dp else 6.dp).clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
        }
    }
    }
}

@Composable
internal fun PlanCard(p: BrewPreset, selected: Boolean, base: BrewPreset = p, cups: Int = base.baseCups, onCups: ((Int) -> Unit)? = null, onClick: () -> Unit) {
    val context = LocalContext.current
    val accent = if (p.isGold) Brand.Gold else MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (p.isGold) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, Brand.Gold.copy(alpha = 0.08f)))
            else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surface))
        .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(18.dp)) else Modifier).pressable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = accent)
        }
        Text(p.sourceAr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (p.isGold || p.source == "community") Brand.Gold else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clip(CircleShape).background((if (p.isGold || p.source == "community") Brand.Gold else MaterialTheme.colorScheme.secondary).copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp))
        if (selected && onCups != null && base.scalesByCups) CupsRow(base, cups, onCups)
        RecipeNumbers(p)
        if (p.grindAr.isNotBlank()) Text("الطحنة: ${p.grindAr}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        p.sourceUrl?.let { url ->
            Text(if (p.advanced) "🔗 متقدمة · المصدر المنشور" else "🔗 المصدر المنشور", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Tone.gold,
                modifier = Modifier.pressable { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) })
        }
    }
}

/** «كم كوب؟»: coffee, water and every pour follow the count; the strength stays. */
@Composable
private fun CupsRow(base: BrewPreset, cups: Int, onCups: (Int) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (base.cupNameAr == "فنجان") "كم فنجان؟" else "كم كوب؟", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CupButton(Icons.Filled.Remove, "كوب أقل", cups > 1, filled = false) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onCups(cups - 1) }
                Text("$cups", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.widthIn(min = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                CupButton(Icons.Filled.Add, "كوب زيادة", cups < base.maxCups, filled = true) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onCups(cups + 1) }
            }
        }
    }
}

@Composable
private fun CupButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }.clip(CircleShape)
        .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
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
        p.totalSeconds.takeIf { it > 0 && p.usesTimer }?.let { brewMinutes(it) to "الوقت" },
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
