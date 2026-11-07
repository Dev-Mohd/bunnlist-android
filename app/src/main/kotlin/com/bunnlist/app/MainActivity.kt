package com.bunnlist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.bunnlist.app.ui.AppRoot
import com.bunnlist.app.ui.BunnListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BunnListApp
        com.bunnlist.app.ui.ThemeMode.load(this)
        setContent {
            BunnListTheme {
                // The app is Arabic-first: right-to-left whatever the phone's language.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    com.bunnlist.app.ui.UpdateGate(app) { AppRoot(app) }
                }
            }
        }
    }

    /** Switching the launcher alias while on screen can close the app on some launchers, so the icon changes as you leave. */
    override fun onStop() {
        super.onStop()
        runCatching { com.bunnlist.app.ui.AppIcons.applySeason(this) }
    }
}
