package com.bunnlist.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.BrowseFilters
import com.bunnlist.core.DiscoverRows
import com.bunnlist.core.Facet
import com.bunnlist.core.Facets
import com.bunnlist.core.ForYou
import com.bunnlist.core.RemoteLot
import com.bunnlist.core.RowLot
import kotlinx.coroutines.delay

/**
 * «اكتشف». Browsing: one main card that grows with you (the advisor for someone new, today's pick once
 * we know your taste), a row from your roasters (or what just arrived), then every coffee. Searching:
 * the cards step aside and filters take their place.
 */
@Composable
fun DiscoverScreen(app: BunnListApp, nav: Nav, state: DiscoverState, signedIn: Boolean, onSelect: (String) -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var forYou by remember { mutableStateOf<ForYou?>(null) }
    var arrivals by remember { mutableStateOf<List<RowLot>>(emptyList()) }
    var facets by remember { mutableStateOf<Facets?>(null) }
    var sheet by remember { mutableStateOf<FacetKind?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    suspend fun token() = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null

    val key = state.query.trim() + "|" + state.filters.params()
    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true; failed = false
        try {
            val page = app.api.browse(state.query, limit = 30, offset = if (reset) 0 else state.lots.size, accessToken = token(), filters = state.filters)
            state.total = page.total
            state.lots = if (reset) page.lots else state.lots + page.lots
            state.loadedKey = key
        } catch (e: ApiError) { failed = true } finally { loading = false }
    }
    LaunchedEffect(key, retry) {
        if (state.loadedKey == key && state.lots.isNotEmpty()) return@LaunchedEffect // came back: keep results and scroll
        delay(if (state.query.isEmpty()) 0 else 350)
        load(reset = true)
        if (state.loadedKey == key) state.listState.scrollToItem(0)
    }
    LaunchedEffect(signedIn) {
        forYou = if (signedIn) runCatching { app.session.withToken { app.api.forYou(it) } }.getOrNull() else null
        if (forYou?.roasterNews.isNullOrEmpty()) arrivals = runCatching {
            app.api.browse(limit = 10, filters = BrowseFilters(sort = "newest")).lots.map { RowLot(it.slug, it.nameAr, it.roasterNameAr, it.imageUrl) }
        }.getOrDefault(emptyList())
    }
    LaunchedEffect(state.inSearchMode) { if (state.inSearchMode && facets == null) facets = runCatching { app.api.facets() }.getOrNull() }
    val nearEnd by remember { derivedStateOf { (state.listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.listState.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.lots.size) { if (nearEnd && state.lots.isNotEmpty() && state.lots.size < state.total) load(reset = false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        if (!state.inSearchMode) Text("قهوتك الجاية، هنا.", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (state.inSearchMode) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(state.query, { state.query = it }, placeholder = { Text("ابحث عن محصول أو محمصة") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { state.query = "" }) { Icon(Icons.Filled.Close, "امسح") }
                    else IconButton(onClick = { scanning = true }) { Icon(Icons.Filled.DocumentScanner, "امسح كيس القهوة", tint = MaterialTheme.colorScheme.primary) }
                },
                shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) state.searching = true })
            if (state.inSearchMode) TextButton(onClick = { state.exitSearch(); focus.clearFocus() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") }
        }
        if (state.inSearchMode) FilterChips(state.filters, facets) { sheet = it }

        LazyColumn(state = state.listState, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            if (!state.inSearchMode) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    val pick = forYou?.dailyPick?.takeIf { it.isPersonal }
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        if (pick != null) DailyPickCard(pick, onOpen = { onSelect(pick.lot.slug) }, onAdvisor = { nav.push(Route.Advisor) })
                        else AdvisorHero { nav.push(Route.Advisor) }
                    }
                }
                val roasterRow = DiscoverRows.fromRoasters(forYou?.roasterNews.orEmpty())
                val row = roasterRow.ifEmpty { arrivals }
                if (row.isNotEmpty()) item {
                    SectionHeader(if (roasterRow.isNotEmpty()) "جديد من محامصك" else "وصل حديثًا")
                    LotStrip(row, onSelect)
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("كل المحاصيل", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (state.total > 0) Text("  ${state.total}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { state.searching = true; sheet = FacetKind.Roaster }) { Icon(Icons.Filled.FilterList, "تصفية", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            } else item {
                Text(if (loading && state.lots.isEmpty()) "نبحث…" else "${state.total} نتيجة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
            when {
                failed && state.lots.isEmpty() -> item { Retry("ما قدرنا نجيب القهوات") { state.loadedKey = null; retry++ } }
                state.lots.isEmpty() && loading -> item { Loading() }
                state.lots.isEmpty() && state.inSearchMode -> item { Empty("ما لقينا شي", "جرّب كلمة ثانية أو شيل بعض الفلاتر.") }
                else -> items(state.lots, key = { it.id }) { lot ->
                    Box(Modifier.padding(horizontal = 16.dp)) { LotRow(lot, selected = state.selected == lot.slug, onClick = { focus.clearFocus(); onSelect(lot.slug) }) }
                }
            }
            if (loading && state.lots.isNotEmpty()) item { Loading() }
        }
    }

    sheet?.let { kind ->
        FacetSheet(kind, facets, state.filters, onDismiss = { sheet = null }) { state.filters = it }
        LaunchedEffect(Unit) { if (facets == null) facets = runCatching { app.api.facets() }.getOrNull() }
    }
    if (scanning) ScanBagSheet(app, signedIn, onDismiss = { scanning = false }, onOpen = { scanning = false; onSelect(it) })
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp))
}

/** Two cards and part of a third, so it reads as something you can swipe. */
@Composable
private fun LotStrip(items: List<RowLot>, onSelect: (String) -> Unit) {
    val screen = LocalConfiguration.current.screenWidthDp.coerceAtMost(600)
    val w = ((screen - 32) / 2.35f).dp
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.slug }) { l ->
            Column(Modifier.width(w).clip(RoundedCornerShape(18.dp)).clickable { onSelect(l.slug) }) {
                AsyncImage(l.imageUrl, l.nameAr, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Brand.Cream))
                Text(l.nameAr, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(l.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AdvisorHero(onStart: () -> Unit) {
    Card(onClick = onStart, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val on = MaterialTheme.colorScheme.onPrimary
                Text("ساعدني أختار", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = on)
                Text("جاوب كم سؤال ونطلع لك قهوتك", fontSize = 14.sp, color = on.copy(alpha = 0.85f))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = on, contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("يلا نبدأ", fontWeight = FontWeight.Bold) }
            }
            val pulse = rememberInfiniteTransition(label = "pulse")
            val tilt by pulse.animateFloat(-6f, 6f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "tilt")
            val glow by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
            Box(contentAlignment = Alignment.Center) {
                Text("✨", fontSize = 20.sp, modifier = Modifier.offset((-26).dp, (-24).dp).alpha(glow))
                Text("☕", fontSize = 52.sp, modifier = Modifier.rotate(tilt))
                Text("✨", fontSize = 15.sp, modifier = Modifier.offset(24.dp, 22.dp).alpha(1.3f - glow))
            }
        }
    }
}

/** «قهوة اليوم لك»: the bag, why it fits, tinted by the bag itself. */
@Composable
private fun DailyPickCard(pick: ForYou.DailyPick, onOpen: () -> Unit, onAdvisor: () -> Unit) {
    val tint = rememberBagColor(pick.lot.imageUrl) ?: MaterialTheme.colorScheme.primary
    Card(onClick = onOpen, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ قهوة اليوم لك", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                AssistChip(onClick = onAdvisor, label = { Text("ساعدني أختار") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(pick.lot.imageUrl, pick.lot.nameAr, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)).background(Brand.Cream))
                Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(pick.lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(pick.lot.roasterNameAr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (pick.reason.isNotBlank()) Text(pick.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    pick.score?.let { Text("توافق $it٪", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary) }
                }
            }
        }
    }
}

enum class FacetKind(val nameAr: String) { Roaster("المحمصة"), Origin("المنشأ"), Process("المعالجة") }

private fun BrowseFilters.of(kind: FacetKind) = when (kind) { FacetKind.Roaster -> roasters; FacetKind.Origin -> origins; FacetKind.Process -> processes }
private fun BrowseFilters.with(kind: FacetKind, v: Set<String>) = when (kind) {
    FacetKind.Roaster -> copy(roasters = v); FacetKind.Origin -> copy(origins = v); FacetKind.Process -> copy(processes = v)
}
private fun Facets.of(kind: FacetKind) = when (kind) { FacetKind.Roaster -> roasters; FacetKind.Origin -> origins; FacetKind.Process -> processes }

@Composable
private fun FilterChips(filters: BrowseFilters, facets: Facets?, onOpen: (FacetKind) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FacetKind.entries.forEach { k ->
            val chosen = filters.of(k)
            val label = when {
                chosen.isEmpty() -> k.nameAr
                chosen.size == 1 -> facets?.of(k)?.firstOrNull { it.value == chosen.first() }?.labelAr ?: k.nameAr
                else -> "${k.nameAr} (${chosen.size})"
            }
            FilterChip(selected = chosen.isNotEmpty(), onClick = { onOpen(k) }, label = { Text(label) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacetSheet(kind: FacetKind, facets: Facets?, filters: BrowseFilters, onDismiss: () -> Unit, onChange: (BrowseFilters) -> Unit) {
    var current by remember { mutableStateOf(kind) }
    var picked by remember(current) { mutableStateOf(filters.of(current)) }
    var find by remember(current) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { onChange(filters.with(current, picked)); onDismiss() }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FacetKind.entries.forEach { k ->
                    FilterChip(selected = current == k, onClick = { onChange(filters.with(current, picked)); current = k }, label = { Text(k.nameAr) })
                }
            }
            val all: List<Facet> = facets?.of(current).orEmpty()
            if (current == FacetKind.Roaster) OutlinedTextField(find, { find = it }, placeholder = { Text("ابحث عن محمصة") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp))
            val shown = if (find.isBlank()) all else all.filter { it.labelAr.contains(find.trim(), ignoreCase = true) }
            if (facets == null) Loading()
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.value }) { f ->
                    val on = f.value in picked
                    Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - f.value else picked + f.value }.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { picked = if (on) picked - f.value else picked + f.value })
                        Text(f.labelAr, modifier = Modifier.weight(1f))
                        Text("${f.count}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { picked = emptySet() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("امسح") }
                Button(onClick = { onChange(filters.with(current, picked)); onDismiss() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("اعرض النتائج") }
            }
        }
    }
}

/** A coffee in a list: photo, name, roaster, origin and notes in one row, to compare at a glance. */
@Composable
fun LotRow(lot: RemoteLot, selected: Boolean = false, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = lot.imageUrl, contentDescription = lot.nameAr, contentScale = ContentScale.Fit,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Cream))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lot.roasterNameAr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(listOfNotNull(lot.flag, lot.originNameAr).joinToString(" "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (lot.hasRatings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text(" ${String.format(java.util.Locale.ROOT, "%.1f", lot.averageRating)} (${lot.reviewCount})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (lot.flavorNotes.isNotEmpty()) {
                    Text(lot.flavorNotes.take(3).joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
