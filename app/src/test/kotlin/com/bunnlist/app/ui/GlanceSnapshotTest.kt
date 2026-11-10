package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewTimeline
import com.bunnlist.core.BrewVoice
import org.junit.Rule
import org.junit.Test

/** The hands-free glance board: gold with the scale reading while pouring, dark with the seconds while waiting. */
class GlanceSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)
    private val preset = BrewPreset.all.first { it.id == "v60-classic" }
    private val timeline = BrewTimeline(preset.steps)

    @Composable private fun Board(second: Int?) {
        val pos = timeline.position(second ?: 0)
        val mood = when {
            second == null -> null
            pos.isFinished -> BrewVoice.Mood.Done
            else -> BrewVoice.mood(preset.steps[pos.stepIndex])
        }
        val ink = if (mood == BrewVoice.Mood.Pour) Color.Black else Color.White
        BunnListTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(glanceColor(mood))) {
                    GlanceBoard(preset, pos, (second ?: 0).toDouble(), mood, null, true, true, ink, {}, Modifier.fillMaxSize().padding(top = 48.dpv))
                }
            }
        }
    }
    private val Int.dpv get() = androidx.compose.ui.unit.Dp(this.toFloat())

    private fun firstSecond(want: BrewVoice.Mood) =
        preset.steps.indices.first { BrewVoice.mood(preset.steps[it]) == want && (preset.steps[it].seconds ?: 0) > 0 }.let { timeline.start(it) + 2 }

    @Test fun ready() = paparazzi.snapshot { Board(null) }
    @Test fun pouring() = paparazzi.snapshot { Board(firstSecond(BrewVoice.Mood.Pour)) }
    @Test fun waiting() = paparazzi.snapshot { Board(firstSecond(BrewVoice.Mood.Wait)) }
    @Test fun done() = paparazzi.snapshot { Board(timeline.totalSeconds) }
}
