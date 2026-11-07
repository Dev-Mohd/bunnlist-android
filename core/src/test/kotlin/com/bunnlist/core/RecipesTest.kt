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
