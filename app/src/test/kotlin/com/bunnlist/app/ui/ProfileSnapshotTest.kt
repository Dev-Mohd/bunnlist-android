package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.ProfileSummary
import com.bunnlist.core.PublicProfile
import org.junit.Rule
import org.junit.Test

class ProfileSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000), maxPercentDifference = 100.0)
    private val nav = Nav(mutableListOf(), { _, _ -> }, { _, _ -> }, {}, {})
    private val p = PublicProfile(username = "mohd", displayName = "محمد", bio = "أحب الإثيوبي المغسول ☕", points = 340, followers = 12, following = 30, isMe = true,
        recipes = listOf(PublicProfile.Recipe("1", "V60 صباحي", "V60", "صدّام - اليمن"), PublicProfile.Recipe("2", "إسبريسو حلو", "ESPRESSO")))
    private val s = ProfileSummary(followRequests = 2, taste = ProfileSummary.Taste(unlocked = true, averageRating = 4.2,
        topOrigins = listOf(ProfileSummary.Group("إثيوبيا", 4, 4.5), ProfileSummary.Group("كولومبيا", 2, 4.0)),
        topProcesses = listOf(ProfileSummary.Group("WASHED", 3)), topFlavors = listOf("ياسمين", "توت", "كراميل"), favoriteMethod = "V60"))
    private val coffees = (1..5).map { CollectionItem(lotId = "$it", slug = "s$it", nameAr = "قهوة رقم $it", roasterNameAr = "محمصة أسانتي", originNameAr = "إثيوبيا", originCode = "ET") }

    private fun shot(tab: ProfileTab) = paparazzi.snapshot {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ProfileBody(p, s, nav, tab, {}, "owned", {}, coffees, mapOf("owned" to 5, "tried" to 12, "favorite" to 3, "wantsToTry" to 7), emptyList())
            }
        } }
    }

    @Test fun coffees() = shot(ProfileTab.Coffees)
    @Test fun recipes() = shot(ProfileTab.Recipes)
}
