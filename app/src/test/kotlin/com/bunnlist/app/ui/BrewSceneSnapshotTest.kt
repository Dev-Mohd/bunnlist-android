package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewScene
import com.bunnlist.core.BrewSceneKind
import org.junit.Rule
import org.junit.Test

/** Renders the brew scenes at a few moments so they can be checked against iOS without a device. */
class BrewSceneSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    private fun row(id: String, times: List<Double>) {
        val p = BrewPreset.all.first { it.id == id }
        paparazzi.snapshot(id) {
            androidx.compose.foundation.layout.Column(Modifier.background(Color.Black)) {
                times.chunked(3).forEach { r -> Row { r.forEach { t -> BrewSceneCanvas(BrewSceneKind.of(p), BrewScene.state(p, t, t > 0), clock = t, modifier = Modifier.size(130.dp, 140.dp)) } } }
            }
        }
    }

    @Test fun v60() = row("v60-classic", listOf(0.0, 8.0, 30.0, 55.0, 110.0, 150.0, 180.0))
    @Test fun v60Iced() = row("v60-iced", listOf(0.0, 10.0, 60.0, 130.0, 175.0, 200.0))
    @Test fun aeropress() = row("aeropress-classic", listOf(0.0, 8.0, 35.0, 80.0, 128.0, 150.0, 170.0))
    @Test fun arabic() = row("arabic-saudi", listOf(0.0, 150.0, 190.0, 400.0, 805.0, 900.0, 960.0, 1000.0))
    @Test fun espresso() {
        val p = BrewPreset.all.first { it.id == "espresso-classic" }
        paparazzi.snapshot("espresso") {
            androidx.compose.foundation.layout.Column(Modifier.background(Color.Black)) { Row {
                listOf(2.0 to true, 10.0 to true, 28.0 to false).forEach { (t, r) ->
                    EspressoSceneCanvas(t, r, BrewScene.espressoTarget(p), t, Modifier.size(130.dp, 140.dp))
                }
            } }
        }
    }
}
