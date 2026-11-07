package com.bunnlist.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
    data object Advisor : Route
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
    data object FollowRequests : Route
    data object Blocked : Route
    data object NotificationSettings : Route
    data class FollowList(val username: String, val which: String) : Route
    data class Recipes(val lotSlug: String?, val lotName: String?) : Route
    data class BrewFinish(val preset: BrewPreset, val lotSlug: String, val lotName: String?, val seconds: Int) : Route
    data class Brewing(val preset: BrewPreset, val lotName: String?, val lotSlug: String? = null) : Route
    data object Journeys : Route
    data object Submissions : Route
    data class SuggestCoffee(val name: String, val roaster: String, val origin: String?) : Route
    data object AppIcon : Route
    data object Grinders : Route
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
    /** On wide screens, the coffee shown beside the list. */
    var selected by mutableStateOf<String?>(null)
    val inSearchMode: Boolean get() = searching || query.isNotBlank() || !filters.isEmpty
    fun exitSearch() { searching = false; query = ""; filters = BrowseFilters() }
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
    val width = LocalConfiguration.current.screenWidthDp
    val rail = width >= 600
    val twoPane = width >= 840

    BackHandler(enabled = stack.isNotEmpty()) { nav.pop() }
    BackHandler(enabled = stack.isEmpty() && tab == Tab.Discover && discover.inSearchMode) { discover.exitSearch() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Discover) { tab = Tab.Discover }

    Box(Modifier.fillMaxSize()) {
        // The tabs stay composed under pushed screens, so each keeps its scroll and state.
        Row(Modifier.fillMaxSize()) {
            if (rail) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach { t ->
                    NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary, selectedTextColor = MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
              // Content scrolls under the floating bar, as on iOS.
              val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
              CompositionLocalProvider(LocalBottomInset provides if (rail) navBottom else 92.dp + navBottom) {
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
              if (!rail) FloatingTabBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        // Pushed screens slide in like iOS (from the left in Arabic) and slide back out on return.
        AnimatedContent(stack.toList(), label = "route",
            transitionSpec = {
                val push = targetState.size >= initialState.size
                (if (push) slideInHorizontally(tween(320)) { -it } else fadeIn(tween(200))) togetherWith
                    (if (push) fadeOut(tween(250)) else slideOutHorizontally(tween(300)) { -it }) using SizeTransform(clip = false)
            },
            contentKey = { it.size to it.lastOrNull() }) { routes ->
            val r = routes.lastOrNull() ?: return@AnimatedContent
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (r) {
                    is Route.Lot -> LotScreen(app, r.slug, nav, openReview = r.openReview)
                    Route.Advisor -> AdvisorScreen(app, nav)
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
                    Route.AppIcon -> AppIconScreen(nav)
                    Route.Grinders -> GrinderGuideScreen(app, nav)
                    is Route.RecipeEditor -> RecipeEditorScreen(app, r.draft, nav)
                    Route.EditProfile -> EditProfileScreen(app, nav)
                    Route.FollowRequests -> FollowRequestsScreen(app, nav)
                    Route.Blocked -> BlockedScreen(app, nav)
                    Route.NotificationSettings -> NotificationSettingsScreen(app, nav)
                    is Route.FollowList -> FollowListScreen(app, r.username, r.which, nav)
                    is Route.Recipes -> RecipesScreen(app, r.lotSlug, r.lotName, nav)
                }
            }
        }
    }
}
