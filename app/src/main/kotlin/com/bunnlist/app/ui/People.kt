package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.bunnlist.core.ListSummary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.Methods
import com.bunnlist.core.OfficialUpdate
import com.bunnlist.core.PersonCard
import com.bunnlist.core.PublicProfile
import com.bunnlist.core.RelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Follow / requested / following, with the private-account flow. */
@Composable
fun FollowButton(app: BunnListApp, username: String, following: Boolean, requested: Boolean, onChange: (following: Boolean, requested: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val label = when { following -> "تتابعه"; requested -> "طلبت"; else -> "تابع" }
    val click: () -> Unit = {
        busy = true
        scope.launch {
            runCatching {
                app.session.withToken { t ->
                    if (following || requested) { app.api.unfollow(username, t); onChange(false, false) }
                    else { val r = app.api.follow(username, t); onChange(r.following, r.requested) }
                }
            }
            busy = false
        }
    }
    if (following || requested) OutlinedButton(onClick = click, enabled = !busy) { Text(label) }
    else Button(onClick = click, enabled = !busy) { Text(label) }
}

@Composable
fun PersonRow(app: BunnListApp, p: PersonCard, nav: Nav) {
    var state by remember(p.username) { mutableStateOf(p.isFollowing to p.requested) }
    Card(onClick = { nav.push(Route.Profile(p.username)) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatarUrl, p.displayName ?: p.username, 44.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.displayName ?: "@${p.username}", fontWeight = FontWeight.Bold)
                    if (p.isPrivate) Icon(Icons.Filled.Lock, "حساب خاص", Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("@${p.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                p.reasonAr?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            }
            if (app.session.isSignedIn) FollowButton(app, p.username, state.first, state.second) { f, r -> state = f to r }
        }
    }
}

/** «اكتشف ناس»: search by name or @username, plus suggestions when signed in. */
@Composable
fun FindPeopleScreen(app: BunnListApp, nav: Nav) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PersonCard>>(emptyList()) }
    var suggested by remember { mutableStateOf<List<PersonCard>>(emptyList()) }
    val signedIn = app.session.isSignedIn
    suspend fun token() = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null
    LaunchedEffect(Unit) { if (signedIn) runCatching { app.session.withToken { app.api.suggestedPeople(it) } }.onSuccess { suggested = (it.similar + it.active).distinctBy { p -> p.username } } }
    LaunchedEffect(q) { delay(300); results = if (q.trim().length >= 2) runCatching { app.api.searchPeople(q, token()) }.getOrDefault(emptyList()) else emptyList() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        TopBar("اكتشف ناس", onBack = { nav.pop() })
        OutlinedTextField(q, { q = it }, placeholder = { Text("ابحث بالاسم أو @اليوزر") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val list = if (q.trim().length >= 2) results else suggested
            if (q.trim().length < 2 && suggested.isNotEmpty()) item { Text("ناس ذوقهم قريب منك", fontWeight = FontWeight.Bold) }
            if (q.trim().length >= 2 && results.isEmpty()) item { Text("ما لقينا أحد بهالاسم", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.username }) { PersonRow(app, it, nav) }
        }
    }
}

/** Someone's public page, laid out like iOS `PublicProfileView` (and like «ملفي»): the header card with follow, then
 *  القهوات / الوصفات / القوائم. A private account shows only the header to people who don't follow it. Report or block from the menu. */
@Composable
fun ProfileScreen(app: BunnListApp, username: String, nav: Nav) {
    var profile by remember(username) { mutableStateOf<PublicProfile?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(ProfileTab.Coffees) }
    var shelf by remember { mutableStateOf("owned") }
    var coffees by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var coffeesHidden by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var lists by remember { mutableStateOf<List<ListSummary>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    suspend fun token() = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null
    LaunchedEffect(username, reload) {
        failed = false
        try { profile = app.api.profile(username, token()) } catch (e: ApiError) { if (profile == null) failed = true }
    }
    val locked = profile?.let { it.isPrivate && !it.isMe && !it.isFollowing } ?: true
    LaunchedEffect(profile?.isFollowing, profile?.isBlocked, locked, shelf) {
        val p = profile ?: return@LaunchedEffect
        if (locked || p.isBlocked) return@LaunchedEffect
        try { val r = app.api.personCoffees(username, shelf, token()); coffees = r.coffees; counts = r.counts; coffeesHidden = false }
        catch (e: ApiError.Forbidden) { coffeesHidden = true } catch (e: Exception) { if (coffees == null) coffees = emptyList() }
    }
    LaunchedEffect(username, tab, locked) {
        if (tab == ProfileTab.Lists && !locked) lists = runCatching { app.api.userLists(username, token()) }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // iOS inline bar: back, a centred @username, and the options menu.
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 8.dp)) {
            CircleIconButton(Modifier.align(Alignment.CenterStart), onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("@$username", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.align(Alignment.Center))
            }
            val p = profile
            if (signedIn && p != null && !p.isMe) Box(Modifier.align(Alignment.CenterEnd)) {
                CircleIconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "خيارات الحساب", tint = MaterialTheme.colorScheme.primary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("بلّغ عن الحساب") }, onClick = { menu = false; reporting = true })
                    DropdownMenuItem(text = { Text(if (p.isBlocked) "إلغاء الحظر" else "حظر الحساب") }, onClick = {
                        menu = false
                        if (p.isBlocked) scope.launch {
                            runCatching { app.session.withToken { app.api.setBlocked(username, false, it) } }.onSuccess { info = "فكّيت الحظر."; reload++ }
                        } else confirmBlock = true
                    })
                }
            }
        }
        val p = profile
        when {
            p == null && failed -> Retry("تعذّر فتح الملف") { reload++ }
            p == null -> Loading()
            else -> ProfileBody(p, null, nav, tab, { tab = it }, shelf, { shelf = it }, coffees, counts, lists, coffeesHidden = coffeesHidden, notice = info,
                onUnblock = { scope.launch { runCatching { app.session.withToken { app.api.setBlocked(username, false, it) } }.onSuccess { info = "فكّيت الحظر."; reload++ } } },
                follow = if (!signedIn || p.isBlocked) null else ({
                    val label = when { p.isFollowing -> "تتابعه"; p.requested -> "طلبت المتابعة"; else -> "تابِع" }
                    PillButton(label, filled = !p.isFollowing && !p.requested) {
                        if (busy) return@PillButton
                        busy = true
                        scope.launch {
                            runCatching { app.session.withToken { t ->
                                if (p.isFollowing || p.requested) { app.api.unfollow(username, t); profile = p.copy(isFollowing = false, requested = false) }
                                else { val r = app.api.follow(username, t); profile = p.copy(isFollowing = r.following, requested = r.requested) }
                            } }
                            busy = false
                            reload++
                        }
                    }
                }))
        }
    }
    if (reporting) ReportDialog("بلّغ عن @$username", onDismiss = { reporting = false }) { r, d ->
        runCatching { app.session.withToken { app.api.report("profile", username, r, d, it) } }.isSuccess.also { if (it) info = "وصلنا بلاغك، شكرًا لك." }
    }
    if (confirmBlock) AlertDialog(onDismissRequest = { confirmBlock = false }, title = { Text("تحظر @$username؟") },
        text = { Text("ما راح تشوف تعليقاته ونشاطه، وهو ما يقدر يتابعك أو يعلّق على منشوراتك. تقدر تفك الحظر بعدين.") },
        confirmButton = {
            TextButton(onClick = {
                confirmBlock = false
                scope.launch { runCatching { app.session.withToken { app.api.setBlocked(username, true, it) } }.onSuccess { info = "حظرته."; reload++ }.onFailure { info = "ما انحظر، جرّب مرة ثانية." } }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("احظر") }
        },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("إلغاء") } })
}

/** iOS's round bar button: a white circle with a hairline shadow. */
@Composable
private fun CircleIconButton(modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(modifier.size(44.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface).pressable(onClick = onClick),
        contentAlignment = Alignment.Center) { icon() }
}

/** «تحديثات BunnList»: posts from the team, with comments, and a way to write to them. */
@Composable
fun UpdatesScreen(app: BunnListApp, nav: Nav) {
    var updates by remember { mutableStateOf<List<OfficialUpdate>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var commenting by remember { mutableStateOf<OfficialUpdate?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        try { updates = app.api.updates(if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null) } catch (e: ApiError) { failed = true }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("تحديثات BunnList", onBack = { nav.pop() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (app.session.isSignedIn) item {
                Card(onClick = { nav.push(Route.Feedback) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("عندك اقتراح أو تجربة؟", fontWeight = FontWeight.Bold)
                        Text("اكتب لنا، نقرا كل رسالة ونرد عليك.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val list = updates
            when {
                list == null && failed -> item { Retry("ما قدرنا نجيب التحديثات") { reload++ } }
                list == null -> item { Loading() }
                list.isEmpty() -> item { Empty("ما فيه تحديثات للحين", "أول ما ننشر شي جديد بيطلع هنا.") }
                else -> items(list, key = { it.id }) { u ->
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(null, null, 36.dp, official = true)
                                Column(Modifier.padding(start = 10.dp)) { OfficialName(); Text(RelativeTime.ar(u.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(u.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(u.body)
                            FilledTonalButton(onClick = { commenting = u }) { Text(if (u.commentCount > 0) "${u.commentCount} تعليق" else "علّق") }
                        }
                    }
                }
            }
        }
    }
    commenting?.let { u ->
        CommentsSheet(app, "update", u.id, u.title, onDismiss = { commenting = null }, onCount = { n -> updates = updates?.map { if (it.id == u.id) it.copy(commentCount = n) else it } })
    }
}
