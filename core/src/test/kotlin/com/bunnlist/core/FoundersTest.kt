package com.bunnlist.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FoundersTest {
    @Test fun labelPadsToTwoDigits() {
        assertEquals("#07", FounderBadge(7).label)
        assertEquals("#42", FounderBadge.label(42))
        assertEquals("#100", FounderBadge.label(100))
    }

    @Test fun handlesFromWhatPeopleType() {
        assertEquals("noura.brews", SocialNetwork.TikTok.handleFrom("@noura.brews"))
        assertEquals("noura.brews", SocialNetwork.TikTok.handleFrom("https://www.tiktok.com/@noura.brews?lang=ar"))
        assertEquals("noura_brews", SocialNetwork.Instagram.handleFrom("instagram.com/noura_brews/"))
        assertEquals("NouraBrews", SocialNetwork.YouTube.handleFrom("https://youtube.com/@NouraBrews"))
        assertEquals("noura", SocialNetwork.X.handleFrom("https://twitter.com/noura"))
        assertNull(SocialNetwork.Instagram.handleFrom("نورة"))
        assertNull(SocialNetwork.TikTok.handleFrom("has space"))
        assertNull(SocialNetwork.X.handleFrom("a".repeat(16)))
    }

    @Test fun linkedInOrder() {
        val s = Socials(tiktok = "a", instagram = "", x = "b")
        assertEquals(listOf(SocialNetwork.TikTok, SocialNetwork.X), s.linked.map { it.first })
        assertEquals("https://www.tiktok.com/@a", SocialNetwork.TikTok.url("a"))
        assertTrue(Socials().isEmpty)
    }

    @Test fun decodesFounderFieldsAndStats() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"username":"noura","points":10,"discoveries":[],"recipes":[{"id":"r1","title":"t","methodCode":"V60","brewedCount":320}],"founder":{"number":7},"socials":{"tiktok":"noura.brews"}}"""))
        server.enqueue(MockResponse().setBody("""{"limit":100,"taken":27,"remaining":73}"""))
        server.enqueue(MockResponse().setBody("""{"username":"noura","founder":{"number":7},"socials":{"tiktok":"new"}}"""))
        server.start()
        val api = ApiClient(server.url("/").toString())
        val p = api.profile("noura", null)
        assertEquals("#07", p.founder?.label)
        assertEquals("noura.brews", p.socials?.tiktok)
        assertEquals(320, p.recipes.first().brewedCount)
        val stats = api.founderStats()
        assertEquals(0.27f, stats.progress, 0.001f)
        assertFalse(stats.isFull)
        val me = api.updateSocials("t", mapOf(SocialNetwork.TikTok to "@new", SocialNetwork.X to ""))
        assertEquals("new", me.socials?.tiktok)
        server.takeRequest(); server.takeRequest()
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertTrue(patch.body.readUtf8().contains("\"socials\":{\"tiktok\":\"@new\",\"x\":\"\"}"))
        server.shutdown()
    }
}
