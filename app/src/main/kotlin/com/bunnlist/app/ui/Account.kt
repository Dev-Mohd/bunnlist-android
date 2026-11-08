package com.bunnlist.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BuildConfig
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.ListSummary
import com.bunnlist.core.Me
import com.bunnlist.core.Methods
import com.bunnlist.core.NotificationKind
import com.bunnlist.core.PersonCard
import com.bunnlist.core.ProfileSummary
import com.bunnlist.core.PublicProfile
import com.bunnlist.core.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * «ملفي», as on iOS: signed in with a username it's your own profile (the one others see) with your taste card;
 * settings sit behind the gear. Without a username yet: pick one. As a guest: ways to sign in.
 */
@Composable
fun AccountScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    var me by remember { mutableStateOf<Me?>(null) }
    var profile by remember { mutableStateOf<PublicProfile?>(null) }
    var summary by remember { mutableStateOf<ProfileSummary?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(signedIn, reload, nav.version) {
        if (!signedIn) return@LaunchedEffect
        runCatching { app.session.withToken { t ->
            me = app.api.me(t); summary = app.api.summary(t)
            me?.username?.let { u -> profile = app.api.profile(u, t) }
        } }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // iOS inline navigation bar: a small centred title, share and settings on the trailing side.
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp)) {
            Text("ملفي", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                me?.username?.let { u ->
                    IconButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "تابع ذائقتي في BunnList: @$u ☕"), null))
                    }) { Icon(Icons.Filled.Share, "شارك ملفك", tint = MaterialTheme.colorScheme.primary) }
                }
                IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Filled.Settings, "الإعدادات", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        when {
            !signedIn -> GuestCard(onSignIn)
            me == null -> Loading()
            me?.username == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brand.Gold.copy(alpha = 0.12f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر اسم مستخدم", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("عشان يلقاك الناس ويتابعونك، ويطلع ملفك مثل ما يشوفونه.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PillButton("اختر اسمك", filled = true) { nav.push(Route.EditProfile) }
                }
                summary?.let { TasteCard(it.taste) }
            }
            else -> MyProfile(app, nav, profile, summary)
        }
    }
}

@Composable
private fun GuestCard(onSignIn: () -> Unit) {
    // iOS guest header: placeholder avatar, «زائر», on the soft gold card.
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Gold.copy(alpha = 0.14f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(null, "؟", 96.dp)
        Text("زائر", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("استكشاف بدون حساب", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("سجّل دخولك", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("احفظ مجموعتك على كل أجهزتك، وسوّ وصفاتك، واقترح القهاوي اللي ناقصة في الدليل.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        PillButton("سجّل الدخول", filled = true, onClick = onSignIn)
    }
}

internal enum class ProfileTab(val nameAr: String) { Coffees("القهوات"), Recipes("الوصفات"), Lists("القوائم") }

@Composable
private fun MyProfile(app: BunnListApp, nav: Nav, p: PublicProfile?, summary: ProfileSummary?) {
    var tab by remember { mutableStateOf(ProfileTab.Coffees) }
    var shelf by remember { mutableStateOf("owned") }
    var coffees by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var lists by remember { mutableStateOf<List<ListSummary>?>(null) }
    val username = p?.username
    LaunchedEffect(username, shelf) {
        if (username == null) return@LaunchedEffect
        runCatching { app.session.withToken { app.api.personCoffees(username, shelf, it) } }.onSuccess { coffees = it.coffees; counts = it.counts }
    }
    LaunchedEffect(username, tab) { if (tab == ProfileTab.Lists && username != null) lists = runCatching { app.session.withToken { app.api.userLists(username, it) } }.getOrDefault(emptyList()) }
    // Your own profile lists every recipe of yours, private ones too (marked 🔒).
    var mine by remember { mutableStateOf<List<com.bunnlist.core.RemoteRecipe>?>(null) }
    LaunchedEffect(username, tab, nav.version) {
        if (tab == ProfileTab.Recipes) mine = runCatching { app.session.withToken { app.api.myRecipes(it) } }.getOrNull()?.filter { it.state != "deleted" }
    }
    ProfileBody(p, summary, nav, tab, { tab = it }, shelf, { shelf = it }, coffees, counts, lists, mine = mine)
}

/** The shelves of «القهوات», as iOS names them. */
private val shelves = listOf("owned" to "عندي", "tried" to "جرّبتها", "favorite" to "المفضلة", "wantsToTry" to "أبي أجربها")

/** «ملفي» laid out like iOS `PublicProfileView`: header, taste card, a segmented control, then the tab. */
@Composable
internal fun ProfileBody(p: PublicProfile?, summary: ProfileSummary?, nav: Nav, tab: ProfileTab, onTab: (ProfileTab) -> Unit,
                         shelf: String, onShelf: (String) -> Unit, coffees: List<CollectionItem>?, counts: Map<String, Int>, lists: List<ListSummary>?,
                         coffeesHidden: Boolean = false, follow: (@Composable () -> Unit)? = null, onUnblock: () -> Unit = {}, notice: String? = null,
                         mine: List<com.bunnlist.core.RemoteRecipe>? = null) {
    LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val full = GridItemSpan(3)
        if (p == null) { item(span = { full }) { Loading() }; return@LazyVerticalGrid }
        item(span = { full }) { Header(p, summary?.followRequests ?: 0, nav, follow) }
        notice?.let { item(span = { full }) { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        summary?.let { s -> item(span = { full }) { Box(Modifier.padding(top = 6.dp)) { TasteCard(s.taste) } } }
        if (p.isBlocked) { item(span = { full }) {
            StateCard(Icons.Filled.Block, MaterialTheme.colorScheme.onSurfaceVariant, "حظرت هذا الحساب", "ما تشوف نشاطه وتعليقاته، وهو ما يشوف حقك.") {
                OutlinedButton(onClick = onUnblock) { Text("إلغاء الحظر") }
            }
        }; return@LazyVerticalGrid }
        if (p.isLocked) { item(span = { full }) {
            StateCard(Icons.Filled.Lock, Brand.Gold, "هذا الحساب خاص", if (p.requested) "طلبت تتابعه. أول ما يقبل بتشوف قهاويه ووصفاته." else "تابعه عشان تشوف قهاويه ووصفاته.")
        }; return@LazyVerticalGrid }
        item(span = { full }) {
            BrandSegmented(ProfileTab.entries.map { it.nameAr }, tab.ordinal, Modifier.padding(top = 6.dp)) { onTab(ProfileTab.entries[it]) }
        }
        when (tab) {
            ProfileTab.Coffees -> {
                item(span = { full }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shelves.forEach { (k, l) -> ChoiceChip(if (counts.isEmpty()) l else "$l ${counts[k] ?: 0}", shelf == k) { onShelf(k) } }
                    }
                }
                val list = coffees
                if (coffeesHidden) item(span = { full }) {
                    Text("🔒 قهاويه خاصة", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                }
                else if (list == null) item(span = { full }) { LoadingBags() }
                else if (list.isEmpty()) item(span = { full }) {
                    Text("ما فيه قهاوي هنا بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                }
                else items(list, key = { "c" + it.lotId }) { c ->
                    Column(Modifier.pressable { nav.push(Route.Lot(c.slug)) }, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LotPhotoWell(c.imageUrl, c.flavorNotes, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)), 6.dp,
                            roaster = c.roasterNameAr, name = c.nameAr, origin = c.originNameAr, originCode = c.originCode)
                        Text(c.nameAr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            ProfileTab.Recipes -> if (p.isMe) item(span = { full }) { MyRecipesList(mine, nav) } else item(span = { full }) {
                if (p.recipes.isEmpty()) Text(if (p.activityHidden) "وصفاته خاصة." else "ما نشرت وصفات بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                    p.recipes.forEachIndexed { i, r ->
                        Column(Modifier.fillMaxWidth().pressable { nav.push(Route.Recipes(null, null)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(Methods.nameAr(r.methodCode), r.lotNameAr).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < p.recipes.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
            ProfileTab.Lists -> {
                val l = lists
                if (l == null) item(span = { full }) { Loading() }
                else if (l.isEmpty()) item(span = { full }) { Empty("ما فيه قوائم", "سوّ قائمة من «مجموعتي».") }
                else items(l, key = { "l" + it.id }) { ListTile(it, 104) { nav.push(Route.ListPage(it.id)) } }
            }
        }
    }
}

@Composable
internal fun Header(p: PublicProfile, requests: Int, nav: Nav, follow: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brand.Gold.copy(alpha = 0.12f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.border(2.dp, Brand.Gold.copy(alpha = 0.55f), CircleShape).padding(3.dp)) { Avatar(p.avatarUrl, p.displayName ?: p.username, 76.dp) }
            Column(Modifier.padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.displayName ?: p.username, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (p.isPrivate) Icon(Icons.Filled.Lock, "حساب خاص", Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text("@${p.username}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        p.bio?.takeIf { it.isNotBlank() }?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountBox(p.followers, "متابِع", Modifier.weight(1f), if (p.isLocked) null else ({ nav.push(Route.FollowList(p.username, "followers")) }))
            CountBox(p.following, "يتابع", Modifier.weight(1f), if (p.isLocked) null else ({ nav.push(Route.FollowList(p.username, "following")) }))
            CountBox(p.points, "نقطة", Modifier.weight(1f), null)
        }
        if (!p.isMe) { follow?.invoke(); return@Column }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PillButton("عدّل ملفك", filled = true) { nav.push(Route.EditProfile) } }
            Box(Modifier.weight(1f)) { PillButton("اكتشف ناس", filled = false) { nav.push(Route.FindPeople) } }
        }
        if (requests > 0) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).pressable { nav.push(Route.FollowRequests) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PersonAdd, null); Text("  طلبات المتابعة", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$requests", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(CircleShape).background(Brand.Gold).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

/** «الوصفات» on your own profile: all your recipes (published and private 🔒), and «＋ سوّ وصفة جديدة». */
@Composable
private fun MyRecipesList(mine: List<com.bunnlist.core.RemoteRecipe>?, nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mine == null) Loading()
        else mine.forEach { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                .pressable { nav.push(Route.RecipeEditor(com.bunnlist.core.RecipeDraft.from(r, copy = false))) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title.ifBlank { Methods.nameAr(r.methodCode) }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(listOfNotNull(Methods.nameAr(r.methodCode), r.lot?.nameAr ?: "عامة").joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val published = r.state == "published"
                Text(if (published) "منشورة" else "🔒 خاصة", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (published) Brand.Olive else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).background(if (published) Brand.Olive.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        CreateRecipeCard { nav.push(Route.RecipeEditor(com.bunnlist.core.RecipeDraft())) }
    }
}

/** A dashed «＋ سوّ وصفة جديدة» card. */
@Composable
internal fun CreateRecipeCard(onClick: () -> Unit) {
    val olive = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .drawBehind { drawRoundRect(olive.copy(alpha = 0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 12f)))) }
        .pressable(onClick = onClick).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("＋ سوّ وصفة جديدة", fontWeight = FontWeight.Bold, color = olive)
        Text("تقدر تبدأ من وصفة بطل أو وصفة المحمصة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PublicProfile.isLocked: Boolean get() = isPrivate && !isMe && !isFollowing

/** A centred card for a blocked or private profile. */
@Composable
private fun StateCard(icon: ImageVector, tint: Color, title: String, body: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(34.dp), tint = tint)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

@Composable
private fun CountBox(value: Int?, label: String, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
        .then(if (onClick != null && value != null) Modifier.pressable(onClick = onClick) else Modifier).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (value != null) Text("$value", fontWeight = FontWeight.Bold, fontSize = 17.sp) else Icon(Icons.Filled.Lock, "خاص", Modifier.size(16.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PillButton(title: String, filled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .background(if (filled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(title, fontWeight = FontWeight.SemiBold, color = if (filled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
    }
}

/** «ذائقتك»: what you like, once you rated enough; until then, how many to go. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasteCard(t: ProfileSummary.Taste) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ذائقتك", fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
            t.averageRating?.let { Text("★ ${String.format(Locale.ROOT, "%.1f", it)}", color = Brand.Gold, fontWeight = FontWeight.Bold) }
        }
        if (t.unlocked) {
            @Composable fun row(title: String, chips: List<String>) {
                if (chips.isEmpty()) return
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp)) }
                    }
                }
            }
            row("المناشئ اللي تحبها", t.topOrigins.map { "${it.name} · ${String.format(Locale.ROOT, "%.1f", it.average)}★" })
            row("المعالجة", t.topProcesses.map { Methods.processAr(it.name) ?: it.name })
            row("النكهات", t.topFlavors)
            t.favoriteMethod?.let { Text("💧 طريقتك المفضلة: ${Methods.nameAr(it)}", color = MaterialTheme.colorScheme.primary) }
        } else Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = Brand.Gold) }
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("بطاقة ذائقتك تنتظرك", fontWeight = FontWeight.Bold)
                Text("قيّم ${t.required} قهوات ونقولك وش يعجبك: المنشأ والمعالجة والنكهات وطريقتك المفضلة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { if (t.required > 0) t.progress / t.required.toFloat() else 0f }, color = Brand.Gold, modifier = Modifier.fillMaxWidth())
                Text("${t.progress}/${t.required}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// MARK: Settings

/** «الإعدادات», as on iOS: preferences, my tools, privacy, about, then sign out and delete. */
@Composable
fun SettingsScreen(app: BunnListApp, nav: Nav, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    var me by remember { mutableStateOf<Me?>(null) }
    var requests by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (signedIn) runCatching { app.session.withToken { t -> me = app.api.me(t); requests = app.api.summary(t).followRequests ?: 0 } } }
    fun patch(block: suspend (String) -> Me) { scope.launch { runCatching { app.session.withToken { block(it) } }.onSuccess { me = it }.onFailure { error = "ما انحفظ، جرّب مرة ثانية." } } }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("الإعدادات", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsGroup("التفضيلات") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Contrast, null, tint = MaterialTheme.colorScheme.primary); Text("  المظهر", modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        listOf("system" to "تلقائي", "light" to "فاتح", "dark" to "داكن").forEachIndexed { i, (k, l) ->
                            SegmentedButton(selected = ThemeMode.mode == k, onClick = { ThemeMode.set(context, k) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l, fontSize = 12.sp) }
                        }
                    }
                }
                if (signedIn) Item(Icons.Filled.Notifications, "الإشعارات", null) { nav.push(Route.NotificationSettings) }
                Item(Icons.Filled.Language, "اللغة", "العربية") {}
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var tipsReset by remember { mutableStateOf(false) }
                Item(Icons.Filled.Lightbulb, if (tipsReset) "بتطلع لك التلميحات من جديد" else "اعرض التلميحات من جديد", null) { Tips.reset(ctx); tipsReset = true }
            }
            if (signedIn) SettingsGroup("أدواتي") {
                var teamUnread by remember { mutableIntStateOf(0) }
                LaunchedEffect(nav.version) { teamUnread = runCatching { app.session.withToken { app.api.teamMessages(it) } }.getOrNull()?.unread ?: 0 }
                Item(Icons.Filled.Email, "رسائل BunnList", if (teamUnread > 0) "$teamUnread" else null) { nav.push(Route.TeamMessages) }
                Item(Icons.Filled.Public, "جوازي", null) { nav.push(Route.Passport) }
                Item(Icons.Filled.MenuBook, "وصفاتي", null) { nav.push(Route.Recipes(null, null)) }
                Item(Icons.Filled.Tune, "مطحنتي", null) { nav.push(Route.Grinders) }
                Item(Icons.Filled.Explore, "رحلتك", null) { nav.push(Route.Journeys) }
                Item(Icons.Filled.AutoAwesome, "اقتراحاتي", null) { nav.push(Route.Submissions) }
            }
            SettingsGroup("الأيقونة") { Item(Icons.Filled.Apps, "أيقونة التطبيق", null) { nav.push(Route.AppIcon) } }
            me?.let { m ->
                SettingsGroup("الخصوصية", "الحساب الخاص: أي أحد يبي يتابعك يرسل طلب وأنت تقبله أو ترفضه، وقهاويك ووصفاتك ونشاطك لمتابعينك بس. اسمك وصورتك ونبذتك تبقى ظاهرة.") {
                    SwitchItem(Icons.Filled.Lock, "حساب خاص", m.isPrivate) { v -> me = m.copy(isPrivate = v); patch { app.api.updateProfile(it, isPrivate = v) } }
                    if (m.isPrivate || requests > 0) Item(Icons.Filled.PersonAdd, "طلبات المتابعة", if (requests > 0) "$requests" else null) { nav.push(Route.FollowRequests) }
                    Item(Icons.Filled.Block, "المحظورين", null) { nav.push(Route.Blocked) }
                    listOf(Triple("followersVisibility", "مين يشوف متابعيني", m.followersVisibility), Triple("followingVisibility", "مين يشوف اللي أتابعهم", m.followingVisibility),
                        Triple("activityVisibility", "مين يشوف نشاطي", m.activityVisibility)).forEach { (field, title, value) ->
                        VisibilityItem(title, Visibility.of(value)) { v -> patch { app.api.updateProfile(it, visibility = field to v) } }
                    }
                    SwitchItem(Icons.Filled.VisibilityOff, "اخفني من المتصدرين", m.hideFromLeaderboard) { v -> me = m.copy(hideFromLeaderboard = v); patch { app.api.updateProfile(it, hideFromLeaderboard = v) } }
                }
            }
            SettingsGroup("عن التطبيق") {
                Item(Icons.Filled.Campaign, "تحديثات BunnList", null) { nav.push(Route.Updates) }
                if (signedIn) Item(Icons.Filled.Email, "تواصل معنا", null) { nav.push(Route.Feedback) }
                Item(Icons.Filled.Description, "الشروط والأحكام", null) { open("https://bunnlist.com/terms") }
                Item(Icons.Filled.PrivacyTip, "سياسة الخصوصية", null) { open("https://bunnlist.com/privacy") }
            }
            if (signedIn) SettingsGroup(null, "يحذف حسابك وكل بياناته نهائيًا.") {
                Item(Icons.AutoMirrored.Filled.Logout, "تسجيل الخروج", null) { confirmSignOut = true }
                Item(Icons.Filled.DeleteForever, if (deleting) "نحذف…" else "حذف الحساب", null, danger = true) { confirmDelete = true }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("BunnList ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text("تسجيل الخروج؟") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; scope.launch { com.bunnlist.app.Push.unregister(app); app.session.signOut(); nav.popAll(); onSignedOut() } }) { Text("اخرج") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("إلغاء") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("تحذف حسابك نهائيًا؟") },
        text = { Text("سيُحذف حسابك وبياناته على الخادم نهائيًا: مجموعتك وتقييماتك ووصفاتك وتعليقاتك. لا يمكن التراجع، ونفس الحساب في الآيفون بينحذف بعد.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false; deleting = true; error = null
                scope.launch {
                    try { app.session.withToken { app.api.deleteAccount(it) }; app.session.signOut(); nav.popAll(); onSignedOut() }
                    catch (e: ApiError) { error = "ما قدرنا نحذف الحساب الحين، جرّب مرة ثانية." }
                    deleting = false
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("احذف نهائيًا") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
}

@Composable
private fun SettingsGroup(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp)) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp), content = content)
        footer?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp)) }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, value: String?, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.primary)
        Text("  $title", color = color, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!danger) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text("  $title", modifier = Modifier.weight(1f)); Switch(on, onChange)
    }
}

@Composable
private fun VisibilityItem(title: String, value: Visibility, onChange: (Visibility) -> Unit) {
    var current by remember(value) { mutableStateOf(value) }
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable { open = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Visibility, null, tint = MaterialTheme.colorScheme.primary); Text("  $title", modifier = Modifier.weight(1f))
            Text(current.nameAr, color = MaterialTheme.colorScheme.onSurfaceVariant); Icon(Icons.Filled.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            Visibility.entries.forEach { v -> DropdownMenuItem(text = { Text(v.nameAr) }, onClick = { open = false; current = v; onChange(v) }) }
        }
    }
}

// MARK: Edit profile

/** «عدّل ملفك»: photo, name, @username and bio. */
@Composable
fun EditProfileScreen(app: BunnListApp, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<Me?>(null) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var check by remember { mutableStateOf<com.bunnlist.core.UsernameCheck?>(null) }
    var busy by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { app.session.withToken { app.api.me(it) } }.onSuccess { me = it; name = it.displayName ?: ""; bio = it.bio ?: ""; username = it.username ?: "" }
    }
    LaunchedEffect(username) {
        check = null
        val u = username.trim().lowercase()
        if (u.isEmpty() || u == me?.username) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        check = runCatching { app.session.withToken { app.api.checkUsername(u, it) } }.getOrNull()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photoBusy = true
        scope.launch {
            try {
                val b64 = withContext(Dispatchers.Default) { encode(context, uri, 512) } ?: throw IllegalStateException()
                me = app.session.withToken { app.api.setAvatar(b64, it) }
            } catch (e: Exception) { error = "ما قدرنا نرفع الصورة." }
            photoBusy = false
        }
    }
    val usernameOk = username.trim().lowercase().let { it == me?.username || (check?.available == true && check?.username == it) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        TopBar("عدّل ملفك", onBack = { nav.pop() })
        val m = me
        if (m == null) { Loading(); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.pressable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Avatar(m.avatarUrl, name.ifBlank { m.username }, 96.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    if (photoBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Filled.PhotoCamera, "غيّر الصورة", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            if (m.avatarUrl != null) TextButton(onClick = { scope.launch { runCatching { app.session.withToken { app.api.removeAvatar(it) } }.onSuccess { me = m.copy(avatarUrl = null) } } }) { Text("شيل الصورة") }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(username, { username = it.take(20).lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' } }, label = { Text("@username") }, singleLine = true,
                    prefix = { Text("@") }, modifier = Modifier.fillMaxWidth(),
                    supportingText = { val c = check; Text(when { username.trim().lowercase() == m.username -> "اسمك الحالي"; c == null -> "حروف إنجليزية صغيرة وأرقام و _ و ."; c.available -> "✓ متاح"; else -> c.problemAr ?: "" }) },
                    isError = check?.available == false)
            }
            OutlinedTextField(bio, { bio = it.take(160) }, label = { Text("نبذة") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), supportingText = { Text("${bio.length}/160") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !busy && name.isNotBlank() && usernameOk, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        app.session.withToken { t ->
                            val u = username.trim().lowercase()
                            if (u != m.username) app.api.setUsername(u, t)
                            app.api.updateProfile(t, displayName = name, bio = bio)
                        }
                        nav.pop()
                    } catch (e: ApiError) { error = if (e is ApiError.Conflict) "الاسم مستخدم، جرّب غيره" else "ما انحفظ، جرّب مرة ثانية." }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(if (busy) "نحفظ…" else "احفظ", fontWeight = FontWeight.Bold) }
        }
    }
}

// MARK: People lists

@Composable
fun FollowListScreen(app: BunnListApp, username: String, which: String, nav: Nav) {
    var people by remember { mutableStateOf<List<PersonCard>?>(null) }
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { people = app.api.followers(username, which, if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null) }
        catch (e: ApiError) { hidden = true }
    }
    PeopleScreen(if (which == "followers") "المتابعين" else "يتابع", people, if (hidden) "القائمة مخفية" else null, nav) { p -> PersonRow(app, p, nav) }
}

@Composable
fun FollowRequestsScreen(app: BunnListApp, nav: Nav) {
    var people by remember { mutableStateOf<List<PersonCard>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { people = runCatching { app.session.withToken { app.api.followRequests(it) } }.getOrDefault(emptyList()) }
    PeopleScreen("طلبات المتابعة", people, null, nav) { p ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatarUrl, p.displayName ?: p.username, 44.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f).pressable { nav.push(Route.Profile(p.username)) }) {
                Text(p.displayName ?: "@${p.username}", fontWeight = FontWeight.Bold); Text("@${p.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            fun answer(accept: Boolean) { people = people?.filterNot { it.username == p.username }; scope.launch { runCatching { app.session.withToken { app.api.answerFollowRequest(p.username, accept, it) } } } }
            Button(onClick = { answer(true) }) { Text("اقبل") }
            TextButton(onClick = { answer(false) }) { Text("ارفض") }
        }
    }
}

@Composable
fun BlockedScreen(app: BunnListApp, nav: Nav) {
    var people by remember { mutableStateOf<List<PersonCard>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { people = runCatching { app.session.withToken { app.api.blocked(it) } }.getOrDefault(emptyList()) }
    PeopleScreen("المحظورين", people, null, nav) { p ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatarUrl, p.displayName ?: p.username, 44.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(p.displayName ?: "@${p.username}", fontWeight = FontWeight.Bold); Text("@${p.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { people = people?.filterNot { it.username == p.username }; scope.launch { runCatching { app.session.withToken { app.api.setBlocked(p.username, false, it) } } } }) { Text("فك الحظر") }
        }
    }
}

@Composable
private fun PeopleScreen(title: String, people: List<PersonCard>?, message: String?, nav: Nav, row: @Composable (PersonCard) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar(title, onBack = { nav.pop() })
        when {
            message != null -> Empty(message, "صاحب الحساب مخفيها.")
            people == null -> Loading()
            people.isEmpty() -> Empty("ما فيه أحد هنا", "")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(people, key = { it.username }) { row(it) } }
        }
    }
}

// MARK: Notifications

@Composable
fun NotificationSettingsScreen(app: BunnListApp, nav: Nav) {
    var settings by remember { mutableStateOf<Map<NotificationKind, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { settings = runCatching { app.session.withToken { app.api.notificationSettings(it) } }.getOrNull() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("الإشعارات", onBack = { nav.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("توصلك تحديثات عن رحلتك مع القهوة. تقدر تختار وش يوصلك تحت.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val s = settings
            if (s == null) Loading() else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp)) {
                NotificationKind.entries.forEach { k ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(k.titleAr, fontWeight = FontWeight.SemiBold); Text(k.detailAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(s[k] ?: true, { on ->
                            settings = s + (k to on)
                            scope.launch { runCatching { app.session.withToken { app.api.setNotification(k, on, it) } }.onSuccess { settings = it }.onFailure { settings = s } }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun ComingSoon(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("قريبًا في نسخة أندرويد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
