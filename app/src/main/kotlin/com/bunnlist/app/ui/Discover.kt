package com.bunnlist.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
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
import com.bunnlist.core.BrowseFilters
import com.bunnlist.core.Facet
import com.bunnlist.core.Facets
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.ForYou
import com.bunnlist.core.HomeText
import com.bunnlist.core.Me
import com.bunnlist.core.RemoteLot
import com.bunnlist.core.TrendingLot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * «اكتشف», section for section the iOS home: greeting and avatar, search with filters and scan, the advisor,
 * today's coffee, new from your roasters, trending, browse by taste, origins, roasters and map, new arrivals,
 * and the whole catalog. Searching swaps the home for results with filter chips.
 */
@Composable
fun DiscoverScreen(app: BunnListApp, nav: Nav, state: DiscoverState, signedIn: Boolean, onSelect: (String) -> Unit) {
    var facets by state.home::facets
    var sheet by remember { mutableStateOf<FacetKind?>(null) }
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (facets == null) facets = runCatching { app.api.facets() }.getOrNull() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.inSearchMode) SearchResults(app, state, signedIn, facets, onSelect, onFacet = { sheet = it }, onScan = { scanning = true },
            onSuggest = { q -> nav.push(Route.SuggestCoffee(q, "", null)) })
        else DiscoverHome(app, nav, state, signedIn, facets, onSelect,
            openSearch = { state.searching = true },
            openFilters = { state.searching = true; sheet = FacetKind.Roaster },
            openScan = { scanning = true })
    }
    sheet?.let { kind -> FacetSheet(kind, facets, state.filters, onDismiss = { sheet = null }) { state.filters = it } }
    if (scanning) ScanBagSheet(app, signedIn, onDismiss = { scanning = false }, onOpen = { scanning = false; onSelect(it) },
        onSuggest = { n, r, o -> scanning = false; nav.push(Route.SuggestCoffee(n, r, o)) })
}

@Composable
private fun DiscoverHome(
    app: BunnListApp, nav: Nav, state: DiscoverState, signedIn: Boolean, facets: Facets?, onSelect: (String) -> Unit,
    openSearch: () -> Unit, openFilters: () -> Unit, openScan: () -> Unit,
) {
    // Kept in DiscoverState so coming back to the tab shows the same page at the same scroll, refreshed quietly.
    val h = state.home
    var me by h::me
    var forYou by h::forYou
    var trending by h::trending
    var newest by h::newest
    var catalogTotal by h::catalogTotal
    var flavor by h::flavor
    var flavorLots by h::flavorLots
    var extras by h::extras
    var streak by h::streak
    LaunchedEffect(signedIn) {
        if (signedIn) runCatching { app.session.withToken { t -> me = app.api.me(t); forYou = app.api.forYou(t) } } else { me = null; forYou = null }
        if (signedIn) {
            extras = runCatching { app.session.withToken { app.api.forYouExtras(it) } }.getOrNull() ?: extras
            streak = runCatching { app.session.withToken { app.api.points(it) } }.getOrNull()?.streak ?: streak
        } else { extras = null; streak = 0 }
    }
    LaunchedEffect(Unit) {
        launch { trending = runCatching { app.api.trending(null) }.getOrDefault(trending) }
        runCatching { app.api.browse(limit = 60, filters = BrowseFilters(sort = "newest")) }.onSuccess { newest = it.lots; catalogTotal = it.total }.onFailure { if (newest == null) newest = emptyList() }
    }
    LaunchedEffect(flavor) {
        val f = flavor ?: return@LaunchedEffect
        if (h.flavorLotsFor == f && flavorLots != null) return@LaunchedEffect
        flavorLots = null
        flavorLots = runCatching { app.api.browse(limit = 10, filters = BrowseFilters(flavors = setOf(f.code))).lots }.getOrDefault(emptyList())
        h.flavorLotsFor = f
    }
    fun search(filters: BrowseFilters) { state.query = ""; state.filters = filters; state.searching = true }

    LazyColumn(state = state.listState, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Header(me, signedIn, onAccount = { nav.openTab(Tab.Account) }, streak = streak)
                SearchBar(openSearch, openFilters, openScan)
            }
        }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            TipAnchor(AppTip.Advisor) { AdvisorCard { Tips.done(ctx, AppTip.Advisor); nav.push(Route.Advisor()) } }
        }
        forYou?.dailyPick?.let { pick -> item { DailyPickCard(pick) { onSelect(pick.lot.slug) } } }
        extras?.resume?.takeIf { it.isNotEmpty() }?.let { list ->
            item { HomeSectionHeader("كمّل من حيث وقفت", "قهاوي عندك وما قيّمتها", null) {} }
            items(list.take(3), key = { "r" + it.lot.id }) { r ->
                Box(Modifier.padding(horizontal = 16.dp)) { ResumeCard(r, onOpen = { onSelect(r.lot.slug) }, onBrew = { nav.openBrewTab(null, r.lot.slug) }) }
            }
        }
        extras?.friendsDrinking?.takeIf { it.isNotEmpty() }?.let { list ->
            item { HomeSectionHeader("اللي تتابعهم يشربون", "جرّبوها هالأسبوع", null) {} }
            items(list.take(3), key = { "f" + it.lot.id }) { f ->
                Box(Modifier.padding(horizontal = 16.dp)) { FriendsDrinkingCard(f) { onSelect(f.lot.slug) } }
            }
        }
        forYou?.roasterNews?.takeIf { it.isNotEmpty() }?.let { news -> item { RoasterNewsRow(news, onSelect) } }
        if (trending.isNotEmpty()) item { TrendingRow(trending, onSelect, showAll = { nav.openTab(Tab.Community) }) }
        item {
            TasteSection(facets, flavor, flavorLots, onPick = { flavor = if (flavor == it) null else it }, onSelect = onSelect,
                showAll = { search(BrowseFilters(flavors = setOf(it.code))) })
        }
        facets?.origins?.let { origins -> item { OriginsRow(origins) { search(BrowseFilters(origins = setOf(it))) } } }
        item { ExploreTiles(openRoasters = { nav.push(Route.Roasters) }, openMap = { nav.push(Route.OriginsMap) }) }
        item {
            HomeSectionHeader("وصلت حديثًا", "آخر قهوة من كل محمصة", "عرض الكل") { search(BrowseFilters(sort = "newest")) }
        }
        val arrivals = newest?.let { HomeText.onePerRoaster(it, 6) }
        if (arrivals == null) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(2) { SkeletonBlock(Modifier.weight(1f).height(230.dp)) } } }
            }
        } else items(arrivals.chunked(2), key = { row -> "n" + row.first().id }) { row ->
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { lot -> DiscoverLotCard(lot, Modifier.weight(1f)) { onSelect(lot.slug) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { CatalogEntryCard(catalogTotal) { state.searching = true } }
    }
}

// MARK: Header and search

@Composable
internal fun Header(me: Me?, signedIn: Boolean, onAccount: () -> Unit, streak: Int = 0) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(HomeText.greeting(hour, if (signedIn) me?.displayName else null), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("قهوتك الجاية، هنا.", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        if (streak > 1) Box(Modifier.padding(end = 8.dp)) { StreakBadge(streak) }
        Box(Modifier.size(48.dp).clip(CircleShape).pressable(onClick = onAccount)) {
            Avatar(me?.avatarUrl, me?.displayName, 48.dp)
            Box(Modifier.matchParentSize().clip(CircleShape).background(Color.Transparent))
        }
    }
}

@Composable
internal fun SearchBar(openSearch: () -> Unit, openFilters: () -> Unit, openScan: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).height(52.dp).shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Brand.Ink.copy(alpha = 0.06f), spotColor = Brand.Ink.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = openSearch).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  ابحث عن قهوة أو محمصة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        SquareButton(Icons.Filled.FilterList, "الفلاتر", filled = false, onClick = openFilters)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        TipAnchor(AppTip.Scan) { SquareButton(Icons.Filled.DocumentScanner, "امسح كيس", filled = true, onClick = { Tips.done(ctx, AppTip.Scan); openScan() }) }
    }
}

@Composable
private fun SquareButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Brand.Ink.copy(alpha = 0.06f), spotColor = Brand.Ink.copy(alpha = 0.06f))
        .clip(RoundedCornerShape(16.dp)).background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface).pressable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

// MARK: The advisor card

@Composable
internal fun AdvisorCard(onOpen: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "p")
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().graphicsLayer { scaleX = 1 + 0.015f * p; scaleY = 1 + 0.015f * p }
        .shadow((8 + 8 * p).dp, RoundedCornerShape(26.dp), ambientColor = Brand.Olive.copy(alpha = 0.1f + 0.2f * p), spotColor = Brand.Olive.copy(alpha = 0.1f + 0.2f * p))
        .clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Brand.Brown, Brand.Olive))).pressable(onClick = onOpen).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ساعدني أختار", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Cream)
            Text("جاوب كم سؤال ونطلع لك قهوتك", fontSize = 15.sp, color = Brand.Cream.copy(alpha = 0.85f))
            Text("يلا نبدأ", fontWeight = FontWeight.Bold, color = Brand.Brown, modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(Brand.Cream).padding(horizontal = 18.dp, vertical = 9.dp))
        }
        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
            Text("✨", fontSize = 22.sp, modifier = Modifier.offset((-26).dp, (-24).dp).alpha(0.3f + 0.7f * p))
            Text("☕", fontSize = 50.sp, modifier = Modifier.rotate(-6 + 12 * (1 - p)))
            Text("✨", fontSize = 16.sp, modifier = Modifier.offset(24.dp, 22.dp).alpha(1f - 0.7f * p))
        }
    }
}

// MARK: Today's coffee

/** The bag floats on its flavour colours with its notes drifting around it; under it, the match ring or the ratings to go. */
@Composable
internal fun DailyPickCard(pick: ForYou.DailyPick, startShown: Boolean = false, onOpen: () -> Unit) {
    val spectrum = HomeText.spectrum(pick.lot.flavorNotes, 2).map { it.color() }
    val colors = if (spectrum.size >= 2) spectrum else (spectrum + listOf(Brand.Gold, Brand.Olive)).take(2)
    var shown by remember { mutableStateOf(startShown) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 120f), label = "appear")
    val bobT = rememberInfiniteTransition(label = "bob")
    val bob by bobT.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "bobv")

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(250.dp)
            .graphicsLayer { alpha = appear; scaleX = 0.96f + 0.04f * appear; scaleY = 0.96f + 0.04f * appear }
            .clip(RoundedCornerShape(28.dp)).pressable(onClick = onOpen)
            .background(Brush.linearGradient(colors.reversed()))
            .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f)))) {
            val w = maxWidth; val h = maxHeight
            Box(Modifier.offset(w * 0.24f - 120.dp, h * 0.42f - 120.dp).size(240.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent))))
            AsyncImage(pick.lot.imageUrl, pick.lot.nameAr, contentScale = ContentScale.Fit,
                modifier = Modifier.size(w * 0.36f, h * 0.62f).offset(w * 0.06f, h * 0.07f + (-7 + 9 * (1 - bob)).dp).rotate(-1.5f + 2f * (1 - bob)).multiply())
            val spots = listOf(0.07f to 0.12f, 0.04f to 0.55f, 0.33f to 0.62f)
            pick.lot.flavorNotes.take(3).forEachIndexed { i, note ->
                val pop by animateFloatAsState(if (shown) 1f else 0.2f, spring(dampingRatio = 0.55f, stiffness = 200f), label = "note$i")
                val dy = (if (i % 2 == 0) -5 else 4) * bob
                FloatingNote(note, Modifier.offset(w * spots[i].first, h * spots[i].second + dy.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = (pop - 0.2f) / 0.8f })
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.align(Alignment.TopEnd).width(w * 0.5f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("قهوة اليوم لك", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    Text(pick.lot.nameAr, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, lineHeight = 28.sp)
                    Text(listOfNotNull(pick.lot.roasterNameAr, pick.lot.flag, pick.lot.originNameAr).joinToString(" · "), fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                    if (pick.score != null && pick.reason.isNotBlank()) Text(pick.reason, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                    val score = pick.score
                    if (score != null) Box(Modifier.align(Alignment.CenterEnd)) { MatchRing(score, shown) }
                    else pick.ratingsToGo?.let { toGo ->
                        val done = 3 - toGo
                        val bar by animateFloatAsState(if (shown) done / 3f else 0f, tween(1100, delayMillis = 800), label = "bar")
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row { Text("قيّم ٣ قهاوي ونختار لك على ذوقك", fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f)); Text("$done من ٣", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                                Box(Modifier.fillMaxWidth(bar).fillMaxHeight().clip(CircleShape).background(Color(0xFFF2D48A)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNote(note: String, modifier: Modifier) {
    val f = FlavorFamily.of(note)
    Row(modifier.shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f)).clip(CircleShape)
        .background(Color.White.copy(alpha = 0.93f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (f == FlavorFamily.Other) Brand.Gold else f.color()))
                Text(" $note", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Brown, maxLines = 1)
            }
        }
    }
}

/** "92٪ تناسبك" as a ring that fills. */
@Composable
private fun MatchRing(score: Int, play: Boolean) {
    val p by animateFloatAsState(if (play) score / 100f else 0f, tween(1400, delayMillis = 500), label = "ring")
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            drawCircle(Color.White.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx()))
            drawArc(Color.White, -90f, 360f * p, false, style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score٪", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 16.sp)
            Text("تناسبك", fontSize = 9.sp, color = Color.White, lineHeight = 10.sp)
        }
    }
}

// MARK: New from your roasters

/** When you last opened each roaster's news (iOS `roasterNewsSeen`), so «جديد» marks only what came after. */
private class NewsSeen(private val prefs: android.content.SharedPreferences) {
    var map by mutableStateOf(read())
        private set
    private fun read(): Map<String, Long> = runCatching {
        val o = org.json.JSONObject(prefs.getString("roasterNewsSeen", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())
    fun mark(roaster: String) {
        map = map + (roaster to System.currentTimeMillis())
        prefs.edit().putString("roasterNewsSeen", org.json.JSONObject(map as Map<*, *>).toString()).apply()
    }
    fun isNew(roaster: String, publishedAt: String): Boolean {
        val at = runCatching { java.time.Instant.parse(publishedAt).toEpochMilli() }.getOrNull() ?: return false
        return at > (map[roaster] ?: 0L)
    }
}

private val NewsRed = Color(0xFFC24559)

/** «جديد من محامصك», as on iOS: one or two roasters show their new coffees as cards; more show story rings
 *  that turn while they hold something not opened yet, each opening a sheet of that roaster's new coffees. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoasterNewsRow(news: List<ForYou.RoasterNews>, onSelect: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val seen = remember { NewsSeen(context.getSharedPreferences("ui", 0)) }
    var showing by remember { mutableStateOf<ForYou.RoasterNews?>(null) }
    if (news.size <= 2) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeSectionHeader("جديد من محامصك", news.joinToString(" · ") { "${it.roasterNameAr} نزّلت ${HomeText.coffees(it.lots.size)} هالشهر" })
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            news.forEach { r ->
                items(r.lots, key = { "rn" + it.slug }) { lot ->
                    Column(Modifier.width(144.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                        .pressable { seen.mark(r.roasterNameAr); onSelect(lot.slug) }.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            LotPhotoWell(lot.imageUrl, lot.flavorNotes, Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(14.dp)), 6.dp, roaster = r.roasterNameAr, name = lot.nameAr)
                            if (seen.isNew(r.roasterNameAr, lot.publishedAt)) Text("جديد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.padding(6.dp).clip(CircleShape).background(NewsRed).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                        Text(lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(HomeText.ago(lot.publishedAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader("جديد من محامصك", "آخر ٣٠ يوم من المحامص اللي في مجموعتك")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(news, key = { "ring" + it.roasterNameAr }) { r ->
                val unseen = r.lots.count { seen.isNew(r.roasterNameAr, it.publishedAt) }
                Column(Modifier.width(70.dp).pressable { seen.mark(r.roasterNameAr); showing = r }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    StoryRing(r.lots.firstOrNull()?.imageUrl, unseen > 0)
                    Text(r.roasterNameAr, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                    Text(if (unseen == 0) " " else if (unseen == 1) "جديد" else "$unseen جديد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NewsRed)
                }
            }
        }
    }
    showing?.let { r ->
        ModalBottomSheet(onDismissRequest = { showing = null }) {
            Text("جديد ${r.roasterNameAr}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center)
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(r.lots, key = { "sheet" + it.slug }) { lot ->
                    Row(Modifier.fillMaxWidth().pressable { showing = null; onSelect(lot.slug) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LotPhotoWell(lot.imageUrl, emptyList(), Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), 2.dp, roaster = r.roasterNameAr, name = lot.nameAr)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(lot.nameAr, color = MaterialTheme.colorScheme.onBackground)
                            Text("نزلت ${HomeText.ago(lot.publishedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A story-style ring (iOS `StoryRing`): turning colours while it holds something new. */
@Composable
private fun StoryRing(imageUrl: String?, fresh: Boolean, size: androidx.compose.ui.unit.Dp = 62.dp) {
    val still = LocalStill.current
    val spin = if (fresh && !still) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
        t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin").value
    } else 0f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.clip(CircleShape)
            .background(if (fresh) androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Brand.Gold, NewsRed, Brand.Olive, Brand.Gold))
                        else androidx.compose.ui.graphics.SolidColor(Brand.Brown.copy(alpha = 0.18f))))
        Box(Modifier.fillMaxSize().padding(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
        LotPhotoWell(imageUrl, emptyList(), Modifier.fillMaxSize().padding(6.dp).clip(CircleShape), 4.dp)
    }
}

// MARK: Trending

@Composable
internal fun TrendingRow(lots: List<TrendingLot>, onSelect: (String) -> Unit, showAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader("🔥 ترند هالأسبوع", action = "الكل", onAction = showAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lots.take(8), key = { "t" + it.id }) { item ->
                Column(Modifier.width(156.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).pressable { onSelect(item.slug) }) {
                    Box {
                        LotPhotoWell(item.imageUrl, item.flavorNotes, Modifier.fillMaxWidth().height(124.dp))
                        Text("${item.rank}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp)
                            .graphicsLayer { shadowElevation = 0f })
                    }
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.nameAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.roasterNameAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        item.reasonsAr.firstOrNull()?.let {
                            Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// MARK: Browse by taste

@Composable
internal fun TasteSection(facets: Facets?, selected: FlavorFamily?, results: List<RemoteLot>?, onPick: (FlavorFamily) -> Unit, onSelect: (String) -> Unit, showAll: (FlavorFamily) -> Unit) {
    val families = facets?.flavors?.mapNotNull { f -> FlavorFamily.entries.firstOrNull { it.code == f.value && it != FlavorFamily.Other } }
        ?: FlavorFamily.advisorChoices
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader("اكتشف حسب ذوقك", "اختر نكهة وتطلع لك محاصيلها هنا")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            families.forEach { fam ->
                val on = selected == fam
                val bg by androidx.compose.animation.animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "chip")
                Row(Modifier.heightIn(min = 46.dp).clip(CircleShape).background(bg).pressable { onPick(fam) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(fam.color()))
                    Text("  ${fam.shortNameAr}", fontWeight = FontWeight.SemiBold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    if (on) Icon(Icons.Filled.Close, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        AnimatedVisibility(selected != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val fam = selected ?: return@AnimatedVisibility
            when {
                results == null -> Loading()
                results.isEmpty() -> Text("ما فيه محاصيل بهالنكهة للحين.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(results, key = { "f" + it.id }) { lot -> DiscoverLotCard(lot, Modifier.width(164.dp)) { onSelect(lot.slug) } }
                    item {
                        Column(Modifier.width(120.dp).height(230.dp).pressable { showAll(fam) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Text("كل محاصيل ${fam.shortNameAr}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// MARK: Origins, roasters and map

@Composable
internal fun OriginsRow(origins: List<Facet>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeader("من وين؟", "المنشأ يقول كثير عن الكوب")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(origins.filter { it.value.length == 2 && it.value != "XB" }, key = { "o" + it.value }) { o ->
                Column(Modifier.widthIn(min = 92.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).pressable { onOpen(o.value) }
                    .padding(vertical = 12.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(com.bunnlist.core.RemoteLot(id = "", slug = "", name = "", nameAr = "", roasterNameAr = "", process = "", originCode = o.value).flag ?: "", fontSize = 24.sp)
                    Text(o.labelAr, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${o.count} محصول", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun ExploreTiles(openRoasters: () -> Unit, openMap: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Icons.Filled.Storefront, Brand.Brown, "المحامص", "كل محمصة وقهاويها", Modifier.weight(1f), openRoasters)
        Tile(Icons.Filled.Map, Brand.Olive, "خريطة المناطق", "من وين تجي قهوتك", Modifier.weight(1f), openMap)
    }
}

@Composable
private fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onClick).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.height(4.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: Coffee cards

/** A coffee in «اكتشف»: the bag on its flavour wash, the name, origin and process, two notes or the real rating. */
@Composable
fun DiscoverLotCard(lot: RemoteLot, modifier: Modifier = Modifier, showRoaster: Boolean = true, onClick: () -> Unit) {
    val pressed = remember { mutableStateOf(false) }
    Column(modifier.shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Brand.Ink.copy(alpha = 0.05f), spotColor = Brand.Ink.copy(alpha = 0.05f))
        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).pressable(pressedState = pressed, onClick = onClick).padding(8.dp)) {
        LotPhotoWell(lot.imageUrl, lot.flavorNotes, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)), lifted = pressed.value,
            roaster = lot.roasterNameAr, name = lot.nameAr, origin = lot.originNameAr, originCode = lot.originCode)
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showRoaster && lot.roasterNameAr.isNotEmpty()) Text(lot.roasterNameAr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(HomeText.originLine(lot.originCode, lot.originNameAr, lot.process, lot.processLabel), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            val rating = HomeText.ratingLine(lot)
            if (rating != null) Text(rating, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 2.dp))
            else if (lot.flavorNotes.isNotEmpty()) Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                lot.flavorNotes.take(2).forEach { n ->
                    val f = FlavorFamily.of(n)
                    Text(n, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = Brand.Brown,
                        modifier = Modifier.clip(CircleShape).background((if (f == FlavorFamily.Other) Brand.Brown else f.color()).copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
internal fun CatalogEntryCard(total: Int?, onOpen: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.LibraryBooks, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("تصفّح كل الدليل", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(total?.let { "$it محصول · بحث وفلاتر وفرز" } ?: "بحث وفلاتر وفرز", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: Search results

@Composable
private fun SearchResults(app: BunnListApp, state: DiscoverState, signedIn: Boolean, facets: Facets?, onSelect: (String) -> Unit, onFacet: (FacetKind) -> Unit, onScan: () -> Unit,
                          onSuggest: (String) -> Unit = {}) {
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val gridState = remember { androidx.compose.foundation.lazy.grid.LazyGridState() }
    suspend fun token() = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null
    val key = state.query.trim() + "|" + state.filters.params()
    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true; failed = false
        try {
            val page = app.api.browse(state.query, limit = 30, offset = if (reset) 0 else state.lots.size, accessToken = token(), filters = state.filters)
            state.total = page.total; state.lots = if (reset) page.lots else state.lots + page.lots; state.loadedKey = key
        } catch (e: ApiError) { failed = true } finally { loading = false }
    }
    LaunchedEffect(key, retry) {
        if (state.loadedKey == key && state.lots.isNotEmpty()) return@LaunchedEffect
        delay(if (state.query.isEmpty()) 0 else 350); load(true); gridState.scrollToItem(0)
    }
    LaunchedEffect(Unit) { if (state.searching && state.filters.isEmpty && state.query.isEmpty()) runCatching { requester.requestFocus() } }
    val nearEnd by remember { derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= gridState.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.lots.size) { if (nearEnd && state.lots.isNotEmpty() && state.lots.size < state.total) load(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(state.query, { state.query = it }, placeholder = { Text("ابحث عن قهوة أو محمصة") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { state.query = "" }) { Icon(Icons.Filled.Close, "امسح") }
                    else IconButton(onClick = onScan) { Icon(Icons.Filled.DocumentScanner, "امسح كيس القهوة", tint = MaterialTheme.colorScheme.primary) }
                },
                shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f).focusRequester(requester))
            TextButton(onClick = { state.exitSearch(); focus.clearFocus() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") }
        }
        FilterChips(state.filters, facets, onFacet, onSort = { state.filters = state.filters.copy(sort = it) })
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + LocalBottomInset.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val full = androidx.compose.foundation.lazy.grid.GridItemSpan(2)
            item(span = { full }) { Text(if (loading && state.lots.isEmpty()) "نبحث…" else "${state.total} نتيجة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            when {
                failed && state.lots.isEmpty() -> item(span = { full }) { Retry("ما قدرنا نجيب القهوات") { state.loadedKey = null; retry++ } }
                state.lots.isEmpty() && loading -> items(6, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(1) }) { SkeletonBlock(Modifier.fillMaxWidth().height(250.dp), 22.dp) }
                state.lots.isEmpty() -> item(span = { full }) { Empty("ما لقينا شي", "جرّب كلمة ثانية أو شيل بعض الفلاتر.") }
                else -> items(state.lots.size, key = { state.lots[it].id }) { i -> val lot = state.lots[i]; BunnCoffeeCard(lot) { focus.clearFocus(); onSelect(lot.slug) } }
            }
            if (loading && state.lots.isNotEmpty()) item(span = { full }) { Loading() }
            // «ما لقيت قهوتك؟ اقترحها» under the results, with what was typed.
            if (state.query.isNotBlank() && !loading) item(span = { full }) { SuggestFromSearchCard(state.query.trim()) { onSuggest(state.query.trim()) } }
        }
    }
}

enum class FacetKind(val nameAr: String) { Roaster("المحمصة"), Origin("المنشأ"), Process("المعالجة"), Flavor("النكهة") }

private fun BrowseFilters.of(kind: FacetKind) = when (kind) { FacetKind.Roaster -> roasters; FacetKind.Origin -> origins; FacetKind.Process -> processes; FacetKind.Flavor -> flavors }
private fun BrowseFilters.with(kind: FacetKind, v: Set<String>) = when (kind) {
    FacetKind.Roaster -> copy(roasters = v); FacetKind.Origin -> copy(origins = v); FacetKind.Process -> copy(processes = v); FacetKind.Flavor -> copy(flavors = v)
}
private fun Facets.of(kind: FacetKind): List<Facet> = when (kind) {
    FacetKind.Roaster -> roasters; FacetKind.Origin -> origins; FacetKind.Process -> processes
    FacetKind.Flavor -> flavors.mapNotNull { f -> FlavorFamily.entries.firstOrNull { it.code == f.value && it != FlavorFamily.Other }?.let { f.copy(labelAr = it.shortNameAr) } }
}

@Composable
private fun FilterChips(filters: BrowseFilters, facets: Facets?, onOpen: (FacetKind) -> Unit, onSort: (String?) -> Unit) {
    var sorting by remember { mutableStateOf(false) }
    val sort = com.bunnlist.core.BrowseSort.entries.firstOrNull { it.code == filters.sort }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilterChip(selected = sort != null, onClick = { sorting = true }, label = { Text(sort?.nameAr ?: "الترتيب") },
                leadingIcon = { Icon(Icons.Filled.SwapVert, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
            DropdownMenu(sorting, { sorting = false }) {
                com.bunnlist.core.BrowseSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.nameAr, fontWeight = if (s == sort) FontWeight.Bold else null) },
                        trailingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                        onClick = { sorting = false; onSort(if (s == com.bunnlist.core.BrowseSort.ForYou) null else s.code) })
                }
            }
        }
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
