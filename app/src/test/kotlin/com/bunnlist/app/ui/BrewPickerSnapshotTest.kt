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
import com.bunnlist.core.BrewGroups
import com.bunnlist.core.RemoteRoasterRecipe
import org.junit.Rule
import org.junit.Test

/** The recipe tabs and cards of «حضّر», to compare with the mockup without a device. */
class BrewPickerSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400), maxPercentDifference = 100.0)

    private val roaster = RemoteRoasterRecipe(method = "V60", tool = "V60", doseG = 15.0, waterMl = 240, tempC = 92, timeSec = 165, grind = "متوسط ناعم",
        steps = listOf("تشريب ٤٠غ", "صب لين ١٥٠غ", "صب لين ٢٤٠غ"))

    @Test fun forCoffee() = paparazzi.snapshot {
        val g = BrewGroups.build("V60", "a", "أسانتي", listOf(roaster), emptyList(), emptyList(), emptyList())
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RecipePicker(g, BrewGroups.Tab.ForCoffee, "roaster:0", 2, {}, {}, {})
                RecipePicker(g, BrewGroups.Tab.BunnList, "v60-46", 1, {}, {}, {})
            }
        } }
    }

    @Test fun arabicCups() = paparazzi.snapshot {
        val g = BrewGroups.build("ARABIC", "a", "أسانتي", emptyList(), emptyList(), emptyList(), emptyList())
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RecipePicker(g, BrewGroups.Tab.BunnList, "arabic-saudi", 4, {}, {}, {})
            }
        } }
    }
}
