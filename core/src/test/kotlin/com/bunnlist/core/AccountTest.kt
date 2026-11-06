package com.bunnlist.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AccountTest {
    @Test fun collectionPutSendsAllFlagsAndShelvesFilter() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"owned":true,"favorite":false,"wantsToTry":false,"tried":true}"""))
        server.enqueue(MockResponse().setBody("""{"entries":[{"lotId":"1","slug":"a","nameAr":"أ","owned":true},{"lotId":"2","slug":"b","nameAr":"ب","wantsToTry":true,"originCode":"ET"}]}"""))
        server.start()
        val api = ApiClient(server.url("/").toString())
        val flags = api.setCollection("a", CollectionFlags(owned = true, tried = true), "t")
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/catalog/lots/a/collection", put.path)
        assertEquals("""{"owned":true,"favorite":false,"wantsToTry":false,"tried":true}""", put.body.readUtf8())
        assertTrue(flags.tried)
        val items = api.collection("t")
        assertEquals(1, items.filter(Shelf.Owned.matches).size)
        assertEquals("🇪🇹", items.first(Shelf.WantsToTry.matches).flag)
        server.shutdown()
    }

    @Test fun reviewBodyOmitsBlankText() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"averageRating":4.5,"reviewCount":2,"review":{"rating":5,"brewMethod":"V60","wouldBuyAgain":true,"body":null}}"""))
        server.enqueue(MockResponse().setBody("""{"review":null}"""))
        server.start()
        val api = ApiClient(server.url("/").toString())
        val r = api.submitReview("a", MyReview(5, "V60", true, "  "), "t")
        assertEquals("""{"rating":5,"brewMethod":"V60","wouldBuyAgain":true}""", server.takeRequest().body.readUtf8())
        assertEquals(2, r.reviewCount)
        assertNull(api.myReview("a", "t"))
        server.shutdown()
    }
}
