package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.MilkDrink
import org.junit.Rule
import org.junit.Test

/** The drawn brewer icons, the milk drink glasses and the milk guide card. */
class BrewIconsSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    @Test fun icons() = paparazzi.snapshot {
        BunnListTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(BrewIconKind.V60, BrewIconKind.Espresso, BrewIconKind.Chemex, BrewIconKind.FrenchPress).forEach { BrewIcon(it, 72.dp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(BrewIconKind.Hot, BrewIconKind.Iced, BrewIconKind.Milk, BrewIconKind.Black).forEach { BrewIcon(it, 72.dp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MilkDrink.advisorChoices.forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { MilkDrinkGlass(d, 72.dp); Text(d.nameAr, fontSize = 12.sp) }
                        }
                    }
                    MilkGuideCard(MilkDrink.FlatWhite)
                }
            }
        }
    }
}
