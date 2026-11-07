package com.bunnlist.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Reviews
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.CollectionFlags
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.HomeText
import com.bunnlist.core.ListSummary
import com.bunnlist.core.Methods
import com.bunnlist.core.MyReview
import com.bunnlist.core.PublicReview
import com.bunnlist.core.RelativeTime
import com.bunnlist.core.RemoteLot
import com.bunnlist.core.RemoteLotDetail
import com.bunnlist.core.RemoteRoasterRecipe
import com.bunnlist.core.SimilarLots
import com.bunnlist.core.TasteLean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** «المحصول», as on iOS: the floating bag, fact tiles, where to buy it, ratings, your collection, people's reviews, alike coffees. */
@Composable
fun LotScreen(app: BunnListApp, slug: String, nav: Nav, embedded: Boolean = false, openReview: Boolean = false) {
    var lot by remember(slug) { mutableStateOf<RemoteLotDetail?>(null) }
    var failed by remember(slug) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(slug, attempt) { failed = false; try { lot = app.api.lot(slug) } catch (e: ApiError) { failed = true } }
    val l = lot
    if (l != null) LotBody(app, l, nav, embedded, openReview) { lot = it }
    else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!embedded) LotTopBar(nav, null, null, {}, {}, {}, {})
        if (failed) Retry("تعذّر جلب تفاصيل المحصول. حاول مجددًا.") { attempt++ } else LotDetailSkeleton()
    }
}

@Composable
private fun LotTopBar(nav: Nav?, flags: CollectionFlags?, signedIn: Boolean?, onBookmark: () -> Unit, onHeart: () -> Unit, onList: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (nav != null) Box(Modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).pressable { nav.pop() }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
        }
        Text("المحصول", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        if (signedIn != null) Row(Modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val olive = MaterialTheme.colorScheme.primary
            if (signedIn) {
                IconButton(onClick = onBookmark) { Icon(if (flags?.wantsToTry == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "ودّي أجرّبها", tint = olive) }
                IconButton(onClick = onHeart) { Icon(if (flags?.favorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "مفضّل", tint = olive) }
                IconButton(onClick = onList) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "أضف لقائمة", tint = olive) }
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "مشاركة", tint = olive) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LotBody(app: BunnListApp, lot: RemoteLotDetail, nav: Nav, embedded: Boolean, openReview: Boolean, onUpdated: (RemoteLotDetail) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    var flags by remember(lot.slug) { mutableStateOf(CollectionFlags()) }
    var myReview by remember(lot.slug) { mutableStateOf<MyReview?>(null) }
    var reviews by remember(lot.slug) { mutableStateOf<List<PublicReview>>(emptyList()) }
    var similar by remember(lot.slug) { mutableStateOf<SimilarLots?>(null) }
    var reviewing by remember(lot.slug) { mutableStateOf(openReview) }
    var choosingMethod by remember { mutableStateOf(false) }
    var addingToList by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<PublicReview?>(null) }
    var experiences by remember(lot.slug) { mutableStateOf<List<com.bunnlist.core.Experience>>(emptyList()) }
    LaunchedEffect(lot.slug, signedIn) {
        val token = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null
        launch { similar = runCatching { app.api.similar(lot.slug) }.getOrNull() }
        launch { reviews = runCatching { app.api.reviews(lot.slug, token) }.getOrDefault(emptyList()) }
        if (token != null) runCatching { app.session.withToken { t -> flags = app.api.collectionFlags(lot.slug, t); myReview = app.api.myReview(lot.slug, t); experiences = app.api.myExperiences(lot.slug, t).filter { it.state != "deleted" } } }
    }
    fun apply(next: CollectionFlags) {
        val before = flags; flags = next
        scope.launch { try { flags = app.session.withToken { app.api.setCollection(lot.slug, next, it) } } catch (e: ApiError) { flags = before } }
    }
    var sharing by remember { mutableStateOf(false) }
    var matchScore by remember(lot.slug) { mutableStateOf<Int?>(null) }
    LaunchedEffect(lot.slug, signedIn) { if (signedIn) matchScore = runCatching { app.session.withToken { app.api.tasteMatch(lot.slug, it) } }.getOrNull()?.score }
    fun share() { sharing = true }
    if (sharing) ModalBottomSheet(onDismissRequest = { sharing = false }) { ShareCardSheetContent(lot, myReview?.rating, matchScore) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LotTopBar(if (embedded) null else nav, flags, signedIn,
            onBookmark = { apply(flags.copy(wantsToTry = !flags.wantsToTry)) }, onHeart = { apply(flags.copy(favorite = !flags.favorite)) },
            onList = { addingToList = true }, onShare = { share() })
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            item { FloatingBagHero(lot) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(lot.nameAr, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                    val rs = lot.roasterSlug
                    Row(Modifier.then(if (rs != null) Modifier.pressable { nav.openRoaster(rs, lot.roasterNameAr) } else Modifier).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lot.roasterNameAr, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (rs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (rs != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (lot.tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lot.tags.forEach { t ->
                            val lux = t.tag == "luxury"
                            Text(when (t.tag) { "luxury" -> "👑 فاخرة"; "infused" -> "💧 منكّهة"; else -> "🌙 ${t.tag}" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = if (lux) Brand.Gold else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(CircleShape).background((if (lux) Brand.Gold else MaterialTheme.colorScheme.primary).copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            item { CoffeeFactsGrid(lot) }
            if (signedIn) item { TasteMatchCard(app, lot.slug, myReview?.rating) }
            lot.buy?.let { buy -> item { BuyButton(buy.title, buy.subtitle, buy.isRoasterStore) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buy.url))) } } }
            item { RatingCard(lot.averageRating, lot.reviewCount, myReview?.rating, signedIn) { reviewing = true } }
            if (lot.flavorNotes.size > 4) item {
                LotSection("كل النكهات") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { lot.flavorNotes.forEach { NoteBubble(it) } }
                }
            }
            if (signedIn) {
                item {
                    LotSection("مجموعتي") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Toggle("لديّ", Icons.Filled.CheckCircle, flags.owned, Modifier.weight(1f)) { apply(flags.copy(owned = !flags.owned)) }
                                Toggle("مفضّل", Icons.Filled.Favorite, flags.favorite, Modifier.weight(1f)) { apply(flags.copy(favorite = !flags.favorite)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Toggle("ودّي أجرّب", Icons.Filled.Bookmark, flags.wantsToTry, Modifier.weight(1f)) { apply(flags.copy(wantsToTry = !flags.wantsToTry)) }
                                Toggle("جرّبته", Icons.Filled.Coffee, flags.tried, Modifier.weight(1f)) { apply(flags.copy(tried = !flags.tried)) }
                            }
                        }
                    }
                }
                item {
                    LotSection("تجاربي") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            experiences.sortedByDescending { it.brewedAt }.take(5).forEach { e ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(listOfNotNull(Methods.nameAr(e.methodCode), e.log?.recipeTitle).joinToString(" · "), fontWeight = FontWeight.Bold)
                                        Text(listOfNotNull(RelativeTime.ar(e.brewedAt).takeIf { it.isNotEmpty() }, e.log?.brewSeconds?.let { Methods.clock(it) },
                                            e.log?.taste?.let { t -> com.bunnlist.core.BrewTaste.entries.firstOrNull { it.code == t }?.nameAr }, if (e.state == "published") "منشورة" else "خاصة").joinToString(" · "),
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (e.note.isNotBlank()) Text(e.note, fontSize = 13.sp, maxLines = 2)
                                    }
                                    Text("★".repeat(e.stars), color = Brand.Gold)
                                }
                            }
                            WideTonalButton("سجّل تجربة تحضير", Icons.Filled.AddCircleOutline) { choosingMethod = true }
                        }
                    }
                }
            }
            lot.community?.takeIf { it.collectors + it.brewers > 0 }?.let { c ->
                item {
                    LotSection("المجتمع") {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat2("${c.collectors}", "عندهم بالمجموعة"); Stat2("${c.brewers}", "حضّروها")
                        }
                    }
                }
            }
            if (lot.brewStats.isNotEmpty()) item {
                LotSection("التقييم حسب طريقة التحضير") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lot.brewStats.forEach { b ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Methods.nameAr(b.method), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("★ ${String.format(Locale.ROOT, "%.1f", b.averageRating)} · ${b.reviewCount}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (lot.roasterRecipes.isNotEmpty()) item { LotSection("وصفة المحمصة") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { lot.roasterRecipes.forEach { RoasterRecipeCard(it) } } } }
            item { LotSection("الوصفات") { WideTonalButton("وصفات هذه القهوة", Icons.AutoMirrored.Filled.MenuBook) { nav.push(Route.Recipes(lot.slug, lot.nameAr)) } } }
            if (reviews.isNotEmpty()) item {
                LotSection("تقييمات الناس") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        reviews.forEach { r -> ReviewRow(r, onAuthor = r.authorUsername?.let { u -> { nav.push(Route.Profile(u)) } }, onComments = r.commentCount?.let { { commentsFor = r } }) }
                    }
                }
            }
            similar?.similar?.takeIf { it.isNotEmpty() }?.let { s -> item { SimilarRow("قهاوي تشبهها", "من محامص ثانية", s.map { it.lot to it.reason.takeIf { r -> r.isNotBlank() } }) { nav.push(Route.Lot(it)) } } }
            similar?.sameRoaster?.takeIf { it.isNotEmpty() }?.let { s -> item { SimilarRow("من نفس المحمصة", lot.roasterNameAr, s.map { it to null }) { nav.push(Route.Lot(it)) } } }
        }
    }

    if (choosingMethod) ModalBottomSheet(onDismissRequest = { choosingMethod = false }) {
        val methods = lot.recommendedBrewMethods.filter { it in BrewPreset.methods }.ifEmpty { listOf("V60", "ESPRESSO") }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("بأي طريقة؟", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            methods.forEach { m ->
                val preset = BrewPreset.presets(m).first()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .pressable { choosingMethod = false; nav.push(Route.Brewing(preset, lot.nameAr, lot.slug)) }.padding(16.dp)) {
                    Text(Methods.nameAr(m), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${preset.nameAr} · ${preset.summaryAr}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (addingToList) AddToListSheet(app, lot.slug, onDismiss = { addingToList = false })
    commentsFor?.let { r -> CommentsSheet(app, "review", r.id, "تقييم ${r.authorName}", onDismiss = { commentsFor = null }, onCount = { n -> reviews = reviews.map { if (it.id == r.id) it.copy(commentCount = n) else it } }) }
    if (reviewing) ReviewSheet(myReview, lot.recommendedBrewMethods.firstOrNull(), onDismiss = { reviewing = false }) { r ->
        try {
            val result = app.session.withToken { app.api.submitReview(lot.slug, r, it) }
            myReview = result.review
            onUpdated(lot.copy(averageRating = result.averageRating, reviewCount = result.reviewCount))
            if (!flags.tried) apply(flags.copy(tried = true, wantsToTry = false))
            reviewing = false
            null
        } catch (e: ApiError) {
            when (e) { ApiError.Forbidden -> "حسابك موقوف عن النشر حاليًا."; ApiError.RateLimited -> "محاولات كثيرة، جرّب بعد شوي."; else -> "ما انحفظ، جرّب مرة ثانية." }
        }
    }
}

// MARK: The floating bag

@Composable
internal fun FloatingBagHero(lot: RemoteLotDetail) {
    val still = (LocalStill.current || androidx.compose.ui.platform.LocalInspectionMode.current)
    var shown by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "hero")
    val bobT = rememberInfiniteTransition(label = "bob")
    val bob by bobT.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "bobv")
    val spots = listOf(0.80f to 0.15f, 0.19f to 0.30f, 0.83f to 0.68f, 0.21f to 0.82f)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))) {
            LotPhotoWell(lot.imageUrl, lot.flavorNotes, Modifier.fillMaxSize().graphicsLayer {
                translationY = (-7 + 9 * (1 - bob)) * density; rotationZ = -1.2f + 1.6f * (1 - bob)
                alpha = appear; scaleX = 0.88f + 0.12f * appear; scaleY = 0.88f + 0.12f * appear
            }, 36.dp)
            lot.flavorNotes.take(4).forEachIndexed { i, n ->
                val pop = remember { Animatable(if (still) 1f else 0.2f) }
                LaunchedEffect(Unit) { delay(250L + i * 120L); pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 200f)) }
                val dy = (if (i % 2 == 0) -6 else 5) * bob
                Box(Modifier.offset(maxWidth * (1f - spots[i].first) - 50.dp, maxHeight * spots[i].second - 16.dp + dy.dp).width(100.dp), contentAlignment = Alignment.Center) {
                    NoteBubble(n, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = (pop.value - 0.2f) / 0.8f })
                }
            }
        }
    }
}

@Composable
private fun NoteBubble(note: String, modifier: Modifier = Modifier) {
    val f = FlavorFamily.of(note)
    val tint = if (f == FlavorFamily.Other) Brand.Brown else f.color()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(modifier.shadow(8.dp, CircleShape, ambientColor = Brand.Brown.copy(alpha = 0.16f), spotColor = Brand.Brown.copy(alpha = 0.16f)).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.94f)).padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Text(" $note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
        }
    }
}

// MARK: Facts

@Composable
internal fun CoffeeFactsGrid(lot: RemoteLotDetail) {
    val still = (LocalStill.current || androidx.compose.ui.platform.LocalInspectionMode.current)
    var appeared by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { appeared = true }
    val halves = buildList<@Composable (Modifier) -> Unit> {
        lot.altitudeMeters?.takeIf { it > 0 }?.let { m -> add { mod -> FactTile(mod) { AltitudeTile(m, appeared) } } }
        lot.processAr?.let { p -> add { mod -> FactTile(mod) { ProcessTile(lot.process, p) } } }
        lot.variety?.takeIf { it.isNotBlank() }?.let { v -> add { mod -> FactTile(mod) { VarietyTile(v) } } }
    }
    val rows = buildList<List<@Composable (Modifier) -> Unit>> {
        if (!lot.originNameAr.isNullOrEmpty()) add(listOf { mod -> FactTile(mod) {
            if (lot.originCode?.uppercase() == "XB" || lot.originNameAr.orEmpty().contains("مزيج")) BlendTile(lot.regionAr) else OriginTile(lot.originNameAr.orEmpty(), lot.flag, lot.regionAr, appeared)
        } })
        halves.chunked(2).forEach { add(it) }
        TasteLean.position(lot.flavorNotes)?.let { pos -> add(listOf { mod -> FactTile(mod, minHeight = 0) { TasteLeanTile(pos, appeared) } }) }
        lot.community?.methods?.filter { it.count > 0 }?.maxByOrNull { it.count }?.let { top -> add(listOf { mod -> FactTile(mod, minHeight = 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("أكثر طريقة يحضّرونها فيها الناس", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${Methods.nameAr(top.method)} · ${top.count} تجربة", fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        } }) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { index, row ->
            val rise = remember { Animatable(if (still) 1f else 0f) }
            LaunchedEffect(Unit) { delay(150L + index * 100L); rise.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 140f)) }
            Row(Modifier.graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 22 * density }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FactTile(modifier: Modifier, minHeight: Int = 110, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.heightIn(min = minHeight.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, Brand.Brown.copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(14.dp), content = content)
}

@Composable private fun TileLabel(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** The flag and country, with a dotted line that draws from the farm to a cup. */
@Composable
private fun OriginTile(country: String, flag: String?, region: String?, play: Boolean) {
    val draw by animateFloatAsState(if (play) 1f else 0f, tween(1400, delayMillis = 600), label = "route")
    val pulseT = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseT.animateFloat(0f, 1f, infiniteRepeatable(tween(1800)), label = "pulsev")
    val gold = Brand.Gold; val brown = MaterialTheme.colorScheme.tertiary
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            flag?.let { Text(it, fontSize = 34.sp) }
            Text(country, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(region?.takeIf { it.isNotEmpty() } ?: "من $country لفنجانك", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.size(140.dp, 64.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val path = Path().apply { moveTo(w * 0.06f, h * 0.86f); quadraticTo(w * 0.5f, -h * 0.35f, w * 0.92f, h * 0.70f) }
                    val pm = PathMeasure().apply { setPath(path, false) }
                    val part = Path(); pm.getSegment(0f, pm.length * draw, part, true)
                    drawPath(part, gold.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
                    val dot = Offset(w * 0.06f, h * 0.86f)
                    drawCircle(gold, 5.dp.toPx(), dot)
                    drawCircle(gold.copy(alpha = 0.8f * (1 - pulse)), 5.dp.toPx() * (1 + 1.4f * pulse), dot, style = Stroke(2.dp.toPx()))
                }
                if (draw > 0.99f) Icon(Icons.Filled.Coffee, null, tint = brown, modifier = Modifier.align(Alignment.CenterEnd).offset(y = 12.dp).size(16.dp))
            }
        }
    }
}

/** A blend: three origins drift together into one cup. */
@Composable
private fun BlendTile(region: String?) {
    val merged = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(500); merged.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 40f)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("مزيج", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(region?.takeIf { it.isNotEmpty() } ?: "خلطة من أكثر من منشأ في فنجان واحد", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        Box(Modifier.size(96.dp, 76.dp), contentAlignment = Alignment.Center) {
            val colors = listOf(Brand.Gold, Brand.Olive, Brand.Brown)
            colors.forEachIndexed { i, c ->
                val a = i * 2 * Math.PI / 3 - Math.PI / 2; val r = 30 - 21 * merged.value
                Box(Modifier.offset((Math.cos(a) * r).dp, (Math.sin(a) * r).dp).size(30.dp).clip(CircleShape).background(c.copy(alpha = 0.75f)))
            }
            Icon(Icons.Filled.Coffee, null, tint = Color.White, modifier = Modifier.size(18.dp).graphicsLayer { alpha = merged.value; scaleX = 0.4f + 0.6f * merged.value; scaleY = 0.4f + 0.6f * merged.value })
        }
    }
}

/** The height counts up while a mountain draws itself. */
@Composable
private fun AltitudeTile(meters: Int, play: Boolean) {
    val shown by animateIntAsState(if (play) meters else 0, tween(1300, delayMillis = 500), label = "alt")
    val draw by animateFloatAsState(if (play) 1f else 0f, tween(1300, delayMillis = 500), label = "mtn")
    val fill by animateFloatAsState(if (play) 1f else 0f, tween(600, delayMillis = 1600), label = "mtnFill")
    val olive = MaterialTheme.colorScheme.primary
    TileLabel("الارتفاع")
    Text("${String.format(Locale.ENGLISH, "%,d", shown)} م", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(Modifier.size(84.dp, 44.dp)) {
            val pts = listOf(0f to 1f, 0.32f to 0.3f, 0.46f to 0.5f, 0.66f to 0.05f, 1f to 1f)
            val path = Path().apply { moveTo(pts[0].first * size.width, pts[0].second * size.height); pts.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) } }
            drawPath(path, olive.copy(alpha = 0.14f * fill))
            val pm = PathMeasure().apply { setPath(path, false) }; val part = Path(); pm.getSegment(0f, pm.length * draw, part, true)
            drawPath(part, olive, style = Stroke(2.2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

/** The process with a small moving sign: a turning sun for dried, a drop for washed… */
@Composable
private fun ProcessTile(code: String, name: String) {
    val spinT = rememberInfiniteTransition(label = "spin")
    val angle by spinT.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "a")
    val pulse by spinT.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val (icon, color, spins) = when (code) {
        "NATURAL" -> Triple(Icons.Filled.WbSunny, Brand.Gold, true)
        "WASHED" -> Triple(Icons.Filled.Opacity, Color(0xFF4F86C6), false)
        "HONEY" -> Triple(Icons.Filled.Opacity, Brand.Gold, false)
        "EXPERIMENTAL" -> Triple(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, false)
        else -> Triple(Icons.Filled.Eco, MaterialTheme.colorScheme.primary, false)
    }
    TileLabel("المعالجة")
    Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    Spacer(Modifier.height(8.dp))
    Icon(icon, null, tint = color, modifier = Modifier.size(32.dp).rotate(if (spins) angle else 0f).graphicsLayer { alpha = if (spins) 1f else pulse })
}

@Composable
private fun VarietyTile(variety: String) {
    TileLabel("السلالة")
    Text(variety, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    Spacer(Modifier.height(8.dp))
    Icon(Icons.Filled.Eco, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
}

/** A bar from chocolate and nuts to fruit, with a cup that slides to where the notes lean. */
@Composable
private fun TasteLeanTile(position: Double, play: Boolean) {
    val x by animateFloatAsState(if (play) position.toFloat() else 0.5f, spring(dampingRatio = 0.55f, stiffness = 50f), label = "lean")
    TileLabel("نكهتها تميل لـ")
    Spacer(Modifier.height(10.dp))
    // Laid out like iOS (chocolate on the left, fruit on the right) whatever the language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(FlavorFamily.Chocolate.color(), FlavorFamily.Caramel.color(), FlavorFamily.Berry.color()))))
                Box(Modifier.offset((maxWidth - 28.dp) * x).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Coffee, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            Row {
                listOf("شوكولاتة ومكسرات", "حلاوة وكراميل", "فواكه وزهور").forEachIndexed { i, t ->
                    Text(t, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (i < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// MARK: Buy, ratings, sections

/** «اشترِ من المحمصة»: a light sweeps across it now and then, and the arrow nudges. */
@Composable
internal fun BuyButton(title: String, subtitle: String, roaster: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "buy")
    val sweep by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1400, delayMillis = 1600)), label = "sweep")
    val nudge by t.animateFloat(0f, 1f, infiniteRepeatable(tween(800, delayMillis = 2000), RepeatMode.Reverse), label = "nudge")
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onBackground).pressable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val fg = MaterialTheme.colorScheme.background
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.ShoppingBag, null, tint = fg) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = fg)
                Text(subtitle, fontSize = 12.sp, color = fg.copy(alpha = 0.75f), maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = fg, modifier = Modifier.offset(x = (-5 * nudge).dp))
        }
        Box(Modifier.matchParentSize().graphicsLayer { translationX = sweep * size.width }.width(70.dp)
            .background(Brush.horizontalGradient(0.4f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.22f), 0.6f to Color.Transparent)))
    }
}

@Composable
internal fun RatingCard(average: Double, count: Int, myRating: Int?, signedIn: Boolean, onRate: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (count > 0) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Laurel(mirrored = false)
                    Text(String.format(Locale.ROOT, "%.1f", average), fontSize = 54.sp, fontWeight = FontWeight.Black)
                    Laurel(mirrored = true)
                }
            }
            Text("$count تقييم", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Filled.Reviews, null, tint = Brand.Gold, modifier = Modifier.size(44.dp))
            Text("ما أحد قيّمها بعد. كن الأول!", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        myRating?.let { Text("✓ تقييمك: $it من ٥", color = MaterialTheme.colorScheme.primary) }
        if (signedIn) Row(Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground).pressable(onClick = onRate),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Coffee, null, tint = MaterialTheme.colorScheme.background)
            Text("  " + if (myRating == null) "جرّبتها؟ قيّمها الآن" else "عدّل تقييمك", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.background)
        } else Text("سجّل الدخول من «حسابي» عشان تقيّمها.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun LotSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp); content() }
}

@Composable
internal fun Toggle(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        .pressable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp)); Text("  $label", color = c, fontSize = 15.sp)
    }
}

@Composable
private fun WideTonalButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val olive = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(olive.copy(alpha = 0.16f)).pressable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = olive); Text("  $label", color = olive, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable private fun Stat2(v: String, l: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(v, fontWeight = FontWeight.Bold, fontSize = 22.sp); Text(l, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReviewRow(r: PublicReview, onAuthor: (() -> Unit)?, onComments: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.authorName, fontWeight = FontWeight.Bold, modifier = Modifier.then(if (onAuthor != null) Modifier.pressable(onClick = onAuthor) else Modifier))
            Text("  ${RelativeTime.ar(r.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("★".repeat(r.rating), color = Brand.Gold)
        }
        Text(Methods.nameAr(r.brewMethod) + if (r.wouldBuyAgain) " · يشتريها مرة ثانية" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        r.body?.takeIf { it.isNotBlank() }?.let { Text(it) }
        if (onComments != null) TextButton(onClick = onComments, contentPadding = PaddingValues(0.dp)) {
            Text(if ((r.commentCount ?: 0) > 0) "${r.commentCount} تعليق" else "علّق", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SimilarRow(title: String, subtitle: String, items: List<Pair<RemoteLot, String?>>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(items, key = { it.first.id }) { (lot, reason) ->
                Column(Modifier.width(156.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).pressable { onOpen(lot.slug) }.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LotPhotoWell(lot.imageUrl, lot.flavorNotes, Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(14.dp)), 6.dp)
                    Text(lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(lot.roasterNameAr, lot.flag).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (reason != null) Text(reason, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    else HomeText.ratingLine(lot)?.let { Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary) }
                }
            }
        }
    }
}

/** «أضف لقائمة»: your lists with a tick where this coffee is, and a new list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToListSheet(app: BunnListApp, slug: String, onDismiss: () -> Unit) {
    var lists by remember { mutableStateOf<List<ListSummary>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { lists = runCatching { app.session.withToken { app.api.myLists(it, slug) } }.getOrDefault(emptyList()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("أضف لقائمة", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val l = lists
            if (l == null) Loading()
            l?.forEach { list ->
                val on = list.hasLot == true
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).pressable {
                    lists = lists?.map { if (it.id == list.id) it.copy(hasLot = !on) else it }
                    scope.launch { runCatching { app.session.withToken { app.api.setListItem(list.id, slug, !on, it) } }.onFailure { reload++ } }
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(list.emoji ?: "☕", fontSize = 22.sp)
                    Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(list.title, fontWeight = FontWeight.Bold); Text(list.countLine, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (on) Icon(Icons.Filled.Check, "فيها", tint = MaterialTheme.colorScheme.primary)
                }
            }
            WideTonalButton("قائمة جديدة", Icons.Filled.AddCircleOutline) { creating = true }
        }
    }
    if (creating) NewListDialog(app, onDismiss = { creating = false }) { created ->
        creating = false
        scope.launch { runCatching { app.session.withToken { app.api.setListItem(created.id, slug, true, it) } }; reload++ }
    }
}

@Composable
private fun RoasterRecipeCard(r: RemoteRoasterRecipe) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.label, fontWeight = FontWeight.Bold)
            val numbers = listOfNotNull(r.doseG?.let { "${Methods.grams(it)}غ بن" }, r.waterMl?.let { "$it مل ماء" }, r.tempC?.let { "$it°م" }, r.timeSec?.let { Methods.clock(it) }, r.grind)
            if (numbers.isNotEmpty()) Text(numbers.joinToString(" · "), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 14.sp) }
            r.link?.let { link -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) { Text("افتح الوصفة") } }
        }
    }
}


/** The coffee page's shape while it loads (iOS LotDetailSkeleton). */
@Composable
private fun LotDetailSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(300.dp), 24.dp)
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(28.dp), 8.dp)
        SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(20.dp), 8.dp)
        SkeletonBlock(Modifier.fillMaxWidth().height(110.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SkeletonBlock(Modifier.weight(1f).height(110.dp)); SkeletonBlock(Modifier.weight(1f).height(110.dp)) }
    }
}
