package com.bunnlist.app.ui

import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrowseFilters
import com.bunnlist.core.RemoteLot

enum class Tab(val label: String, val icon: ImageVector) {
    Discover("اكتشف", Icons.Filled.AutoAwesome),
    Collection("مجموعتي", Icons.Filled.Layers),
    Brew("حضّر", Icons.Filled.Coffee),
    Community("المجتمع", Icons.Filled.Groups),
    Account("حسابي", Icons.Filled.Person),
}

/** Screens pushed over the tabs. */
sealed interface Route {
    data class Lot(val slug: String, val openReview: Boolean = false) : Route
    /** The session lives in the route, so answers and results survive opening a coffee and coming back. */
    class Advisor(val session: AdvisorSession = AdvisorSession()) : Route
    data object Feedback : Route
    data object Updates : Route
    data object FindPeople : Route
    data class Profile(val username: String) : Route
    data object Roasters : Route
    data class Roaster(val slug: String) : Route
    data object OriginsMap : Route
    data class ListPage(val id: String) : Route
    data object Passport : Route
    data object Settings : Route
    data object GrindConverter : Route
    data class RecipeEditor(val draft: com.bunnlist.core.RecipeDraft) : Route
    data object EditProfile : Route
    data object FounderSocials : Route
    data object FollowRequests : Route
    data object Blocked : Route
    data object NotificationSettings : Route
    data class FollowList(val username: String, val which: String) : Route
    data class Recipes(val lotSlug: String?, val lotName: String?, val openId: String? = null) : Route
    data class BrewFinish(val preset: BrewPreset, val lotSlug: String, val lotName: String?, val seconds: Int) : Route
    data class Brewing(val preset: BrewPreset, val lotName: String?, val lotSlug: String? = null) : Route
    data object Journeys : Route
    data object Submissions : Route
    data class SuggestCoffee(val name: String, val roaster: String, val origin: String?) : Route
    data object AddCoffee : Route
    data object AppIcon : Route
    data object Grinders : Route
    data object TeamMessages : Route
}

/** Discover's search, filters, results and scroll, kept while you open a coffee and come back. */
class DiscoverState {
    var query by mutableStateOf("")
    var filters by mutableStateOf(BrowseFilters())
    var searching by mutableStateOf(false)
    var lots by mutableStateOf<List<RemoteLot>>(emptyList())
    var total by mutableIntStateOf(0)
    var loadedKey by mutableStateOf<String?>(null)
    val listState = LazyListState()
    /** The home page's data, so returning to the tab doesn't reload it from the top. */
    val home = DiscoverHomeData()
    /** On wide screens, the coffee shown beside the list. */
    var selected by mutableStateOf<String?>(null)
    val inSearchMode: Boolean get() = searching || query.isNotBlank() || !filters.isEmpty
    fun exitSearch() { searching = false; query = ""; filters = BrowseFilters() }
}

class DiscoverHomeData {
    var me by mutableStateOf<com.bunnlist.core.Me?>(null)
    var forYou by mutableStateOf<com.bunnlist.core.ForYou?>(null)
    var trending by mutableStateOf<List<com.bunnlist.core.TrendingLot>>(emptyList())
    var newest by mutableStateOf<List<RemoteLot>?>(null)
    var catalogTotal by mutableStateOf<Int?>(null)
    var flavor by mutableStateOf<com.bunnlist.core.FlavorFamily?>(null)
    var flavorLots by mutableStateOf<List<RemoteLot>?>(null)
    var flavorLotsFor: com.bunnlist.core.FlavorFamily? = null
    var flavorTotal by mutableStateOf<Int?>(null)
    var extras by mutableStateOf<com.bunnlist.core.ForYouExtras?>(null)
    var streak by mutableIntStateOf(0)
    var facets by mutableStateOf<com.bunnlist.core.Facets?>(null)
}

/** What screens use to move around. */
class Nav(
    private val stack: MutableList<Route>,
    val openBrewTab: (method: String?, lotName: String?) -> Unit,
    val openRoaster: (slug: String, nameAr: String) -> Unit,
    val openOrigin: (code: String) -> Unit,
    val openTab: (Tab) -> Unit,
) {
    fun push(route: Route) { stack.add(route) }
    /** Discover's search with these filters (the challenge's «القهاوي اللي تحسب»). */
    var showCoffees: (BrowseFilters) -> Unit = {}
    /** Bumps when a pushed screen closes, so tabs can refresh what it may have changed. */
    var version by mutableIntStateOf(0)
    fun pop() { if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); version++ } }
    fun popAll() { stack.clear(); version++ }
    /** After a guided brew of a coffee: «كيف طلعت؟» in place of the timer. */
    fun finishBrew(preset: BrewPreset, slug: String, lotName: String?, seconds: Int) {
        pop(); push(Route.BrewFinish(preset, slug, lotName, seconds))
    }
    /** Back to a coffee's page with the rating open. */
    fun rateLot(slug: String) {
        pop()
        val i = stack.indexOfLast { it is Route.Lot && it.slug == slug }
        if (i >= 0) stack[i] = Route.Lot(slug, openReview = true) else stack.add(Route.Lot(slug, openReview = true))
    }
}

/** Welcome until signed in (or browsing as a guest), then the five tabs. A rail replaces the bottom bar on wide screens. */
@Composable
fun AppRoot(app: BunnListApp) {
    var signedIn by rememberSaveable { mutableStateOf(app.session.isSignedIn) }
    var guest by rememberSaveable { mutableStateOf(false) }
    if (!signedIn && !guest) {
        WelcomeScreen(app, onSignedIn = { signedIn = true }, onGuest = { guest = true })
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Discover) }
    val toWelcome = { signedIn = false; guest = false }
    // Right after signing in (or opening the app signed in): the drawing mark until the profile is here,
    // then the first-run setup once per account, as on iOS.
    var me by remember(signedIn) { mutableStateOf<com.bunnlist.core.Me?>(null) }
    var meLoaded by remember(signedIn) { mutableStateOf(!signedIn) }
    var onboardingDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        me = runCatching { app.session.withToken { app.api.me(it) } }.getOrNull(); meLoaded = true
        com.bunnlist.app.Push.register(app)
    }
    // Ask for notifications once, after setup (Android 13+ needs the permission).
    val context = androidx.compose.ui.platform.LocalContext.current
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    val readyForAsk = signedIn && meLoaded && (me?.onboarded == true || onboardingDone)
    LaunchedEffect(readyForAsk) {
        val prefs = context.getSharedPreferences("ui", 0)
        if (readyForAsk && android.os.Build.VERSION.SDK_INT >= 33 && !com.bunnlist.app.Push.canNotify(context) && !prefs.getBoolean("askedPush", false)) {
            prefs.edit().putBoolean("askedPush", true).apply()
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (signedIn && !meLoaded) { BrandLoadingView("نجهّز حسابك…"); return }
    me?.takeIf { !it.onboarded && !onboardingDone }?.let { m -> OnboardingFlow(app, m) { onboardingDone = true }; return }
    var brewMethod by remember { mutableStateOf<String?>(null) }
    var brewLot by remember { mutableStateOf<String?>(null) }
    val discover = remember { DiscoverState() }
    val stack = remember { mutableStateListOf<Route>() }
    val nav = remember {
        Nav(stack,
            openBrewTab = { m, l -> stack.clear(); brewMethod = m; brewLot = l; tab = Tab.Brew },
            openRoaster = { slug, _ -> stack.add(Route.Roaster(slug)) },
            openOrigin = { code -> stack.clear(); discover.query = ""; discover.filters = BrowseFilters(origins = setOf(code)); discover.searching = true; tab = Tab.Discover },
            openTab = { t -> stack.clear(); tab = t }).also { n ->
            n.showCoffees = { f -> stack.clear(); discover.query = ""; discover.filters = f; discover.searching = true; tab = Tab.Discover }
        }
    }
    @Composable fun RouteContent(r: Route) {
        when (r) {
            is Route.Lot -> LotScreen(app, r.slug, nav, openReview = r.openReview)
            is Route.Advisor -> AdvisorScreen(app, r.session, nav)
            Route.Feedback -> FeedbackScreen(app, nav)
            Route.Updates -> UpdatesScreen(app, nav)
            Route.FindPeople -> FindPeopleScreen(app, nav)
            is Route.Profile -> ProfileScreen(app, r.username, nav)
            Route.Roasters -> RoastersScreen(app, nav)
            is Route.Roaster -> RoasterScreen(app, r.slug, nav)
            Route.OriginsMap -> OriginsMapScreen(app, nav)
            is Route.ListPage -> ListScreen(app, r.id, nav)
            is Route.Brewing -> BrewSessionScreen(r.preset, r.lotName, onClose = { nav.pop() },
                onRate = r.lotSlug?.let { slug -> { secs: Int -> nav.finishBrew(r.preset, slug, r.lotName, secs) } })
            is Route.BrewFinish -> BrewFinishScreen(app, r, nav)
            Route.Passport -> PassportScreen(app, nav)
            Route.Settings -> SettingsScreen(app, nav, onSignedOut = toWelcome)
            Route.GrindConverter -> GrindConverterScreen(nav)
            Route.Journeys -> JourneysScreen(app, nav)
            Route.Submissions -> SubmissionsScreen(app, nav)
            is Route.SuggestCoffee -> SuggestCoffeeScreen(app, r.name, r.roaster, r.origin, nav)
            Route.AddCoffee -> AddCoffeeScreen(app, nav)
            Route.AppIcon -> AppIconScreen(nav)
            Route.Grinders -> GrinderGuideScreen(app, nav)
            Route.TeamMessages -> TeamMessagesScreen(app, nav)
            is Route.RecipeEditor -> RecipeEditorScreen(app, r.draft, nav)
            Route.EditProfile -> EditProfileScreen(app, nav)
            Route.FounderSocials -> FounderSocialsScreen(app, nav)
            Route.FollowRequests -> FollowRequestsScreen(app, nav)
            Route.Blocked -> BlockedScreen(app, nav)
            Route.NotificationSettings -> NotificationSettingsScreen(app, nav)
            is Route.FollowList -> FollowListScreen(app, r.username, r.which, nav)
            is Route.Recipes -> RecipesScreen(app, r.lotSlug, r.lotName, nav, r.openId)
        }
    }
    val width = LocalConfiguration.current.screenWidthDp
    val rail = width >= 600
    val twoPane = width >= 840

    // Pushed screens live on a real stack, as on iOS: every screen stays composed underneath (so going back
    // finds it as it was left), the top one slides in and out, and a back swipe drags it with the finger.
    val shown = remember { mutableStateListOf<StackEntry>() }
    val stateHolder = rememberSaveableStateHolder()
    val stackScope = rememberCoroutineScope()
    var nextEntryId by remember { mutableIntStateOf(0) }
    val widthPx = with(LocalDensity.current) { width.dp.toPx() }
    // Keeps what's on screen in step with the route list: screens that left slide out (the top one) and are
    // forgotten, new ones slide in. A swap at the same depth (the brew timer handing over to «كيف طلعت؟»)
    // comes in underneath the one leaving.
    LaunchedEffect(Unit) {
        snapshotFlow { stack.toList() }.collect { target ->
            val live = shown.filter { !it.leaving }
            var same = 0
            while (same < live.size && same < target.size && live[same].route == target[same]) same++
            val gone = live.drop(same)
            gone.forEach { it.leaving = true }
            gone.dropLast(1).forEach { shown.remove(it); stateHolder.removeState(it.id) }
            gone.lastOrNull()?.let { top ->
                stackScope.launch {
                    top.offset.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                    shown.remove(top); stateHolder.removeState(top.id)
                }
            }
            val fresh = target.drop(same)
            fresh.forEachIndexed { j, route ->
                val slides = j == fresh.lastIndex && gone.isEmpty()
                val entry = StackEntry(nextEntryId++, route, if (slides) 1f else 0f)
                val at = shown.indexOfFirst { it.leaving }.let { if (it < 0) shown.size else it }
                shown.add(at, entry)
                if (slides) stackScope.launch { entry.offset.animateTo(0f, tween(340, easing = FastOutSlowInEasing)) }
            }
        }
    }
    // The back swipe: the top screen goes where the finger goes. Letting go past the system's threshold
    // pops it (it finishes leaving from where it is); letting go early puts it back.
    PredictiveBackHandler(enabled = stack.isNotEmpty()) { progress ->
        val top = shown.lastOrNull { !it.leaving }
        try {
            progress.collect { e ->
                if (top != null && widthPx > 0f) {
                    val fromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                    top.dir = if (fromLeft) 1f else -1f
                    top.offset.snapTo(((if (fromLeft) e.touchX else widthPx - e.touchX) / widthPx).coerceIn(0f, 1f))
                }
            }
            nav.pop()
        } catch (e: CancellationException) {
            if (top != null) stackScope.launch { top.offset.animateTo(0f, tween(200)) }
            throw e
        }
    }
    // «اكتشف» again from search goes back to the home page, where you were.
    // Tapping «اكتشف» while already on its home page goes back to the top, as on iOS.
    val tabScope = rememberCoroutineScope()
    fun selectTab(t: Tab) {
        val wasHome = stack.isEmpty() && t == Tab.Discover && tab == Tab.Discover && !discover.inSearchMode
        if (stack.isNotEmpty()) nav.popAll()
        if (t == Tab.Discover && tab == Tab.Discover && discover.inSearchMode) discover.exitSearch()
        if (wasHome) tabScope.launch { discover.listState.animateScrollToItem(0) }
        tab = t
    }
    BackHandler(enabled = stack.isEmpty() && tab == Tab.Discover && discover.inSearchMode) { discover.exitSearch() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Discover) { tab = Tab.Discover }

    Box(Modifier.fillMaxSize()) {
        // The tabs stay composed under pushed screens, so each keeps its scroll and state.
        Row(Modifier.fillMaxSize()) {
            if (rail) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { t ->
                    NavigationRailItem(selected = tab == t, onClick = { selectTab(t) }, icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary, selectedTextColor = MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
              // Content scrolls under the floating bar, as on iOS.
              val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
              CompositionLocalProvider(LocalBottomInset provides if (rail) navBottom else 92.dp + navBottom, LocalTipsEnabled provides stack.isEmpty()) {
                Crossfade(tab, Modifier.fillMaxSize(), animationSpec = tween(220), label = "tab") { tab ->
                    when (tab) {
                        Tab.Discover -> if (twoPane) Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.45f)) { DiscoverScreen(app, nav, discover, signedIn, onSelect = { discover.selected = it }) }
                            VerticalDivider()
                            Box(Modifier.weight(0.55f)) {
                                val sel = discover.selected
                                if (sel != null) LotScreen(app, sel, nav, embedded = true)
                                else Empty("اختر قهوة", "تفاصيلها بتطلع هنا.")
                            }
                        } else DiscoverScreen(app, nav, discover, signedIn, onSelect = { nav.push(Route.Lot(it)) })
                        Tab.Collection -> CollectionScreen(app, nav, signedIn, onSignIn = toWelcome)
                        Tab.Brew -> BrewScreen(app, nav, signedIn, onSignIn = toWelcome, focusSlug = brewLot, initialMethod = brewMethod)
                        Tab.Community -> CommunityScreen(app, nav, signedIn, onSignIn = toWelcome)
                        Tab.Account -> AccountScreen(app, nav, signedIn, onSignIn = toWelcome, onSignedOut = toWelcome)
                    }
                }
              }
              if (!rail) FloatingTabBar(tab, onSelect = { selectTab(it) }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        // Pushed screens slide in like iOS (from the left in Arabic) and slide back out on return.
        // A light shade over the tabs while a screen sits on them, lifting as it leaves.
        shown.firstOrNull()?.let { first ->
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.16f * (1f - first.offset.value) }.background(Color.Black))
        }
        shown.forEachIndexed { i, entry ->
            key(entry.id) {
                val above = shown.getOrNull(i + 1)
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = entry.dir * entry.offset.value * size.width
                    // The screen underneath waits a little to the side and comes back as the one above leaves.
                    if (above != null) translationX -= above.dir * (1f - above.offset.value) * size.width * 0.22f
                    // Only the top screen and the one under it are ever seen.
                    alpha = if (i < shown.size - 3) 0f else 1f
                }) {
                    stateHolder.SaveableStateProvider(entry.id) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { RouteContent(entry.route) }
                    }
                    if (above != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.16f * (1f - above.offset.value) }.background(Color.Black))
                }
            }
        }
    }
}

/** One pushed screen on the stack, and where it is on its way in or out: 0 is in place, 1 is off screen. */
private class StackEntry(val id: Int, val route: Route, start: Float) {
    val offset = Animatable(start)
    /** Which way it leaves: -1 to the left (how screens come and go in Arabic), +1 for a swipe from the left edge. */
    var dir by mutableFloatStateOf(-1f)
    var leaving by mutableStateOf(false)
}
