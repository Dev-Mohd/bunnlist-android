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
import com.bunnlist.core.*
import org.junit.Rule
import org.junit.Test

/** The new recipe editor parts, to compare with the approved mockup without a device. */
class RecipeEditorSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2600), maxPercentDifference = 100.0)
    private val d = RecipeDraft(doseGrams = 16.0, hotWaterGrams = 267.0, waterTempC = 93.0).simplePours(3, true).let {
        it.copy(steps = it.steps.toMutableList().also { s -> s[2] = s[2].copy(tempC = 88.0, note = "صب من النص للخارج ببطء") })
    }.addStep(ExtraStep.Dilute)

    private fun shot(content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) = paparazzi.snapshot {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        } }
    }

    @Test fun simple() = shot { BasicsCard(d) {}; SimpleStepsCard(d) {} }
    @Test fun advanced() = shot { AdvancedStepsCard(d) {}; WaterCheck(d.copy(hotWaterGrams = 277.0)) {} }
    @Test fun review() = shot { RecipeReview(d) }
}
