package com.bunnlist.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient

    @Before fun setUp() { server = MockWebServer().apply { start() }; api = ApiClient(server.url("/").toString()) }
    @After fun tearDown() { server.shutdown() }

    private val lotJson = """{"id":"l1","slug":"riyadh-colombia","name":"Colombia","nameAr":"كولومبيا","roasterNameAr":"محمصة الرياض","roasterSlug":"riyadh","originNameAr":"كولومبيا","originCode":"co","process":"NATURAL","processLabel":null,"flavorNotes":["ياسمين"],"roastLevel":"متوسط","averageRating":4.5,"reviewCount":3,"imageUrl":null,"futureField":1}"""

    @Test fun decodesBrowseAndSendsQuery() = runTest {
        server.enqueue(MockResponse().setBody("""{"total":736,"lots":[$lotJson]}"""))
        val page = api.browse(query = "  يمن ", limit = 20)
        assertEquals(736, page.total)
        assertEquals("كولومبيا", page.lots.first().nameAr)
        assertEquals("🇨🇴", page.lots.first().flag)
        val req = server.takeRequest()
        assertEquals("/api/v1/catalog/browse", req.requestUrl!!.encodedPath)
        assertEquals("يمن", req.requestUrl!!.queryParameter("q"))
        assertEquals("20", req.requestUrl!!.queryParameter("limit"))
    }

    @Test fun mapsErrorsWithReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"reason":"code_invalid"}"""))
        try { api.verifyEmailCode("a@b.com", "123456", "dev"); fail() } catch (e: ApiError.Unprocessable) { assertEquals("code_invalid", e.reason) }
        server.enqueue(MockResponse().setResponseCode(429))
        try { api.requestEmailCode("a@b.com"); fail() } catch (e: ApiError) { assertEquals(ApiError.RateLimited, e) }
    }

    @Test fun verifyPostsTheRightBody() = runTest {
        server.enqueue(MockResponse().setBody("""{"accessToken":"a","refreshToken":"r","accessExpiresInSeconds":900,"accountId":"acc","isNewAccount":true}"""))
        val t = api.verifyEmailCode(" Me@X.com ", "123456", "dev-1")
        assertTrue(t.isNewAccount == true)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"email\":\"Me@X.com\"") && body.contains("\"deviceId\":\"dev-1\""))
    }
}
