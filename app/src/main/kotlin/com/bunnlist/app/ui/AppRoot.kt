package com.bunnlist.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.BrewPreset

private enum class Tab(val label: String, val icon: ImageVector) {
    Discover("اكتشف", Icons.Filled.AutoAwesome),
    Collection("مجموعتي", Icons.Filled.Layers),
    Brew("حضّر", Icons.Filled.Coffee),
    Community("المجتمع", Icons.Filled.Groups),
    Account("حسابي", Icons.Filled.Person),
}

/** Screens pushed over the tabs. */
sealed interface Route {
    data class Lot(val slug: String) : Route
    data object Advisor : Route
    data object Feedback : Route
    data class Brewing(val preset: BrewPreset, val lotName: String?) : Route
}

/** What screens use to move around. */
class Nav(private val stack: MutableList<Route>, val openBrewTab: (method: String?, lotName: String?) -> Unit) {
    fun push(route: Route) { stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun popAll() { stack.clear() }
}

/** Welcome until signed in (or browsing as a guest), then the five tabs, as on iOS. */
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
    val stack = remember { mutableStateListOf<Route>() }
    val nav = remember { Nav(stack) { m, l -> stack.clear(); brewMethod = m; brewLot = l; tab = Tab.Brew } }

    BackHandler(enabled = stack.isNotEmpty()) { nav.pop() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Discover) { tab = Tab.Discover }

    val top = stack.lastOrNull()
    if (top != null) {
        // A Surface sets the text colour for the theme; without it pushed screens draw black text in dark mode.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { r ->
            when (r) {
                is Route.Lot -> LotScreen(app, r.slug, nav)
                Route.Advisor -> AdvisorScreen(app, nav)
                Route.Feedback -> FeedbackScreen(app, nav)
                is Route.Brewing -> BrewSessionScreen(r.preset, r.lotName, onClose = { nav.pop() })
            }
        }
        }
        return
    }
    Scaffold(bottomBar = {
        NavigationBar {
            Tab.entries.forEach { t ->
                NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Discover -> DiscoverScreen(app, nav)
                Tab.Brew -> BrewScreen(nav, initialMethod = brewMethod, lotName = brewLot, onLotCleared = { brewLot = null })
                Tab.Collection -> CollectionScreen(app, nav, signedIn, onSignIn = toWelcome)
                Tab.Account -> AccountScreen(app, nav, signedIn, onSignIn = toWelcome, onSignedOut = toWelcome)
                else -> ComingSoon(tab.label)
            }
        }
    }
}
