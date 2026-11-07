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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
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
    suspend fun token() = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null

    LaunchedEffect(refresh, signedIn) {
        failed = false
        try { pulse = app.api.home(token()).pulse } catch (e: ApiError) { failed = true }
        latest = runCatching { app.api.updates(token()).firstOrNull() }.getOrNull()
    }
    LaunchedEffect(section, refresh) {
        if (section == Section.Following && signedIn) following = runCatching { app.session.withToken { app.api.followingFeed(it) } }.getOrNull() ?: emptyList()
    }
    LaunchedEffect(section, kind, allTime, refresh) {
        if (section == Section.Leaders) board = runCatching { app.api.leaderboard(allTime, kind, token()) }.getOrNull()
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
                    item { UpdatesCard(latest) { nav.push(Route.Updates) } }
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
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LeaderKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.nameAr) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            FilterChip(selected = !allTime, onClick = { allTime = false }, label = { Text("هذا الأسبوع") })
                            FilterChip(selected = allTime, onClick = { allTime = true }, label = { Text("على الإطلاق") })
                        }
                        board?.me?.rank?.let { Text("ترتيبك $it · ${board?.me?.points ?: 0} نقطة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                        if (!signedIn) Text("سجّل دخولك وابدأ تجمع نقاط من تقييماتك ووصفاتك.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    val b = board
                    if (b == null) item { Loading() }
                    else if (b.entries.isEmpty()) item { Empty("ما فيه أحد للحين", "كن الأول!") }
                    else items(b.entries, key = { "${it.rank}-${it.username ?: it.name}" }) { e ->
                        Card(onClick = { e.username?.let { nav.push(Route.Profile(it)) } }, shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = if (e.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${e.rank}" }, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                                Avatar(e.avatarUrl, e.name, 38.dp)
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(e.name, fontWeight = FontWeight.Bold)
                                    e.username?.let { Text("@$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Text("${e.points}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
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
