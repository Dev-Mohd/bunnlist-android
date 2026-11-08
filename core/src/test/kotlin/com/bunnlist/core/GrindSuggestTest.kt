package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

class GrindSuggestTest {
    private val c40 = GuideGrinder(id = "c40", brand = "Comandante", model = "C40", adjustment = "clicks", micronsPerStep = 30.0)
    private val e55 = GuideGrinder(id = "e55", brand = "STARSEEKER", model = "E55")

    @Test fun clicksWhenTheGrinderConverts() {
        val v60 = BrewPreset.presets("V60").first { it.id == "v60-classic" }   // medium-fine → 625 µm
        assertEquals(625, v60.suggestedMicrons)
        assertEquals("مقترح: 21 كليك", suggestedGrindAr(v60, c40))
    }

    @Test fun levelInWordsOtherwise() {
        val v60 = BrewPreset.presets("V60").first { it.id == "v60-classic" }
        assertEquals("مقترح: متوسط ناعم", suggestedGrindAr(v60, e55))
        assertEquals("مقترح: متوسط ناعم", suggestedGrindAr(v60, null))
    }

    @Test fun aSavedRecipesMicronsWin() {
        val r = RemoteRecipe(id = "r", methodCode = "V60", grindMicrons = 720, grindLevel = "medium").toPreset()
        assertEquals(720, r.suggestedMicrons)
        assertEquals("مقترح: 24 كليك", suggestedGrindAr(r, c40))
    }
}
