package com.bunnlist.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test





class BrewAndAdvisorTest {
    private val classic = BrewPreset.all.first { it.id == "v60-classic" }

    @Test fun timelineWalksStepsAndPourTargets() {
        val t = BrewTimeline(classic.steps)
        assertEquals(175, t.totalSeconds)
        assertEquals(BrewTimeline.Position(0, 45, 45.0, false), t.position(0))
        assertEquals(BrewTimeline.Position(1, 30, 150.0, false), t.position(45))
        assertEquals(BrewTimeline.Position(2, 1, 250.0, false), t.position(104))
        val swirl = t.position(105)
        assertEquals(3, swirl.stepIndex); assertNull(swirl.pourTo)
        assertTrue(t.position(175).isFinished)
        assertEquals(105, t.start(3))
    }

    @Test fun coldBrewIsAChecklist() {
        val cold = BrewPreset.presets("COLD_BREW").single()
        assertFalse(cold.usesTimer)
        assertEquals("1:10", cold.ratioText)
        assertEquals("1:2", BrewPreset.presets("ESPRESSO").single().ratioText)
        assertEquals("1:16", BrewPreset.all.first { it.id == "v60-iced" }.ratioText)
    }

    @Test fun everyMethodHasAPreset() {
        BrewPreset.methods.forEach { assertTrue(it, BrewPreset.presets(it).isNotEmpty()) }
    }

    @Test fun advisorStepsAndBody() {
        var a = AdvisorAnswers(mood = "wake", method = "V60", serve = "HOT")
        assertEquals(6, a.steps.size)
        assertTrue(a.isComplete)
        a = a.copy(method = "ESPRESSO")
        assertEquals(AdvisorStep.Milk, a.steps[2])
        assertFalse(a.isComplete)
        a = a.copy(milk = true, families = setOf(FlavorFamily.Chocolate, FlavorFamily.Berry, FlavorFamily.Other), adventure = 0.333, text = "  توت  ")
        val body = a.body()
        assertEquals(listOf("berry", "chocolate"), body["families"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("0.33", body["adventure"]!!.jsonPrimitive.content)
        assertEquals("true", body["milk"]!!.jsonPrimitive.content)
        assertEquals("توت", body["text"]!!.jsonPrimitive.content)
        assertNull(AdvisorAnswers(mood = "calm", method = "V60", serve = "ICED").body()["text"])
    }

    @Test fun flavorFamilies() {
        assertEquals(FlavorFamily.Citrus, FlavorFamily.of("يوسفي"))
        assertEquals(FlavorFamily.Floral, FlavorFamily.of("Jasmine"))
        assertEquals(FlavorFamily.Other, FlavorFamily.of("??"))
    }

    @Test fun formatting() {
        assertEquals("30 ث", Methods.clock(30)); assertEquals("2:05", Methods.clock(125))
        assertEquals("18", Methods.grams(18.0)); assertEquals("18.5", Methods.grams(18.5))
    }
}
