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
import androidx.compose.ui.Modifier
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
    data class Brewing(val preset: BrewPreset, val lotName: String?, val lotSlug: String? = null) : Route
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
) {
    fun push(route: Route) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** After a guided brew of a coffee: back to its page with the rating open. */
    fun rateAfterBrew(slug: String) {
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
    var brewMethod by remember { mutableStateOf<String?>(null) }
    var brewLot by remember { mutableStateOf<String?>(null) }
    val discover = remember { DiscoverState() }
    val stack = remember { mutableStateListOf<Route>() }
    val nav = remember {
        Nav(stack,
            openBrewTab = { m, l -> stack.clear(); brewMethod = m; brewLot = l; tab = Tab.Brew },
            openRoaster = { slug, _ -> stack.clear(); discover.query = ""; discover.filters = BrowseFilters(roasters = setOf(slug)); discover.searching = true; tab = Tab.Discover })
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
            Scaffold(modifier = Modifier.weight(1f), bottomBar = {
                if (!rail) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary, selectedTextColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }) { padding ->
                Crossfade(tab, Modifier.fillMaxSize().padding(padding), animationSpec = tween(220), label = "tab") { tab ->
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
                        Tab.Brew -> BrewScreen(nav, initialMethod = brewMethod, lotName = brewLot, onLotCleared = { brewLot = null })
                        Tab.Community -> CommunityScreen(app, nav, signedIn, onSignIn = toWelcome)
                        Tab.Account -> AccountScreen(app, nav, signedIn, onSignIn = toWelcome, onSignedOut = toWelcome)
                    }
                }
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
                    is Route.Brewing -> BrewSessionScreen(r.preset, r.lotName, onClose = { nav.pop() },
                        onRate = r.lotSlug?.let { slug -> { nav.rateAfterBrew(slug) } })
                }
            }
        }
    }
}
