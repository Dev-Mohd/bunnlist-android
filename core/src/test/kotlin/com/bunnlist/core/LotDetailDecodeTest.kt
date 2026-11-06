package com.bunnlist.core

import org.junit.Assert.*
import org.junit.Test


class LotDetailDecodeTest {
    @Test fun decodesLiveShape() {
        val text = """{"id":"x","slug":"s","name":"N","nameAr":"ن","roasterNameAr":"م","roasterSlug":"r","originNameAr":"كولومبيا","originCode":"CO",
          "process":"NATURAL","processLabel":null,"flavorNotes":["يوسفي"],"roastLevel":"متوسط","averageRating":0,"reviewCount":0,"imageUrl":null,
          "descriptionAr":"و","regionAr":null,"variety":"Gesha","recommendedBrewMethods":["ESPRESSO","V60"],"brewStats":[],"discoveredBy":null,
          "community":{"collectors":0,"brewers":0,"methods":[]},"tags":[{"tag":"luxury","note":"n"}],"altitudeMeters":null,
          "buy":{"url":"https://a.com/p","host":"a.com","kind":"roaster","storeNameAr":null},"roasterRecipes":[]}"""
        val lot = ApiClient().json.decodeFromString<RemoteLotDetail>(text)
        assertEquals("🇨🇴", lot.flag)
        assertEquals("مجففة", lot.processAr)
        assertEquals("اشترِ من المحمصة", lot.buy?.title)
    }
}
