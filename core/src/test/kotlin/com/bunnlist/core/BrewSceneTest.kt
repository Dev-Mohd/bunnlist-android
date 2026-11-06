package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

/** Same cases as the iOS BrewSceneTests, so both apps animate the same brew. */
class BrewSceneTest {
    private fun p(id: String) = BrewPreset.all.first { it.id == id }

    @Test fun sceneKindPerMethod() {
        assertEquals(BrewSceneKind.PourOver(false), BrewSceneKind.of(p("v60-classic")))
        assertEquals(BrewSceneKind.PourOver(true), BrewSceneKind.of(p("v60-iced")))
        assertEquals(BrewSceneKind.AeroPress, BrewSceneKind.of(p("aeropress-classic")))
        assertEquals(BrewSceneKind.Espresso, BrewSceneKind.of(p("espresso-classic")))
        assertEquals(BrewSceneKind.Arabic, BrewSceneKind.of(p("arabic-saudi")))
        assertEquals(BrewSceneKind.Ring, BrewSceneKind.of(p("chemex-classic")))
    }

    @Test fun readyBeforeStartAndDoneAfter() {
        assertEquals(SceneStage.Ready, BrewScene.state(p("v60-classic"), 0.0, false).stage)
        val done = BrewScene.state(p("v60-classic"), 999.0, true)
        assertEquals(SceneStage.Done, done.stage); assertEquals(1.0, done.drained, 0.0)
    }

    @Test fun bloomPoursQuicklyThenWaits() {
        val early = BrewScene.state(p("v60-classic"), 9.0, true)
        assertEquals(SceneStage.Bloom, early.stage)
        assertEquals(45.0 / 250 * 0.5, early.poured, 0.001)
        val late = BrewScene.state(p("v60-classic"), 30.0, true)
        assertEquals(45.0 / 250, late.poured, 0.001)
        assertEquals(15.0, late.secondsToNext ?: 0.0, 0.001)
    }

    @Test fun drainNeverExceedsPouredAndStandingRisesThenFalls() {
        var peak = 0.0
        for (t in 0..175) {
            val s = BrewScene.state(p("v60-classic"), t.toDouble(), true)
            assertTrue(s.drained <= s.poured + 1e-9)
            peak = maxOf(peak, s.standing)
        }
        assertTrue(peak > 0.2)
        assertTrue(BrewScene.state(p("v60-classic"), 174.0, true).standing < 0.05)
    }

    @Test fun cueInLastThreeSeconds() {
        assertTrue(BrewScene.state(p("v60-classic"), 43.0, true).isCueing)
        assertFalse(BrewScene.state(p("v60-classic"), 30.0, true).isCueing)
    }

    @Test fun arabicStagesFollowTheirOrder() {
        val stages = listOf(0.0, 190.0, 400.0, 805.0, 900.0, 950.0).map { BrewScene.state(p("arabic-saudi"), it, true).stage }
        assertEquals(listOf(SceneStage.Heat, SceneStage.AddCoffee, SceneStage.Simmer, SceneStage.AddCardamom, SceneStage.Settle, SceneStage.Serve), stages)
        assertEquals("على نار هادية", BrewScene.state(p("arabic-saudi"), 400.0, true).titleAr)
    }

    @Test fun espressoTarget() { assertEquals(25..31, BrewScene.espressoTarget(p("espresso-classic"))) }
}
