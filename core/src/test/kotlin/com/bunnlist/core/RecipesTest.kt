package com.bunnlist.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RecipesTest {
    @Test fun recipeBecomesPreset() {
        val r = RemoteRecipe(id = "r1", title = "V60 سارة", methodCode = "V60", doseGrams = 15.0, hotWaterGrams = 250.0, waterTempC = 93.0,
            grinder = "Comandante", grindSetting = "24", authorName = "سارة", likeCount = 3,
            steps = listOf(RemoteRecipeStep("bloom", 45.0, 40, "تفتيح"), RemoteRecipeStep("pour", 205.0, 60)))
        val p = r.toPreset()
        assertEquals("24 على Comandante", p.grindAr); assertEquals(StepKind.Bloom, p.steps[0].kind); assertEquals(100, p.totalSeconds)
        assertEquals("community", p.source); assertEquals("من سارة · 3 إعجاب", p.sourceAr)
    }

    @Test fun tasteFixes() {
        assertEquals("زِد البن إلى 16غ", TasteFix.suggest(BrewTaste.Weak, "V60", 15.0, 250.0).titleAr)
        assertEquals("طوّل الناتج إلى 40غ", TasteFix.suggest(BrewTaste.Strong, "ESPRESSO", 18.0, 36.0).titleAr)
        assertEquals("ثبّتها", TasteFix.suggest(BrewTaste.Balanced, "V60", null, null).titleAr)
    }

    @Test fun experienceBody() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"id":"e1","methodCode":"V60","stars":4}"""))
        server.start()
        val api = ApiClient(server.url("/").toString())
        api.saveExperience("s", BrewPreset.all.first(), 4, 175, BrewTaste.Sour, " حلوة ", false, "t")
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("sour", body["taste"]!!.jsonPrimitive.content); assertEquals("base", body["recipeSource"]!!.jsonPrimitive.content)
        assertEquals("250.0", body["waterGrams"]!!.jsonPrimitive.content); assertEquals("حلوة", body["note"]!!.jsonPrimitive.content)
        assertEquals("175", body["brewSeconds"]!!.jsonPrimitive.content)
        server.shutdown()
    }
}

class RecipeDraftTest {
    @Test fun presetDraftIsPublishableOnceNamed() {
        val d = RecipeDraft.from(BrewPreset.all.first { it.id == "v60-classic" }, "slug")
        assertEquals(listOf("اكتب اسم للوصفة"), d.problemsAr())
        assertTrue(d.copy(title = "صباحي").problemsAr().isEmpty())
        assertEquals(1, d.copy(title = "x", hotWaterGrams = 300.0).problemsAr().size)
        val body = d.copy(title = "x").body()
        assertEquals("bloom", body["steps"]!!.let { (it as kotlinx.serialization.json.JsonArray)[0] as kotlinx.serialization.json.JsonObject }["kind"]!!.jsonPrimitive.content)
        assertEquals("null", body["yieldGrams"].toString())
    }
}

class GrindTest {
    @Test fun converts() {
        val c40 = Grinder.all.first()
        assertEquals(720, c40.microns(24.0)); assertEquals(GrindLevel.Medium, GrindLevel.of(720))
        assertEquals(36, c40.equivalents(24.0).first { it.first.id == "1zpresso-k-ultra" }.second)
    }
}

class ExtrasTest {
    @Test fun seasonsAndLabels() {
        assertEquals(AppIconChoice.NationalDay, AppIconChoice.season(java.time.LocalDate.of(2026, 9, 23)))
        assertEquals(AppIconChoice.Ramadan, AppIconChoice.season(java.time.LocalDate.of(2027, 2, 15)))
        assertNull(AppIconChoice.season(java.time.LocalDate.of(2026, 11, 7)))
        assertEquals("تناسبك", TasteMatch(score = 75).verdictAr)
        assertEquals("سارة وعلي و3 غيرهم جرّبوها هالأسبوع", ForYouExtras.Friends(RemoteLot(id = "1", slug = "s", name = "", nameAr = "", roasterNameAr = "", process = ""), listOf("سارة", "علي"), 5).line)
    }

    @org.junit.Test fun appConfigVerdict() {
        val p = AppConfig.Platform(minBuild = 3, latestBuild = 5, storeUrl = "x")
        org.junit.Assert.assertEquals(AppConfig.Verdict.Required, AppConfig.verdict(2, p))
        org.junit.Assert.assertEquals(AppConfig.Verdict.Suggested, AppConfig.verdict(3, p))
        org.junit.Assert.assertEquals(AppConfig.Verdict.Ok, AppConfig.verdict(5, p))
        org.junit.Assert.assertEquals(AppConfig.Verdict.Ok, AppConfig.verdict(1, AppConfig.Platform()))
    }
}

class RecipeWizardTest {
    @org.junit.Test fun doseKeepsStrengthAndPoursAddUp() {
        val d = RecipeDraft().start("V60", false)
        val r = d.ratio!!
        val bigger = d.setDose(20.0)
        org.junit.Assert.assertEquals(r, bigger.ratio!!, 0.05)
        org.junit.Assert.assertEquals(bigger.hotWaterGrams!!, bigger.pouredGrams, 0.01)
    }

    @org.junit.Test fun pourToMovesTheNextPour() {
        val d = RecipeDraft(steps = RecipeDraft.pourOverSteps(250.0), hotWaterGrams = 250.0)
        val moved = d.setPourTo(0, 60.0)
        org.junit.Assert.assertEquals(60.0, moved.cumulativeWater[0], 0.01)
        org.junit.Assert.assertEquals(250.0, moved.cumulativeWater.last(), 0.01) // later readings stay
    }

    @org.junit.Test fun startTimesAndFinish() {
        val d = RecipeDraft(steps = RecipeDraft.pourOverSteps(250.0))
        org.junit.Assert.assertEquals(listOf(0, 45, 85), d.startTimes)
        val shifted = d.setStart(1, 30)
        org.junit.Assert.assertEquals(listOf(0, 30, 85), shifted.startTimes)
        org.junit.Assert.assertEquals(200, shifted.setFinish(200).totalSeconds)
    }

    @org.junit.Test fun toolLoadsBaristaRecipeAndIcedAddsIce() {
        val espresso = RecipeDraft().start("ESPRESSO", false)
        org.junit.Assert.assertTrue(espresso.isEspresso)
        org.junit.Assert.assertEquals(GrindLevel.ExtraFine, espresso.grindLevel)
        val iced = RecipeDraft().start("CHEMEX", true)
        org.junit.Assert.assertTrue(iced.iced)
        org.junit.Assert.assertNotNull(iced.iceGrams)
        org.junit.Assert.assertTrue(RecipeDraft(title = "x").copy(grindLevel = GrindLevel.Medium).problemsAr().none { it.contains("الطحن") })
    }

    @org.junit.Test fun bodyCarriesProFields() {
        val b = RecipeDraft(title = "t", roastLevel = RoastLevel.Light, filterType = FilterType.Abaca, grindLevel = GrindLevel.MediumFine).body().toString()
        org.junit.Assert.assertTrue(b.contains("\"roastLevel\":\"light\"") && b.contains("\"filterType\":\"abaca\"") && b.contains("\"grindLevel\":\"mediumFine\""))
    }
}
