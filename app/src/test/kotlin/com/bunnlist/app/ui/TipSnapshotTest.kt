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
import org.junit.Rule
import org.junit.Test

class TipSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    @Test fun tips() = paparazzi.snapshot {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TipBubble(AppTip.Advisor, pointsDown = false, anchorCenterX = 700f) {}
                TipBubble(AppTip.Brew, pointsDown = true, anchorCenterX = 400f) {}
                TipBubble(AppTip.Updates, pointsDown = false, anchorCenterX = 200f) {}
            }
        } }
    }
}
