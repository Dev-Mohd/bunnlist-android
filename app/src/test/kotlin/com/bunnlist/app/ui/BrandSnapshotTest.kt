package com.bunnlist.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.RemoteLot
import org.junit.Rule
import org.junit.Test

class BrandSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    @Test fun brand() = paparazzi.snapshot {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().brandBackdrop(MaterialTheme.colorScheme.background).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandMark(120.dp); BrandWordmark(30)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BunnCoffeeCard(RemoteLot(id = "1", slug = "a", name = "", nameAr = "صدّام - اليمن", roasterNameAr = "محمصة أسانتي", originNameAr = "اليمن", originCode = "YE", process = "NATURAL",
                        flavorNotes = listOf("كراميل", "ياسمين", "توت"), averageRating = 4.5, reviewCount = 12), Modifier.weight(1f)) {}
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoffeeArtCard("محمصة الرياض", "جزيرة شيبارة", "كولومبيا", "CO", Modifier.fillMaxWidth().height(120.dp))
                        SkeletonBlock(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        } }
    }
}
