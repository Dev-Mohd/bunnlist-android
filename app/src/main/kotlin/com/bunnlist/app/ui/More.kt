package com.bunnlist.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.app.R
import com.bunnlist.core.ApiError
import com.bunnlist.core.AppIconChoice
import com.bunnlist.core.ChallengeState
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.ForYouExtras
import com.bunnlist.core.GuideGrinder
import com.bunnlist.core.HomeText
import com.bunnlist.core.PersonCard
import com.bunnlist.core.RemoteLotDetail
import com.bunnlist.core.Submission
import com.bunnlist.core.TasteMatch
import kotlinx.coroutines.launch
import java.io.File

// MARK: Weekly challenge

/** «تحدي الأسبوع»: what to do, time left, my progress filling up, how many joined. */
@Composable
fun WeeklyChallengeCard(state: ChallengeState, onShowCoffees: (com.bunnlist.core.BrowseFilters) -> Unit) {
    val c = state.challenge ?: return
    val still = LocalStill.current
    var shown by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { shown = true }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Brand.Olive, Color(0xFF2E3B26)))).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("تحدي الأسبوع · ${c.timeLeftAr()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                Text(c.titleAr, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                c.subtitleAr?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f)) }
            }
            c.emoji?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = (-5 + 7 * bob) * density; rotationZ = -6 + 9 * bob }) }
        }
        state.me?.let { me ->
            val p by animateFloatAsState(if (shown) me.progress / maxOf(c.target, 1).toFloat() else 0f, spring(dampingRatio = 0.8f, stiffness = 60f), label = "challenge")
            Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Color(0xFFE8C27A)))
            }
            Text(if (me.completed) "خلّصت التحدي 🎉 +${c.points} نقطة" else "${me.progress} من ${c.target} · ${c.actionAr} ${if (c.target == 1) "قهوة وحدة" else "${c.target} قهاوي مختلفة"}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Row {
            Text(if (state.participants == 0) "كن أول واحد يشارك" else "${state.participants} شخص شاركوا · ${state.finished} خلّصوه", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
            Text("+${c.points} نقطة وشارة", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
        }
        if (!c.filters.isEmpty) Text("☕ القهاوي اللي تحسب", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).pressable { onShowCoffees(c.filters.browse()) }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** «توأم ذوقك»: people whose coffees overlap yours most, with the share as a ring. */
@Composable
fun TasteTwinsRow(people: List<PersonCard>, nav: Nav) {
    if (people.isEmpty()) return
    val still = LocalStill.current
    var shown by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { shown = true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👥 توأم ذوقك", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { nav.push(Route.FindPeople) }) { Text("الكل", fontWeight = FontWeight.Bold) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(people.take(10), key = { it.username }) { p ->
                val ring by animateFloatAsState(if (shown) (p.sharedPercent ?: 0) / 100f else 0f, tween(1300, delayMillis = 400), label = "twin")
                val olive = MaterialTheme.colorScheme.primary
                Column(Modifier.width(96.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).pressable { nav.push(Route.Profile(p.username)) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Brand.Brown.copy(alpha = 0.12f), style = Stroke(5.dp.toPx()))
                            drawArc(olive, -90f, 360f * ring, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Avatar(p.avatarUrl, p.displayName ?: p.username, 48.dp)
                    }
                    Text(p.displayName ?: p.username, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if ((p.sharedPercent ?: 0) > 0) Text("${p.sharedPercent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = olive)
                    else p.reasonAr?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

// MARK: Journeys

/** «رحلتك»: quests whose progress comes from your collection. */
@Composable
fun JourneysScreen(app: BunnListApp, nav: Nav) {
    var items by remember { mutableStateOf<List<CollectionItem>>(emptyList()) }
    val signedIn = app.session.isSignedIn
    LaunchedEffect(Unit) { if (signedIn) items = runCatching { app.session.withToken { app.api.collection(it) } }.getOrDefault(emptyList()) }
    data class Quest(val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val current: Int, val target: Int)
    val quests = listOf(
        Quest("اجمع قهوتك", "اجمع ٥ محاصيل في مجموعتك.", Icons.Filled.Layers, Brand.Olive, items.count { it.owned }, 5),
        Quest("ذوّاق", "جرّب ٥ محاصيل مختلفة.", Icons.Filled.Coffee, Brand.Brown, items.count { it.tried }, 5),
        Quest("مستكشف المناشئ", "اجمع قهوة من ٥ دول مختلفة.", Icons.Filled.Public, Color(0xFF2E9D57), items.mapNotNull { it.originCode }.toSet().size, 5),
        Quest("جامع المحامص", "اجمع قهوة من ٥ محامص.", Icons.Filled.Storefront, Brand.Gold, items.map { it.roasterNameAr }.filter { it.isNotEmpty() }.toSet().size, 5),
        Quest("المفضّلة", "علّم ٥ محاصيل كمفضّلة.", Icons.Filled.Favorite, Color(0xFFD64545), items.count { it.favorite }, 5),
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("رحلتك", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("انطلق في مغامرات القهوة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("اجمع، جرّب، واستكشف — وافتح مغامرات جديدة كل ما كبرت مجموعتك.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!signedIn) Text("سجّل الدخول وابدأ رحلات القهوة وتابع تقدّمك.", color = MaterialTheme.colorScheme.primary)
            quests.forEach { q ->
                val done = q.current >= q.target
                val p by animateFloatAsState(minOf(q.current, q.target) / q.target.toFloat(), tween(1000), label = q.title)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(q.color.copy(alpha = if (signedIn) 1f else 0.3f)), contentAlignment = Alignment.Center) {
                        Icon(if (!signedIn) Icons.Filled.Lock else if (done) Icons.Filled.Verified else q.icon, null, tint = Color.White)
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row { Text(q.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${minOf(q.current, q.target)}/${q.target}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(q.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { p }, color = q.color, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// MARK: Taste match

/** «كم تناسبك»: the score with why, or how many ratings until there is one. */
@Composable
fun TasteMatchCard(app: BunnListApp, slug: String, key: Any?) {
    var m by remember(slug, key) { mutableStateOf<TasteMatch?>(null) }
    LaunchedEffect(slug, key) { m = runCatching { app.session.withToken { app.api.tasteMatch(slug, it) } }.getOrNull() }
    val match = m ?: return
    val score = match.score
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (score != null) {
            val color = if (score >= 70) Brand.Olive else if (score >= 50) Brand.Gold else Brand.Brown
            val p by animateFloatAsState(score / 100f, tween(1200, delayMillis = 300), label = "match")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                        drawCircle(color.copy(alpha = 0.18f), style = Stroke(7.dp.toPx()))
                        drawArc(color, -90f, 360f * p, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Text("$score%", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(match.verdictAr ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
                    match.reasons.take(3).forEach { r -> Text((if (r.positive) "⊕ " else "⊖ ") + r.text, fontSize = 13.sp, color = if (r.positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    match.myRating?.let { Text("وقيّمتها أنت $it من ٥", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Insights, null, tint = Brand.Gold)
            Text("  قيّم ${match.needed} ${if (match.needed == 1) "قهوة" else "قهوات"} زيادة ونقول لك تناسبك أو لا", fontSize = 14.sp)
        }
    }
}

// MARK: Suggestions

/** «اقترح قهوة»: a coffee missing from the guide, for the team to review. */
@Composable
fun SuggestCoffeeScreen(app: BunnListApp, prefillName: String, prefillRoaster: String, prefillOrigin: String?, nav: Nav) {
    var name by remember { mutableStateOf(prefillName) }
    var roaster by remember { mutableStateOf(prefillRoaster) }
    var origin by remember { mutableStateOf(prefillOrigin ?: "") }
    var notes by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        TopBar("اقترح قهوة", onBack = { nav.pop() })
        if (sent) Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Text("✨", fontSize = 52.sp); Text("وصلنا اقتراحك", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("نراجعه ونضيفه للدليل، ويوصلك إشعار. وإذا انضاف يطلع اسمك عليه كمكتشفه.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = { nav.pop(); nav.push(Route.Submissions) }) { Text("اقتراحاتي") }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ما لقيتها في الدليل؟ اكتب اللي على الكيس ونضيفها.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(120) }, label = { Text("اسم القهوة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(roaster, { roaster = it.take(120) }, label = { Text("المحمصة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(origin, { origin = it.take(80) }, label = { Text("المنشأ (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("الإيحاءات (اختياري، افصلها بفاصلة)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it.take(1000) }, label = { Text("ملاحظة للفريق (اختياري)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = name.trim().length >= 2 && roaster.trim().length >= 2 && !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try { app.session.withToken { app.api.suggestCoffee(name, roaster, origin, notes.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }, note, it) }; sent = true }
                    catch (e: ApiError) { error = if (e == ApiError.RateLimited) "اقترحت كثير اليوم، كمّل بكرة." else "ما انرسل، جرّب مرة ثانية." }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(if (busy) "نرسل…" else "أرسل الاقتراح", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun SubmissionsScreen(app: BunnListApp, nav: Nav) {
    var list by remember { mutableStateOf<List<Submission>?>(null) }
    LaunchedEffect(Unit) { list = runCatching { app.session.withToken { app.api.mySubmissions(it) } }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("اقتراحاتي", onBack = { nav.pop() }) { IconButton(onClick = { nav.push(Route.SuggestCoffee("", "", null)) }) { Icon(Icons.Filled.Add, "اقترح قهوة") } }
        val l = list
        when {
            l == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(76.dp), 18.dp) } }
            l.isEmpty() -> Empty("ما اقترحت قهوة للحين", "لما تمسح كيس وما نلقاه، تقدر تقترحه ونضيفه.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(l, key = { it.id }) { s ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                        .then(if (s.lotSlug != null) Modifier.pressable { nav.push(Route.Lot(s.lotSlug!!)) } else Modifier).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold); Text(s.roasterName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            s.reviewNote?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(s.statusAr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = when (s.status) { "APPROVED" -> Brand.Olive; "REJECTED" -> MaterialTheme.colorScheme.error; else -> Brand.Gold })
                    }
                }
            }
        }
    }
}

// MARK: Share card

/** The story card (iOS ShareCardView), drawn and shared as an image. */
@Composable
fun ShareCardSheetContent(lot: RemoteLotDetail, myRating: Int?, matchScore: Int?) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val notes = lot.flavorNotes.take(3)
    val glow = notes.map { FlavorFamily.of(it).color() }.ifEmpty { listOf(Brand.Olive, Brand.Gold) } + Brand.Gold
    Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.size(270.dp, 480.dp).drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }.clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF7F2E8), Color(0xFFEDE3D1))))
                .drawWithContent {
                    drawRect(Brush.radialGradient(listOf(glow[0].copy(alpha = 0.35f), Color.Transparent), center = Offset(0f, 0f), radius = size.width))
                    drawRect(Brush.radialGradient(listOf(glow[minOf(1, glow.size - 1)].copy(alpha = 0.3f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width))
                    drawContent()
                }.padding(18.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(26.dp)); Text("BunnList", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Brand.Ink)
                            Spacer(Modifier.weight(1f))
                            matchScore?.let { Text("تناسبني $it%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(CircleShape).background(Brand.Olive).padding(horizontal = 8.dp, vertical = 4.dp)) }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF7F7F7)), contentAlignment = Alignment.Center) {
                        if (lot.imageUrl != null) AsyncImage(lot.imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp), onSuccess = {})
                        else CoffeeArtCard(lot.roasterNameAr, lot.nameAr, lot.originNameAr, lot.originCode, Modifier.fillMaxSize())
                    }
                    Text(lot.nameAr, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Brand.Ink, maxLines = 1)
                    Text(lot.roasterNameAr, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black.copy(alpha = 0.55f))
                    if (notes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        notes.forEach { n -> Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(FlavorFamily.of(n).color())); Text(" $n", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOfNotNull(lot.originNameAr?.let { "المنشأ" to it }, lot.processAr?.let { "المعالجة" to it }).forEach { (k, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(k, fontSize = 10.sp, color = Color.Black.copy(alpha = 0.5f)); Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Brand.Ink) }
                        }
                    }
                    myRating?.let { r -> Text("★".repeat(r) + "☆".repeat(5 - r), color = Color(0xFFCC9933), fontSize = 18.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("اكتشف، قيّم، واختر أفضل محاصيل القهوة", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black.copy(alpha = 0.55f))
                }
            }
        }
        Button(onClick = {
            scope.launch {
                val bmp = layer.toImageBitmap().asAndroidBitmap()
                val dir = File(context.cacheDir, "shares").apply { mkdirs() }
                val file = File(dir, "bunnlist-${lot.slug}.png")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val text = "${lot.nameAr} من ${lot.roasterNameAr} على BunnList https://bunnlist.com/coffees/${lot.slug}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Icon(Icons.Filled.Share, null); Text(" شارك البطاقة", fontWeight = FontWeight.Bold) }
    }
}

// MARK: App icon

object AppIcons {
    private fun alias(c: AppIconChoice) = "com.bunnlist.app.Icon${c.name}"
    fun current(context: Context): AppIconChoice = AppIconChoice.entries.firstOrNull {
        context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, alias(it))).let { s -> s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && it == AppIconChoice.Classic) }
    } ?: AppIconChoice.Classic
    fun apply(context: Context, choice: AppIconChoice) {
        if (current(context) == choice) return
        AppIconChoice.entries.forEach { c ->
            context.packageManager.setComponentEnabledSetting(ComponentName(context.packageName, alias(c)),
                if (c == choice) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
    /** On launch: the season's icon while an occasion lasts (if seasonal is on), else your choice. */
    fun applySeason(context: Context) {
        val prefs = context.getSharedPreferences("ui", 0)
        val chosen = runCatching { AppIconChoice.valueOf(prefs.getString("icon", "Classic")!!) }.getOrDefault(AppIconChoice.Classic)
        val seasonal = prefs.getBoolean("seasonalIcon", true)
        apply(context, (if (seasonal) AppIconChoice.season(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Riyadh"))) else null) ?: chosen)
    }
    fun res(c: AppIconChoice) = when (c) {
        AppIconChoice.Classic -> R.mipmap.ic_season_classic_fg; AppIconChoice.Dark -> R.mipmap.ic_season_dark_fg; AppIconChoice.Ramadan -> R.mipmap.ic_season_ramadan_fg
        AppIconChoice.EidFitr -> R.mipmap.ic_season_eidfitr_fg; AppIconChoice.EidAdha -> R.mipmap.ic_season_eidadha_fg; AppIconChoice.NationalDay -> R.mipmap.ic_season_nationalday_fg
        AppIconChoice.FoundingDay -> R.mipmap.ic_season_foundingday_fg
    }
}

/** «أيقونة التطبيق»: pick an icon, and whether it changes by itself on occasions. */
@Composable
fun AppIconScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", 0) }
    var chosen by remember { mutableStateOf(runCatching { AppIconChoice.valueOf(prefs.getString("icon", "Classic")!!) }.getOrDefault(AppIconChoice.Classic)) }
    var seasonal by remember { mutableStateOf(prefs.getBoolean("seasonalIcon", true)) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("أيقونة التطبيق", onBack = { nav.pop() })
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(AppIconChoice.entries.toList(), key = { it.name }) { c ->
                Column(Modifier.pressable { chosen = c; prefs.edit().putString("icon", c.name).apply() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        Image(painterResource(AppIcons.res(c)), c.nameAr, contentScale = ContentScale.Crop, modifier = Modifier.size(78.dp).clip(RoundedCornerShape(20.dp))
                            .then(if (c == chosen) Modifier.border(3.dp, Brand.Gold, RoundedCornerShape(20.dp)) else Modifier))
                        if (c == chosen) Icon(Icons.Filled.CheckCircle, null, tint = Brand.Gold, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).background(Color.White, CircleShape))
                    }
                    Text(c.nameAr, fontSize = 12.sp)
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تلقائي (موسمي)", fontWeight = FontWeight.SemiBold)
                        Text("تتغيّر الأيقونة في رمضان والعيدين واليوم الوطني ويوم التأسيس، وترجع لاختيارك بعدها.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(seasonal, { seasonal = it; prefs.edit().putBoolean("seasonalIcon", it).apply() })
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Text("تتغيّر الأيقونة لما تطلع من التطبيق، وقد يأخذ المشغّل ثواني عشان يحدّثها.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// MARK: Grinders

/** «مطحنتي»: the grinder guide; pick yours and see its range, zero point and sources. */
@Composable
fun GrinderGuideScreen(app: BunnListApp, nav: Nav) {
    val context = LocalContext.current
    var grinders by remember { mutableStateOf<List<GuideGrinder>?>(null) }
    var mine by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<GuideGrinder?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        grinders = runCatching { app.api.grinders() }.getOrDefault(emptyList())
        if (app.session.isSignedIn) mine = runCatching { app.session.withToken { app.api.me(it) } }.getOrNull()?.grinderId
    }
    fun choose(g: GuideGrinder?) {
        mine = g?.id; context.getSharedPreferences("brew", 0).edit().putString("guideGrinder", g?.id).apply()
        scope.launch { runCatching { app.session.withToken { app.api.setMyGrinder(g?.id, it) } } }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("مطحنتي", onBack = { if (open != null) open = null else nav.pop() })
        val o = open
        if (o != null) { GrinderDetail(o, o.id == mine, onChoose = { choose(if (o.id == mine) null else o) }); return@Column }
        val list = grinders
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list?.firstOrNull { it.id == mine }?.let { g ->
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.Olive.copy(alpha = 0.12f)).pressable { open = g }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("مطحنتي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("${g.brand} ${g.title}", fontWeight = FontWeight.Bold); Text(g.rangeAr ?: "المواصفات قريبًا", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { OutlinedTextField(q, { q = it }, placeholder = { Text("ابحث عن مطحنة") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) }
            if (list == null) items(5) { SkeletonBlock(Modifier.fillMaxWidth().height(64.dp), 16.dp) }
            else list.filter { it.matches(q) }.groupBy { it.categoryAr }.forEach { (cat, gs) ->
                item { Text(cat, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
                items(gs, key = { it.id }) { g ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).pressable { open = g }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            if (g.imageUrl != null) AsyncImage(g.imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) else Icon(Icons.Filled.Tune, null, tint = Brand.Olive)
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) { Text("${g.brand} ${g.title}", fontWeight = FontWeight.Bold); Text(g.rangeAr ?: "المواصفات قريبًا", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (g.id == mine) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GrinderDetail(g: GuideGrinder, isMine: Boolean, onChoose: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (g.imageUrl != null) AsyncImage(g.imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp)) else Text("الصورة قريبًا", fontSize = 12.sp, color = Color.Gray)
        }
        g.imageCredit?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(g.brand, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(g.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(g.categoryAr, color = MaterialTheme.colorScheme.primary)
        g.rangeAr?.let { Text("المدى: $it", fontWeight = FontWeight.SemiBold) }
        g.micronsPerStep?.let { Text("كل درجة ≈ ${Methods.gramsCompat(it)} ميكرون", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        g.zeroGuideAr?.let { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) { Text("الصفر", fontWeight = FontWeight.Bold); Text(it, fontSize = 14.sp) } }
        g.notesAr?.let { Text(it, fontSize = 14.sp) }
        if (g.adjustment == null) Text("نجمع مواصفاتها من مصادر الشركة. تقدر تختارها كمطحنتك الحين، والمواصفات تنضاف قريبًا.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (g.sources.isNotEmpty()) {
            Text("المصادر", fontWeight = FontWeight.Bold)
            g.sources.forEach { s -> Text("🔗 ${s.label}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.pressable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url))) }.padding(vertical = 4.dp)) }
        }
        Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(if (isMine) "✓ مطحنتي · شيلها" else "هذي مطحنتي", fontWeight = FontWeight.Bold) }
    }
}

private object Methods { fun gramsCompat(v: Double) = com.bunnlist.core.Methods.grams(v) }

// MARK: Home extras

/** «كمّل من حيث وقفت»: a bag you own but haven't rated — rate it or brew it. */
@Composable
fun ResumeCard(item: ForYouExtras.Resume, onOpen: () -> Unit, onBrew: () -> Unit) {
    var lit by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { for (i in 1..4) { kotlinx.coroutines.delay(450L + i * 140L); lit = i } }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).pressable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            LotPhotoWell(item.lot.imageUrl, item.lot.flavorNotes, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)), 3.dp, roaster = item.lot.roasterNameAr, name = item.lot.nameAr, originCode = item.lot.originCode)
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.lot.nameAr, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("عندك ${HomeText.ago(item.since)} · كيف كانت؟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row { repeat(5) { i -> val sc by animateFloatAsState(if (i < lit) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "s$i")
                        Icon(Icons.Filled.Star, null, tint = if (i < lit) Brand.Gold else Brand.Brown.copy(alpha = 0.2f), modifier = Modifier.size(14.dp).graphicsLayer { scaleX = sc; scaleY = sc }) } }
                }
            }
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Brand.Olive).pressable(onClick = onBrew).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Coffee, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text(" حضّرها", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/** «اللي تتابعهم يشربون». */
@Composable
fun FriendsDrinkingCard(item: ForYouExtras.Friends, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp)) {
            item.names.take(3).forEachIndexed { i, n ->
                Box(Modifier.offset(x = (i * 14).dp).size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(n.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        LotPhotoWell(item.lot.imageUrl, item.lot.flavorNotes, Modifier.padding(start = 8.dp).size(52.dp).clip(RoundedCornerShape(12.dp)), 3.dp, roaster = item.lot.roasterNameAr, name = item.lot.nameAr, originCode = item.lot.originCode)
        Column(Modifier.padding(start = 10.dp)) {
            Text(item.lot.nameAr, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(item.line, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** The streak flame by the avatar. */
@Composable
fun StreakBadge(days: Int) {
    val t by rememberInfiniteTransition(label = "flame").animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "f")
    Row(Modifier.clip(CircleShape).background(Color(0xFFFF8A3D).copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = 15.sp, modifier = Modifier.graphicsLayer { scaleX = t; scaleY = t }); Text(" $days", fontWeight = FontWeight.Bold, color = Color(0xFFE0651F))
    }
}
