package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

class DiscoverTest {
    @Test fun filtersBecomeSortedParams() {
        val f = BrowseFilters(roasters = setOf("kof", "arriyadh"), processes = setOf("NATURAL"))
        assertEquals(mapOf("roasters" to "arriyadh,kof", "processes" to "NATURAL"), f.params())
        assertEquals(3, f.count); assertFalse(f.isEmpty)
        assertTrue(BrowseFilters().isEmpty)
    }

    @Test fun roasterRowInterleaves() {
        fun n(r: String, vararg s: String) = ForYou.RoasterNews(r, s.map { ForYou.NewsLot(it, it) })
        val row = DiscoverRows.fromRoasters(listOf(n("أ", "a1", "a2", "a3"), n("ب", "b1")), limit = 3)
        assertEquals(listOf("a1", "b1", "a2"), row.map { it.slug })
        assertEquals("ب", row[1].subtitle)
    }

    @Test fun dailyPickIsPersonalOnlyWithScore() {
        val lot = RemoteLot(id = "1", slug = "s", name = "n", nameAr = "ن", roasterNameAr = "م", process = "NATURAL")
        assertFalse(ForYou.DailyPick(lot, score = null).isPersonal)
        assertTrue(ForYou.DailyPick(lot, score = 80).isPersonal)
    }
}
