package com.bunnlist.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    private class MemoryStore(var t: StoredTokens? = null) : TokenStore {
        override fun load() = t
        override fun save(tokens: StoredTokens) { t = tokens }
        override fun clear() { t = null }
    }

    @Test fun refreshesAheadOfExpiryAndSignsOutWhenRefreshDies() = runTest {
        val server = MockWebServer().apply { start() }
        val api = ApiClient(server.url("/").toString())
        var clock = 1_000_000L
        val store = MemoryStore(StoredTokens("old", "r1", clock + 30_000, "acc"))
        val session = Session(api, store) { clock }
        server.enqueue(MockResponse().setBody("""{"accessToken":"new","refreshToken":"r2","accessExpiresInSeconds":900,"accountId":"acc"}"""))
        assertEquals("new", session.accessToken())
        assertEquals("r2", store.t!!.refreshToken)
        clock += 900_000
        server.enqueue(MockResponse().setResponseCode(401))
        try { session.accessToken(); fail() } catch (e: ApiError.Unauthorized) { }
        assertFalse(session.isSignedIn)
        assertNull(store.t)
        server.shutdown()
    }
}
