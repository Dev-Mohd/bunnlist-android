package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.ChallengeState
import com.bunnlist.core.ForYouExtras
import com.bunnlist.core.RemoteLot
import com.bunnlist.core.RemoteLotDetail
import org.junit.Rule
import org.junit.Test

class ExtrasSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2600), maxPercentDifference = 100.0)
    private val lot = RemoteLot(id = "1", slug = "a", name = "", nameAr = "صدّام - اليمن", roasterNameAr = "محمصة أسانتي", originNameAr = "اليمن", originCode = "YE", process = "WASHED",
        flavorNotes = listOf("كراميل", "ياسمين", "توت"))

    @Test fun extras() = paparazzi.snapshot {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row { StreakBadge(6) }
                WeeklyChallengeCard(ChallengeState(ChallengeState.Challenge("c", "حضّر ٣ قهاوي إثيوبية", "جرّب الغسيل والمجفف", "☕", target = 3, points = 30,
                    filters = ChallengeState.Filters(originCode = "ET"), endsAt = "2030-01-01T00:00:00Z"), participants = 42, finished = 9, me = ChallengeState.Mine(2))) {}
                ResumeCard(ForYouExtras.Resume(lot, "2026-11-01T00:00:00Z"), {}, {})
                FriendsDrinkingCard(ForYouExtras.Friends(lot, listOf("سارة", "فهد"), 3)) {}
            }
        } }
    }

    @Test fun shareCard() = paparazzi.snapshot {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                ShareCardSheetContent(RemoteLotDetail(id = "1", slug = "a", name = "", nameAr = "صدّام - اليمن", roasterNameAr = "محمصة أسانتي", originNameAr = "اليمن", originCode = "YE",
                    process = "NATURAL", flavorNotes = listOf("كراميل", "ياسمين", "توت")), 4, 86)
            }
        } }
    }
}
