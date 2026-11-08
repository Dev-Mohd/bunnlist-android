package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

class RecipeEditingTest {
    private val base = RecipeDraft(doseGrams = 15.0, hotWaterGrams = 250.0, waterTempC = 92.0)

    @Test fun simplePoursSplitWaterAfterBloom() {
        val d = base.simplePours(3, bloom = true)
        assertTrue(d.hasBloom); assertEquals(3, d.pourCount)
        assertEquals(listOf(45.0, 68.0, 68.0, 69.0), d.steps.filter { it.kind.addsWater }.map { it.addedWaterGrams })
        assertEquals(250.0, d.pouredGrams, 0.0)
        assertEquals(listOf(0, 45, 75, 105, 135), d.startTimes)
        assertEquals(195, d.totalSeconds)
        assertFalse(d.waterMismatch)
        val noBloom = d.simplePours(2, bloom = false)
        assertFalse(noBloom.hasBloom); assertEquals(listOf(125.0, 125.0), noBloom.steps.filter { it.kind.addsWater }.map { it.addedWaterGrams })
    }

    @Test fun addedAndScaleReading() {
        val d = base.simplePours(3, true).setAdded(2, 73.0)
        assertEquals(113.0, d.scaleBefore(2), 0.0)
        assertEquals(186.0, d.cumulativeWater[2], 0.0)
        assertEquals(64.0, d.steps[3].addedWaterGrams!!, 0.0)
        assertEquals(250.0, d.pouredGrams, 0.0)
    }

    @Test fun movingKeepsDurationsAndRecomputesStarts() {
        val d = base.simplePours(2, true).addStep(ExtraStep.Stir)   // bloom, pour, pour, wait, stir
        val moved = d.moveStep(4, 1)
        assertEquals(StepKind.Stir, moved.steps[1].kind)
        assertEquals(listOf(0, 45, 55, 85, 115), moved.startTimes)
        assertEquals(d.totalSeconds, moved.totalSeconds)
    }

    @Test fun addPourTakesMissingWaterElseHalves() {
        val d = base.simplePours(2, true)
        val split = d.addPour()
        assertEquals(3, split.pourCount); assertEquals(250.0, split.pouredGrams, 0.0)
        val short = d.copy(hotWaterGrams = 270.0).addPour()
        assertEquals(20.0, short.steps[3].addedWaterGrams!!, 0.0)
    }

    @Test fun mismatchFixes() {
        val d = base.simplePours(3, true).copy(hotWaterGrams = 260.0)
        assertTrue(d.waterMismatch); assertEquals(10.0, d.waterShortfall, 0.0)
        assertEquals(260.0, d.giveShortfallToLastPour().pouredGrams, 0.0)
        assertEquals(250.0, d.adoptPouredTotal().hotWaterGrams!!, 0.0)
    }

    @Test fun additionsAreNotBrewWater() {
        val d = base.simplePours(2, true).addStep(ExtraStep.Dilute).addStep(ExtraStep.Ice)
        assertEquals(250.0, d.pouredGrams, 0.0)
        assertEquals(20.0, d.dilutionGrams, 0.0); assertEquals(100.0, d.iceAddedGrams, 0.0)
        assertFalse(d.waterMismatch)
        val steps = d.body()["steps"].toString()
        assertTrue(steps.contains("\"addition\":\"dilute\"")); assertTrue(steps.contains("\"additionTemp\":\"room\""))
        assertEquals("تخفيف", d.steps[d.steps.size - 2].titleAr())
    }

    @Test fun temperatures() {
        var d = base.simplePours(2, true)
        d = d.copy(steps = d.steps.toMutableList().also { it[1] = it[1].copy(tempC = 88.0) })
        assertEquals(88.0, d.stepTemp(1)!!, 0.0); assertEquals(92.0, d.stepTemp(2)!!, 0.0)
        assertEquals("جهّز الماء على 92°م", d.prepLineAr)
        assertEquals("صبّة 1 على 88°", d.customTempsAr)
        assertNull(d.copy(waterTempC = null).prepLineAr)
        assertTrue(d.body()["steps"].toString().contains("\"tempC\":88.0"))
    }
}
