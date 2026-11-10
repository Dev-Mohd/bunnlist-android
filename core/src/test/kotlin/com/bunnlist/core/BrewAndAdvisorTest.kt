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
        val (cold, ready) = BrewPreset.presets("COLD_BREW")
        assertFalse(cold.usesTimer)
        assertEquals("1:10", cold.ratioText)
        assertEquals("1:12", ready.ratioText)
        assertEquals(listOf("dilute", "ice"), cold.steps.mapNotNull { it.addition })
        assertEquals(listOf("ice"), ready.steps.mapNotNull { it.addition })
        assertEquals("1:2", BrewPreset.presets("ESPRESSO").first().ratioText)
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

class BrewRedesignTest {
    private fun recipe(id: String, method: String = "V60", lot: String? = "nardos", mine: Boolean, likes: Int? = null, state: String = "published") =
        RemoteRecipe(id = id, clientId = "c$id", title = id, methodCode = method, lot = lot?.let { RemoteRecipe.Lot(slug = it, nameAr = "ق", roasterNameAr = "م") },
            state = state, authorName = "سارة", isMine = mine, likeCount = likes)

    @org.junit.Test fun cupsScaleEverythingButTime() {
        val arabic = BrewPreset.presets("ARABIC")[0]
        org.junit.Assert.assertEquals(10, arabic.baseCups)
        val four = arabic.scaled(4)
        org.junit.Assert.assertEquals(12.0, four.doseGrams, 0.0)
        org.junit.Assert.assertEquals(400.0, four.hotWaterGrams!!, 0.0)
        org.junit.Assert.assertEquals(400.0, four.steps[0].addedWaterGrams!!, 0.0)
        org.junit.Assert.assertEquals(arabic.totalSeconds, four.totalSeconds)
        val v60 = BrewPreset.presets("V60")[0]
        org.junit.Assert.assertEquals(1, v60.baseCups)
        org.junit.Assert.assertEquals(30.0, v60.scaled(2).doseGrams, 0.0)
        org.junit.Assert.assertEquals(v60, v60.scaled(1))
        val espresso = BrewPreset.presets("ESPRESSO")[0]
        org.junit.Assert.assertEquals(espresso, espresso.scaled(3))
    }

    @org.junit.Test fun groupsPutThisCoffeeFirst() {
        val roaster = RemoteRoasterRecipe(method = "V60", tool = "V60", doseG = 15.0, waterMl = 240, tempC = 92, timeSec = 165, grind = "متوسط",
            steps = listOf("تشريب ٤٠غ", "صب لين ١٥٠غ", "صب لين ٢٤٠غ"))
        val link = RemoteRoasterRecipe(method = "XBLOOM", link = "https://x")
        val g = BrewGroups.build("V60", "nardos", "أسانتي", listOf(link, roaster), listOf(recipe("m", mine = true)),
            listOf(recipe("few", mine = false, likes = 1), recipe("many", mine = false, likes = 9)),
            listOf(recipe("many", mine = false, likes = 9), recipe("rio", lot = "rio", mine = false, likes = 4)))
        org.junit.Assert.assertEquals(listOf("roaster:1", "recipe:many", "recipe:few"), g.forCoffee.map { it.id })
        org.junit.Assert.assertEquals("من أسانتي · وصفة المحمصة لهالقهوة", g.forCoffee[0].sourceAr)
        org.junit.Assert.assertEquals(listOf(55, 55, 55), g.forCoffee[0].steps.map { it.seconds })
        org.junit.Assert.assertEquals(listOf("recipe:rio"), g.community.map { it.id })
        org.junit.Assert.assertEquals(BrewGroups.Tab.ForCoffee, g.defaultTab)
        org.junit.Assert.assertEquals(BrewGroups.Tab.Mine, g.tabOf("recipe:m"))
        org.junit.Assert.assertEquals("v60-classic", g.bunnlist.first().id)
        org.junit.Assert.assertTrue(g.bunnlist.any { it.id == "v60-hoffmann" })
    }

    @org.junit.Test fun championsMatchTheirPublishedTotals() {
        val kasuya = BrewPreset.all.first { it.id == "v60-46" }
        org.junit.Assert.assertEquals(300.0, kasuya.steps.sumOf { it.addedWaterGrams ?: 0.0 }, 0.0)
        org.junit.Assert.assertEquals(210, kasuya.totalSeconds)
        org.junit.Assert.assertEquals("Tetsu Kasuya · بطل العالم لتحضير القهوة 2016", kasuya.sourceAr)
        org.junit.Assert.assertEquals(210, BrewPreset.all.first { it.id == "v60-hoffmann" }.totalSeconds)
        for (p in BrewPreset.champions) {
            org.junit.Assert.assertTrue(p.id, p.sourceUrl!!.startsWith("https://"))
            org.junit.Assert.assertEquals(p.id, p.hotWaterGrams!!, p.steps.sumOf { it.addedWaterGrams ?: 0.0 }, 0.0)
        }
        org.junit.Assert.assertEquals("16 د", brewMinutes(960))
        org.junit.Assert.assertEquals("16 د", brewMinutes(970))
        org.junit.Assert.assertEquals("2:45 د", brewMinutes(165))
    }

    @Test fun espressoHasItsMilkDrinksUnderIt() {
        val espresso = BrewPreset.presets("ESPRESSO")
        assertEquals(listOf("espresso-classic", "espresso-cortado", "espresso-flatwhite", "espresso-cappuccino", "espresso-latte"), espresso.map { it.id })
        assertEquals(listOf(MilkDrink.Cortado, MilkDrink.FlatWhite, MilkDrink.Cappuccino, MilkDrink.Latte), espresso.mapNotNull { it.milkDrink })
        espresso.forEach { assertEquals(it.id, 5, it.prepAr.size); assertEquals(28, it.steps.first().seconds); assertEquals("1:2", it.ratioText) }
        assertEquals(120.0, espresso[2].milkGrams)
        assertEquals(listOf(40.0, 120.0, 110.0, 220.0), MilkDrink.advisorChoices.map { it.typicalMilkGrams })
        assertEquals(listOf(120, 160, 180, 300), MilkDrink.advisorChoices.map { it.cupMl })
        assertTrue(MilkDrink.Cappuccino.airSeconds.first > MilkDrink.FlatWhite.airSeconds.last)
        assertTrue(MilkDrink.FlatWhite.steamingAr[0].contains("120غ"))
    }

    @Test fun advisorAsksForTheDrinkOnlyWithMilk() {
        var a = AdvisorAnswers(method = "ESPRESSO")
        assertFalse(AdvisorStep.MilkDrink in a.steps)
        a = a.copy(milk = true)
        assertEquals(listOf(AdvisorStep.Mood, AdvisorStep.Method, AdvisorStep.Milk, AdvisorStep.MilkDrink, AdvisorStep.Flavors, AdvisorStep.Serve, AdvisorStep.Adventure, AdvisorStep.Text), a.steps)
        assertEquals("espresso-cappuccino", a.copy(milkDrink = MilkDrink.Cappuccino).milkPreset?.id)
        assertNull(a.copy(milk = false, milkDrink = MilkDrink.Latte).milkPreset)
        assertNull(AdvisorAnswers(method = "V60", milk = true, milkDrink = MilkDrink.Latte).milkPreset)
    }
}
