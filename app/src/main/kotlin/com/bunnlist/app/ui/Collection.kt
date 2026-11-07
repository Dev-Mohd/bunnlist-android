package com.bunnlist.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.CollectionInsights
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.ListDraft
import com.bunnlist.core.ListSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Shelf(val nameAr: String, val matches: (CollectionItem) -> Boolean) {
    All("الكل", { true }), Owned("لديّ", { it.owned }), Tried("جرّبته", { it.tried }), Favorite("مفضّل", { it.favorite }), WantsToTry("ودّي أجرّب", { it.wantsToTry })
}

/** «مجموعتي», as on iOS: numbers, the passport, your taste, the shelf, lists, then the bags by shelf. */
@Composable
fun CollectionScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit) {
    if (!signedIn) { SignInPrompt("مجموعتك تنحفظ في حسابك", "سجّل الدخول وابدأ تجمع القهوات اللي جرّبتها والقهوات اللي ودّك تجرّبها.", onSignIn); return }
    var items by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(Shelf.All) }
    var query by remember { mutableStateOf("") }
    var countries by remember { mutableStateOf<Int?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        try { items = app.session.withToken { app.api.collection(it) } } catch (e: ApiError) { if (items == null) failed = true }
        if (countries == null) countries = runCatching { app.api.facets().origins.count { it.value != "XB" } }.getOrNull()
    }
    val all = items
    val visible = all.orEmpty().filter(shelf.matches).filter { i ->
        query.isBlank() || query.trim().split(" ").all { t -> "${i.nameAr} ${i.roasterNameAr} ${i.originNameAr ?: ""}".contains(t, ignoreCase = true) }
    }

    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        val full = GridItemSpan(2)
        item(span = { full }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مجموعتي", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp))
                Row(Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f).padding(start = 8.dp)) {
                        if (query.isEmpty()) Text("ابحث في مجموعتك", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        androidx.compose.foundation.text.BasicTextField(query, { query = it }, singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        when {
            all == null && failed -> item(span = { full }) { Retry("تعذّر تحميل مجموعتك") { attempt++ } }
            all == null -> {
                item(span = { full }) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { SkeletonBlock(Modifier.weight(1f).height(64.dp), 18.dp) } } }
                item(span = { full }) { SkeletonBlock(Modifier.fillMaxWidth().height(94.dp)) }
                items(4) { SkeletonBlock(Modifier.fillMaxWidth().height(240.dp)) }
            }
            else -> {
                if (shelf == Shelf.All && query.isEmpty() && all.isNotEmpty()) item(span = { full }) { Overview(all, countries, nav) }
                if (shelf == Shelf.All && query.isEmpty()) item(span = { full }) { ListsStrip(app, nav) }
                item(span = { full }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Shelf.entries.forEach { s ->
                            val on = shelf == s
                            Text("${s.nameAr} ${all.count(s.matches)}", fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface)
                                    .pressable { shelf = s }.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                    }
                }
                if (visible.isEmpty()) item(span = { full }) {
                    Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Coffee, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Text(if (query.isEmpty()) "ما فيه شي هنا للحين" else "ما لقينا شي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (query.isEmpty()) "امسح كيس أو دوّر قهوة وعلّمها بـ «لديّ» أو «مفضّل»." else "جرّب كلمة ثانية.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else items(visible, key = { it.lotId }) { CollectionCard(it) { nav.push(Route.Lot(it.slug)) } }
            }
        }
    }
}

@Composable
private fun Overview(items: List<CollectionItem>, catalogCountries: Int?, nav: Nav) {
    val insights = remember(items) { CollectionInsights(items) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(insights.coffees, "قهوة", Modifier.weight(1f))
            CountTile(insights.countryCodes.size, "دول", Modifier.weight(1f))
            CountTile(insights.roasters, "محامص", Modifier.weight(1f))
            CountTile(insights.favorites, "مفضّلة", Modifier.weight(1f))
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        TipAnchor(AppTip.Passport) { Passport(insights.countryCodes, maxOf(catalogCountries ?: 0, insights.countryCodes.size), shown) { Tips.done(ctx, AppTip.Passport); nav.push(Route.Passport) } }
        if (items.count { it.flavorNotes.isNotEmpty() } >= 3 && insights.shares.isNotEmpty()) TasteSpread(insights.shares, shown)
        val owned = items.filter { it.owned }
        if (items.size >= 4 && owned.isNotEmpty()) {
            ShelfTitle("على رفّي الحين", owned.size)
            WoodenShelf(owned.take(12), shown) { nav.push(Route.Lot(it)) }
        }
        val wish = items.filter { it.wantsToTry }
        if (items.size >= 4 && wish.isNotEmpty()) {
            ShelfTitle("ودّي أجرّب", wish.size)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wish.take(12), key = { "w" + it.lotId }) { i ->
                    Row(Modifier.width(186.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).pressable { nav.push(Route.Lot(i.slug)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LotPhotoWell(i.imageUrl, i.flavorNotes, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), 2.dp)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(i.nameAr, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text(i.roasterNameAr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** A number that counts up. */
@Composable
internal fun CountTile(value: Int, label: String, modifier: Modifier) {
    val still = (LocalStill.current || androidx.compose.ui.platform.LocalInspectionMode.current)
    var target by remember { mutableIntStateOf(if (still) value else 0) }
    LaunchedEffect(value) { delay(300); target = value }
    val shown by animateIntAsState(target, tween(1100), label = "count")
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$shown", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Passport(codes: List<String>, total: Int, shown: Boolean, onOpen: () -> Unit) {
    val p by animateFloatAsState(if (shown && total > 0) codes.size / total.toFloat() else 0f, tween(1400, delayMillis = 400), label = "passport")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                drawCircle(Brand.Gold.copy(alpha = 0.18f), style = Stroke(7.dp.toPx()))
                drawArc(Brand.Gold, -90f, 360f * p, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${codes.size}", fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 18.sp)
                if (total > 0) Text("من $total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 11.sp)
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("جواز قهوتك", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row {
                codes.take(9).forEachIndexed { i, code ->
                    val pop by animateFloatAsState(if (shown) 1f else 0.1f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "flag$i")
                    Text(com.bunnlist.core.RemoteLot(id = "", slug = "", name = "", nameAr = "", roasterNameAr = "", process = "", originCode = code).flag ?: "",
                        fontSize = 20.sp, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop })
                }
            }
            if (total > codes.size) Text("ختمك الجاي؟ في الدليل ${total - codes.size} دولة ما جرّبتها", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasteSpread(shares: List<CollectionInsights.Share>, shown: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("بصمة ذوقك", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
            var used = 0f
            shares.forEachIndexed { i, s ->
                val w by animateFloatAsState(if (shown) s.percent / 100f else 0f, spring(dampingRatio = 0.8f, stiffness = 60f), label = "share$i")
                used += w
                if (w > 0.001f) Box(Modifier.fillMaxHeight().weight(w).background(s.family.color()))
            }
            if (1f - used > 0.001f) Spacer(Modifier.weight(1f - used))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shares.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.family.color()))
                    Text(" ${s.family.shortNameAr} ${s.percent}٪", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun ShelfTitle(title: String, count: Int) {
    Row { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, fontSize = 17.sp, modifier = Modifier.weight(1f)); Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** The bags you have now, dropping onto a wooden shelf one by one. */
@Composable
private fun WoodenShelf(items: List<CollectionItem>, shown: Boolean, onOpen: (String) -> Unit) {
    Box {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            items(items.size, key = { "s" + items[it].lotId }) { idx ->
                val i = items[idx]
                val drop = remember { Animatable(if (shown) 0f else -70f) }
                LaunchedEffect(Unit) { delay(400L + minOf(idx, 8) * 100L); drop.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 250f)) }
                LotPhotoWell(i.imageUrl, i.flavorNotes, Modifier.graphicsLayer { translationY = drop.value * density; alpha = 1f + drop.value / 70f }
                    .size(70.dp, 92.dp).clip(RoundedCornerShape(10.dp)).pressable { onOpen(i.slug) }, 3.dp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFA77B4F), Color(0xFF7A5232)))))
    }
}

/** «قوائمي»: a new-list tile, then your lists. */
@Composable
private fun ListsStrip(app: BunnListApp, nav: Nav) {
    var lists by remember { mutableStateOf<List<ListSummary>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { lists = runCatching { app.session.withToken { app.api.myLists(it) } }.getOrDefault(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("قوائمي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val olive = MaterialTheme.colorScheme.primary
                Column(Modifier.width(124.dp).pressable { creating = true }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(124.dp).drawBehind {
                        drawRoundRect(olive.copy(alpha = 0.5f), cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 15f))))
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, tint = olive, modifier = Modifier.size(34.dp)) }
                    Text("قائمة جديدة", fontWeight = FontWeight.Bold, color = olive)
                    Text(if (lists.isNullOrEmpty()) "مثل «للضيوف»" else " ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(lists.orEmpty(), key = { it.id }) { l -> ListTile(l, 124) { nav.push(Route.ListPage(l.id)) } }
        }
    }
    if (creating) NewListDialog(app, onDismiss = { creating = false }) { creating = false; reload++ }
}

@Composable
fun ListTile(list: ListSummary, size: Int, onClick: () -> Unit) {
    Column(Modifier.width(size.dp).pressable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(20.dp)).background(Brand.Gold.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (list.covers.isEmpty()) Text(list.emoji ?: "☕", fontSize = (size * 0.32).sp)
            else Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.covers.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { url -> AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.size(((size - 18) / 2).dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.7f))) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(list.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (!list.isPublic) Icon(Icons.Filled.Lock, "خاصة", Modifier.padding(start = 4.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(list.countLine, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewListDialog(app: BunnListApp, onDismiss: () -> Unit, onCreated: (ListSummary) -> Unit) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("☕") }
    var isPublic by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("قائمة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(ListDraft.MAX_TITLE) }, placeholder = { Text("مثل «للضيوف»") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ListDraft.emojis.forEach { e ->
                        Text(e, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).background(if (e == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .pressable { emoji = e }.padding(6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("يشوفها الناس", modifier = Modifier.weight(1f)); Switch(isPublic, { isPublic = it }) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    try { onCreated(app.session.withToken { app.api.createList(title, emoji, isPublic, it) }) }
                    catch (e: ApiError) { error = "ما انحفظت، جرّب مرة ثانية." }
                    busy = false
                }
            }) { Text("أنشئ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

/** A list's page: its coffees. */
@Composable
fun ListScreen(app: BunnListApp, id: String, nav: Nav) {
    var page by remember { mutableStateOf<com.bunnlist.core.ListPage?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        try { page = app.api.list(id, if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null) } catch (e: ApiError) { failed = true }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar(page?.list?.let { "${it.emoji ?: ""} ${it.title}".trim() } ?: "", onBack = { nav.pop() })
        val p = page
        when {
            p == null && failed -> Retry("ما قدرنا نجيب القائمة") { reload++ }
            p == null -> Loading()
            else -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Text("من ${p.owner.name} · ${p.list.countLine}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        p.list.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (p.lots.isEmpty()) item(span = { GridItemSpan(2) }) { Empty("القائمة فاضية", "افتح أي قهوة واضغط «أضف لقائمة».") }
                items(p.lots, key = { it.lot.id }) { e -> DiscoverLotCard(e.lot) { nav.push(Route.Lot(e.lot.slug)) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CollectionCard(item: CollectionItem, onClick: () -> Unit) {
    val shelves = buildList {
        if (item.owned) add(Icons.Filled.CheckCircle to "لديّ"); if (item.tried) add(Icons.Filled.Coffee to "جرّبته")
        if (item.favorite) add(Icons.Filled.Favorite to "مفضّل"); if (item.wantsToTry) add(Icons.Filled.Bookmark to "ودّي أجرّب")
    }
    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onClick).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        LotPhotoWell(item.imageUrl, emptyList(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)), 4.dp)
        Spacer(Modifier.height(4.dp))
        Text(item.nameAr, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.roasterNameAr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        listOfNotNull(item.flag, item.originNameAr).joinToString(" ").takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
            shelves.forEach { (icon, label) ->
                Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(" $label", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SignInPrompt(title: String, body: String, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("☕", fontSize = 52.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onSignIn, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)) { Text("سجّل الدخول") }
        }
    }
}
