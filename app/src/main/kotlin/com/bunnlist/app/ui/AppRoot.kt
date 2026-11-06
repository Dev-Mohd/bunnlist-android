package com.bunnlist.app.ui

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bunnlist.app.BunnListApp

private enum class Tab(val label: String, val icon: ImageVector) {
    Discover("اكتشف", Icons.Filled.AutoAwesome),
    Collection("مجموعتي", Icons.Filled.Layers),
    Brew("حضّر", Icons.Filled.Coffee),
    Community("المجتمع", Icons.Filled.Groups),
    Account("حسابي", Icons.Filled.Person),
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
    Scaffold(bottomBar = {
        NavigationBar {
            Tab.entries.forEach { t ->
                NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Discover -> DiscoverScreen(app)
                Tab.Account -> AccountScreen(app, signedIn = signedIn, onSignOut = { app.session.signOut(); signedIn = false; guest = false })
                else -> ComingSoon(tab.label)
            }
        }
    }
}
