package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

class ListsTest {
    @Test fun insights() {
        fun c(o: String?, r: String, fav: Boolean = false, notes: List<String> = emptyList()) =
            CollectionItem(lotId = o + r, slug = "s", nameAr = "ن", roasterNameAr = r, originCode = o, favorite = fav, flavorNotes = notes)
        val i = CollectionInsights(listOf(c("YE", "a", notes = listOf("توت", "شوكولا")), c("ye", "a", true, listOf("توت")), c("XB", "b"), c(null, "")))
        assertEquals(4, i.coffees); assertEquals(listOf("YE"), i.countryCodes); assertEquals(2, i.roasters); assertEquals(1, i.favorites)
        assertEquals(100, i.shares.sumOf { it.percent }); assertEquals(FlavorFamily.Berry, i.shares.first().family)
    }
    @Test fun leanAndCount() {
        assertEquals(1.0, TasteLean.position(listOf("توت", "ياسمين"))!!, 0.0001)
        assertNull(TasteLean.position(listOf("??")))
        assertEquals("فاضية", ListSummary(id = "1", title = "t").countLine)
        assertEquals(listOf(34, 33, 33), CollectionInsights.percentages(listOf(1, 1, 1)))
    }
}
