package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.LeaderKind
import com.bunnlist.core.Leaderboard
import com.bunnlist.core.Methods
import com.bunnlist.core.OfficialUpdate
import com.bunnlist.core.PulseItem
import com.bunnlist.core.RelativeTime

private enum class Section(val nameAr: String) { ForYou("لك"), Following("أتابعهم"), Leaders("المتصدرين") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(Section.ForYou) }
    var pulse by remember { mutableStateOf<List<PulseItem>?>(null) }
    var following by remember { mutableStateOf<List<PulseItem>?>(null) }
    var latest by remember { mutableStateOf<OfficialUpdate?>(null) }
    var board by remember { mutableStateOf<Leaderboard?>(null) }
    var kind by remember { mutableStateOf(LeaderKind.Points) }
    var allTime by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var commenting by remember { mutableStateOf<PulseItem?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var challenge by remember { mutableStateOf<com.bunnlist.core.ChallengeState?>(null) }
    var trending by remember { mutableStateOf<com.bunnlist.core.HomeFeed.Trending?>(null) }
    var trendTab by rememberSaveable { mutableIntStateOf(0) }
    var points by remember { mutableStateOf<com.bunnlist.core.Points?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var twins by remember { mutableStateOf<List<com.bunnlist.core.PersonCard>>(emptyList()) }
    suspend fun token() = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null

    LaunchedEffect(refresh, signedIn) {
        failed = false
        try { val home = app.api.home(token()); pulse = home.pulse; trending = home.trending } catch (e: ApiError) { failed = true }
        latest = runCatching { app.api.updates(token()).firstOrNull() }.getOrNull()
        challenge = runCatching { app.api.challenge(token()) }.getOrNull()
        twins = if (signedIn) runCatching { app.session.withToken { app.api.suggestedPeople(it) } }.getOrNull()?.similar ?: emptyList() else emptyList()
    }
    LaunchedEffect(section, refresh) {
        if (section == Section.Following && signedIn) following = runCatching { app.session.withToken { app.api.followingFeed(it) } }.getOrNull() ?: emptyList()
    }
    LaunchedEffect(section, kind, allTime, refresh, signedIn) {
        if (section != Section.Leaders) return@LaunchedEffect
        points = if (signedIn) runCatching { app.session.withToken { app.api.points(it) } }.getOrNull() else null
        board = runCatching { app.api.leaderboard(kind != LeaderKind.Points || allTime, kind, token()) }.getOrNull()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("المجتمع", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.push(Route.FindPeople) }) { Icon(Icons.Filled.PersonAdd, "اكتشف ناس", tint = MaterialTheme.colorScheme.primary) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Section.entries.forEachIndexed { i, s ->
                SegmentedButton(selected = section == s, onClick = { section = s }, shape = SegmentedButtonDefaults.itemShape(i, Section.entries.size)) { Text(s.nameAr) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            when (section) {
                Section.ForYou -> {
                    item {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        TipAnchor(AppTip.Updates) { UpdatesCard(latest) { Tips.done(ctx, AppTip.Updates); nav.push(Route.Updates) } }
                    }
                    challenge?.takeIf { it.challenge != null }?.let { c -> item { WeeklyChallengeCard(c) { f -> nav.showCoffees(f) } } }
                    item { TrendingSection(trending, failed, trendTab, { trendTab = it }, nav) }
                    if (signedIn && twins.isNotEmpty()) item { TasteTwinsRow(twins, nav) }
                    if (!pulse.isNullOrEmpty()) item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(Icons.Filled.MonitorHeart, null, Modifier.size(24.dp)); Text("  نبض المجتمع", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val list = pulse
                    when {
                        list == null && failed -> item { Retry("ما قدرنا نجيب المنشورات") { refresh++ } }
                        list == null -> items(3) { SkeletonBlock(Modifier.fillMaxWidth().height(180.dp), 22.dp) }
                        list.isEmpty() -> item { Empty("هدوء هنا", "أول ما أحد يحضّر أو ينشر وصفة بيطلع هنا.") }
                        else -> items(list, key = { it.at + it.name + (it.brewId ?: it.recipeId ?: it.lotSlug) }) { p ->
                            PostCard(p, nav, onComments = { commenting = p })
                        }
                    }
                }
                Section.Following -> if (!signedIn) item { Guest(onSignIn) } else {
                    val list = following
                    when {
                        list == null -> item { Loading() }
                        list.isEmpty() -> item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("👥", fontSize = 40.sp)
                                Text("ما فيه جديد ممن تتابعهم", fontWeight = FontWeight.Bold)
                                Text("تابع ناس ذوقهم قريب منك وتشوف وش جربوا ووش حضّروا هنا أول بأول.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { nav.push(Route.FindPeople) }) { Text("اكتشف ناس") }
                            }
                        }
                        else -> items(list, key = { "f" + it.at + it.name + (it.brewId ?: it.recipeId ?: it.lotSlug) }) { p -> PostCard(p, nav, onComments = { commenting = p }) }
                    }
                }
                Section.Leaders -> {
                    item { if (signedIn) MyPointsCard(app, points, board, allTime) else Guest(onSignIn) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RuleButton(Icons.AutoMirrored.Filled.HelpOutline, "كيف أجمع النقاط؟", Modifier.weight(1f)) { showRules = true }
                            RuleButton(Icons.Filled.Flag, "التحديات", Modifier.weight(1f)) { nav.push(Route.Journeys) }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.EmojiEvents, null, Modifier.size(28.dp)); Text("  المتصدرين", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            val kinds = listOf(LeaderKind.Helpful to "أفضل تعليق", LeaderKind.Raters to "أكثر مقيّم", LeaderKind.Points to "الأكثر نشاطًا")
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                kinds.forEachIndexed { i, (k, label) ->
                                    SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, kinds.size), icon = {}) { Text(label, maxLines = 1) }
                                }
                            }
                            if (kind == LeaderKind.Points) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(selected = !allTime, onClick = { allTime = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("هذا الأسبوع") }
                                SegmentedButton(selected = allTime, onClick = { allTime = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("كل الأوقات") }
                            }
                        }
                    }
                    val b = board
                    when {
                        b == null -> item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { SkeletonBlock(Modifier.fillMaxWidth().height(60.dp), 14.dp) } } }
                        b.entries.isEmpty() -> item {
                            Empty(if (kind == LeaderKind.Points) "ما أحد جمع نقاط ${if (allTime) "بعد" else "هالأسبوع"}" else "القائمة فاضية للحين", "قيّم قهوة أو انشر وصفة وكن أول اسم في القائمة.")
                        }
                        else -> item {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                                b.entries.forEachIndexed { i, e ->
                                    LeaderRow(e, kind) { if (!e.isMe) e.username?.let { nav.push(Route.Profile(it)) } }
                                    if (i < b.entries.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showRules) PointRulesSheet { showRules = false }
    commenting?.let { p ->
        val (type, id) = p.commentTarget ?: return@let
        CommentsSheet(app, type, id, p.lotNameAr ?: p.recipeTitle ?: "", onDismiss = { commenting = null }, onCount = { n ->
            fun upd(l: List<PulseItem>?) = l?.map { if (it === p || it == p) it.copy(commentCount = n) else it }
            pulse = upd(pulse); following = upd(following)
        })
    }
}

@Composable
private fun UpdatesCard(latest: OfficialUpdate?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Avatar(null, null, 42.dp, official = true)
            Column(Modifier.padding(start = 12.dp)) {
                Text("تحديثات BunnList", fontWeight = FontWeight.Bold)
                if (latest != null) {
                    Text(latest.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    Text(latest.body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text("أخبار التطبيق، والقهوات الجديدة، وصندوق اقتراحاتك.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PostCard(p: PulseItem, nav: Nav, onComments: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.then(
                if (p.username != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.push(Route.Profile(p.username!!)) } else Modifier)) {
                Avatar(p.avatarUrl, p.name, 38.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    Text("${p.actionAr} · ${RelativeTime.ar(p.at)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (p.lotSlug != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { nav.push(Route.Lot(p.lotSlug!!)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(p.imageUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(p.lotNameAr ?: "", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(p.flag, p.roasterNameAr).joinToString(" "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            p.recipeTitle?.let { Text("📋 $it", fontWeight = FontWeight.SemiBold) }
            val nums = listOfNotNull(p.doseGrams?.let { "${Methods.grams(it)}غ بن" }, p.waterGrams?.let { "${Methods.grams(it)}غ ماء" }, p.brewSeconds?.let { Methods.clock(it) })
            if (nums.isNotEmpty()) Text(nums.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            p.stars?.takeIf { it > 0 }?.let { s ->
                Row { repeat(s) { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp)) } }
            }
            p.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
            if (p.commentTarget != null) TextButton(onClick = onComments, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                Text(if (p.commentCount > 0) "  ${p.commentCount} تعليق" else "  علّق")
            }
        }
    }
}


@Composable fun Loading() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable fun Retry(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(text); TextButton(onClick = onRetry) { Text("إعادة المحاولة") } }
}
@Composable fun Empty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold); Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Guest(onSignIn: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("انضم للمجتمع", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("سجّل دخولك وتابع ناس ذوقهم قريب منك.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSignIn) { Text("سجّل دخولك") }
        }
    }
}


/** Your points card (iOS myCard): avatar, rank, points / streak / this week, and the streak bonus line. */
@Composable
private fun MyPointsCard(app: BunnListApp, points: com.bunnlist.core.Points?, board: Leaderboard?, allTime: Boolean) {
    var me by remember { mutableStateOf<com.bunnlist.core.Me?>(null) }
    LaunchedEffect(Unit) { me = runCatching { app.session.withToken { app.api.me(it) } }.getOrNull() }
    val olive = Brand.Olive
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(olive, olive.copy(alpha = 0.8f)))).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(me?.avatarUrl, me?.displayName, 52.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(me?.displayName ?: "نقاطك", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = androidx.compose.ui.graphics.Color.White)
                val rank = board?.me?.rank
                Text(if (rank != null) "ترتيبك $rank ${if (allTime) "على الإطلاق" else "هذا الأسبوع"}" else "ابدأ تجمع نقاط وتطلع في القائمة",
                    fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
            }
        }
        val streak = points?.streak ?: 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat("${points?.total ?: 0}", "نقطة", Icons.Filled.Star, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(36.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f)))
            Stat("$streak", if (streak == 1) "يوم متواصل" else "أيام متواصلة", Icons.Filled.LocalFireDepartment, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(36.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f)))
            Stat("+${points?.week ?: 0}", "هذا الأسبوع", Icons.AutoMirrored.Filled.TrendingUp, Modifier.weight(1f))
        }
        points?.level?.let { level ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${level.emoji} ${level.nameAr}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
                    val next = level.next; val nextName = level.nextNameAr
                    if (next != null && nextName != null)
                        Text("باقي ${next - points.total} على «$nextName»", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(level.progress.toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(Brand.Gold))
                }
            }
        }
        if (streak > 0) {
            val toBonus = 7 - streak % 7
            Text(if (toBonus == 7) "أكملت أسبوع! كمّل عشان المكافأة الجاية 🔥" else "باقي $toBonus ${if (toBonus == 1) "يوم" else "أيام"} على مكافأة السلسلة (+25)",
                fontSize = 12.sp, color = Brand.GoldLight)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = androidx.compose.ui.graphics.Color.White)
            Text(" $value", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = androidx.compose.ui.graphics.Color.White)
        }
        Text(label, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun RuleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).pressable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text("  $label", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** iOS LeaderboardRow: medal or rank, avatar, name and @username, points in gold. */
@Composable
private fun LeaderRow(e: com.bunnlist.core.LeaderEntry, kind: LeaderKind, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (e.isMe) Brand.Gold.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${e.rank}" }, fontSize = if (e.rank <= 3) 22.sp else 15.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        Box(Modifier.padding(horizontal = 8.dp).size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Brand.Gold.copy(alpha = 0.35f)).padding(2.dp), contentAlignment = Alignment.Center) {
            Avatar(e.avatarUrl, e.name, 44.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(if (e.isMe) "${e.name} (أنت)" else e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                e.levelAr?.let { Text("${e.levelEmoji.orEmpty()} $it", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand.Olive) }
                e.username?.let { Text("@$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        val unit = when (kind) { LeaderKind.Helpful -> " مفيد"; LeaderKind.Raters -> " تقييم"; else -> "" }
        Text("${e.points}$unit", fontWeight = FontWeight.Bold, color = Brand.Gold)
        Icon(if (kind == LeaderKind.Raters) Icons.AutoMirrored.Filled.Chat else Icons.Filled.Star, null, Modifier.padding(start = 4.dp).size(18.dp), tint = Brand.Gold)
    }
}

/** «كيف أجمع النقاط؟»: the rewards table (iOS PointRulesView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointRulesSheet(onDismiss: () -> Unit) {
    val rules = listOf(Triple("تضيف قهوة لمجموعتك", 5, "أول 5 قهاوي باليوم"), Triple("تقيّم قهوة", 5, "أول 5 تقييمات باليوم"),
        Triple("تكتب ملاحظة مع تقييمك", 5, "20 حرف أو أكثر"), Triple("تسجّل تجربة تحضير", 5, "3 مرات باليوم"), Triple("تنشر وصفة", 20, "مرة لكل وصفة"),
        Triple("أحد يشوف تقييمك مفيد", 2, "لين 5 باليوم"), Triple("أحد يحضّر بوصفتك", 3, "لين 5 باليوم"),
        Triple("سلسلة 7 أيام", 25, "كل أسبوع متواصل، ويوم سماح واحد بالأسبوع"))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("كيف أجمع النقاط؟", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                rules.forEachIndexed { i, (t, n, note) ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(t); Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("+$n", fontWeight = FontWeight.Bold, color = Brand.Gold)
                    }
                    if (i < rules.lastIndex) HorizontalDivider()
                }
            }
            Text("السلسلة تزيد يوم كل ما سويت نشاط واحد على الأقل (بتوقيت السعودية)، ويوم واحد يفوتك بالأسبوع ما يكسرها. بعد الحد اليومي تنحسب أفعالك بس بدون نقاط. لو حذفت شي، تنسحب نقاطه. ما تبي تطلع في المتصدرين؟ من الإعدادات.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class TrendTab(val nameAr: String) { Coffee("قهوة"), Roasters("محامص"), Recipes("وصفات") }

/** «ترند هالأسبوع» (iOS TrendingSection): coffees as big ranked cards, roasters, recipes. */
@Composable
private fun TrendingSection(trending: com.bunnlist.core.HomeFeed.Trending?, failed: Boolean, tab: Int, onTab: (Int) -> Unit, nav: Nav) {
    val flame by androidx.compose.animation.core.rememberInfiniteTransition(label = "flame").animateFloat(0.6f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "f")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(26.dp).graphicsLayer { alpha = flame }, tint = androidx.compose.ui.graphics.Color(0xFFF59E3B))
            Text(" ترند هالأسبوع", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TrendTab.entries.forEachIndexed { i, t ->
                Text(t.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (tab == i) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface)
                        .pressable { onTab(i) }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        when {
            trending == null && failed -> Text("ما قدرنا نجيب الترند. اسحب للتحديث.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            trending == null -> SkeletonBlock(Modifier.fillMaxWidth().height(300.dp), 26.dp)
            tab == 0 -> if (trending.lots.isEmpty()) TrendEmpty("الترند يبدأ لما الناس تحضّر وتقيّم. كن أول واحد هالأسبوع!") else {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(trending.lots, key = { "tl" + it.id }) { item -> TrendingCard(item) { nav.push(Route.Lot(item.slug)) } }
                }
                if (trending.lots.any { it.isTopUp }) Text("القائمة تكمل من الأكثر إضافة هالشهر لين يكثر النشاط.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            tab == 1 -> if (trending.roasters.isEmpty()) TrendEmpty("ما فيه محامص في الترند هالأسبوع للحين.") else {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                    trending.roasters.forEachIndexed { i, r ->
                        Row(Modifier.fillMaxWidth().clickable { nav.openRoaster(r.slug, r.nameAr) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Gold, modifier = Modifier.width(30.dp))
                            LotPhotoWell(r.imageUrl, emptyList(), Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), 3.dp, roaster = r.nameAr, name = r.nameAr)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(r.nameAr, fontWeight = FontWeight.Bold)
                                Text("${r.coffees} ${if (r.coffees == 1) "قهوة" else "قهوات"} عليها نشاط هالأسبوع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.outline)
                        }
                        if (i < trending.roasters.lastIndex) HorizontalDivider(Modifier.padding(start = 12.dp))
                    }
                }
            }
            else -> if (trending.recipes.isEmpty()) TrendEmpty("انشر وصفتك، وإذا عجبت الناس تطلع هنا.") else {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trending.recipes.size, key = { "tr" + trending.recipes[it].id }) { i ->
                        val r = trending.recipes[i]
                        Column(Modifier.size(210.dp, 170.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                            .pressable { nav.push(Route.Recipes(null, null, openId = r.id)) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Brand.Gold.copy(alpha = 0.8f))
                                Spacer(Modifier.weight(1f))
                                Text(Methods.nameAr(r.methodCode), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Olive,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Olive.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Text(r.title, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(r.authorName + (r.lotNameAr?.let { " · $it" } ?: ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("❤ ${r.likes}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFE0457B))
                                if (r.brews > 0) Text("☕ ${r.brews} حضّروها", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Olive)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendEmpty(text: String) {
    Text("✨ $text", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp))
}

/** One big trending card: rank, the bag, how it moved and why it's here (iOS TrendingCard). */
@Composable
private fun TrendingCard(item: com.bunnlist.core.TrendingLot, onClick: () -> Unit) {
    Column(Modifier.width(270.dp).shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Brand.Brown.copy(alpha = 0.12f), spotColor = Brand.Brown.copy(alpha = 0.12f))
        .clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            LotPhotoWell(item.imageUrl, item.flavorNotes, Modifier.fillMaxSize(), 14.dp, roaster = item.roasterNameAr, name = item.nameAr, origin = item.originNameAr, originCode = item.originCode)
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                Text("${item.rank}", fontSize = 54.sp, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(0f, 6f), 12f)))
                Spacer(Modifier.weight(1f))
                val (text, color) = when {
                    item.pinned -> "📌 مثبّتة" to Brand.Ink
                    item.moveAr != null -> item.moveAr!! to (if (item.move == null) Brand.Gold else if ((item.move ?: 0) > 0) Brand.Olive else Brand.Brown)
                    else -> null to Brand.Ink
                }
                text?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (color == Brand.Gold) Brand.Ink else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 9.dp, vertical = 5.dp)) }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.nameAr, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.roasterNameAr.takeIf { it.isNotEmpty() }, item.originNameAr, item.processLabel).joinToString(" · "), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                item.reasonsAr.forEach { Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 4.dp)) }
            }
            FlavorStrip(com.bunnlist.core.HomeText.spectrum(item.flavorNotes), Modifier.padding(top = 6.dp))
        }
    }
}
