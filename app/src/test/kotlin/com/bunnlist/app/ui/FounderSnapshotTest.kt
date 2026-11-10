package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.Socials
import org.junit.Rule
import org.junit.Test

/** «مبدع مؤسس» pieces: seal, chip, ringed avatars and the linked accounts. */
class FounderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    @Test fun pieces() = paparazzi.snapshot {
        BunnListTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    FounderSeal(140.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FounderAvatar(null, "نورة", 76.dp, founder = true)
                        FounderAvatar(null, "فهد", 44.dp, founder = true)
                        FounderAvatar(null, "عبدالله", 44.dp, founder = false)
                    }
                    FounderChip(7)
                    FounderChip(7, compact = true)
                    SocialLinks(Socials(tiktok = "noura.brews", instagram = "noura.brews", x = "noura"))
                }
            }
        }
    }
}
