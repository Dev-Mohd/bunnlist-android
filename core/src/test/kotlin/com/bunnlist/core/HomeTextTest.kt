package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

class HomeTextTest {
    @Test fun greeting() {
        assertEquals("صباح الخير", HomeText.greeting(9, null))
        assertEquals("مساء الخير، Admin", HomeText.greeting(14, "Admin User"))
    }
    @Test fun agoMatchesIos() {
        val now = java.time.Instant.parse("2026-11-20T12:00:00Z").toEpochMilli()
        assertEquals("اليوم", HomeText.ago("2026-11-20T08:00:00Z", now))
        assertEquals("من 5 أيام", HomeText.ago("2026-11-15T12:00:00Z", now))
        assertEquals("من 11 يوم", HomeText.ago("2026-11-09T12:00:00Z", now))
        assertEquals("من أسبوعين", HomeText.ago("2026-11-04T12:00:00Z", now))
    }
    @Test fun trendingReasons() {
        val top = TrendingLot(id = "1", slug = "s", nameAr = "ن", reason = "added", adds = 1)
        assertEquals(listOf("+1 أضافوها هالشهر"), top.reasonsAr)
        val hot = TrendingLot(id = "1", slug = "s", nameAr = "ن", reason = "hot", brews = 12, reviews = 2)
        assertEquals(listOf("12 تجربة", "تقييمين"), hot.reasonsAr)
    }
    @Test fun originLineAndOnePerRoaster() {
        assertEquals("🇾🇪 اليمن · مجففة", HomeText.originLine("YE", "اليمن", "NATURAL", null))
        fun l(id: String, r: String) = RemoteLot(id = id, slug = id, name = "", nameAr = id, roasterNameAr = r, process = "")
        assertEquals(listOf("a", "c"), HomeText.onePerRoaster(listOf(l("a", "x"), l("b", "x"), l("c", "y")), 6).map { it.id })
    }
    @Test fun ratingsToGo() {
        val lot = RemoteLot(id = "1", slug = "s", name = "", nameAr = "ن", roasterNameAr = "م", process = "")
        assertEquals(2, ForYou.DailyPick(lot, rated = 1).ratingsToGo)
        assertNull(ForYou.DailyPick(lot, score = 80).ratingsToGo)
    }
}
