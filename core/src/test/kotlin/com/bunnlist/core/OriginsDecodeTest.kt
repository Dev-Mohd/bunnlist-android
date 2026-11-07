package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test

/** The live origins list has countries with no English name and signed-out `tried: null`; the map must still load. */
class OriginsDecodeTest {
    @Test fun decodesNullNameEn() {
        val text = """{"origins":[{"code":"CO","nameAr":"كولومبيا","nameEn":"Colombia","lots":214,"roasters":40,"averageRating":4.8,
          "families":[{"key":"berry","count":75}],"processes":[{"key":"NATURAL","count":50}],"regions":["هويلا"],"tried":null},
          {"code":"CR","nameAr":"كوستاريكا","nameEn":null,"lots":44,"roasters":29,"averageRating":5,"families":[],"processes":[],"regions":[],"tried":null}]}"""
        val list = ApiClient().json.decodeFromString<OriginList>(text)
        assertEquals(2, list.origins.size)
        assertNull(list.origins[1].nameEn)
        assertNull(list.origins[0].tried)
    }

    @Test fun nullForDefaultedFieldTakesDefault() {
        val o = ApiClient().json.decodeFromString<OriginInfo>("""{"code":"SA","nameAr":"السعودية","lots":null,"regions":null}""")
        assertEquals(0, o.lots)
        assertTrue(o.regions.isEmpty())
    }
}
