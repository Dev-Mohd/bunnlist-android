package com.bunnlist.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CommunityTest {
    @Test fun pulseCommentTargets() {
        val brew = PulseItem(kind = "brew", at = "2026-11-06T10:00:00Z", name = "س", brewId = "b1", methodCode = "V60")
        assertEquals("brew" to "b1", brew.commentTarget)
        assertEquals("حضّر V60", brew.actionAr)
        assertNull(PulseItem(kind = "discovery", at = "", name = "س").commentTarget)
        assertEquals("recipe" to "r1", PulseItem(kind = "recipe", at = "", name = "س", recipeId = "r1").commentTarget)
    }

    @Test fun relativeTime() {
        val now = java.time.Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()
        assertEquals("الحين", RelativeTime.ar("2026-11-06T11:59:30Z", now))
        assertEquals("قبل 5 د", RelativeTime.ar("2026-11-06T11:55:00Z", now))
        assertEquals("قبل 2 يوم", RelativeTime.ar("2026-11-04T12:00:00Z", now))
        assertEquals("", RelativeTime.ar("bad", now))
    }

    @Test fun profileFollowCommentAndReportCalls() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"username":"boysoth","displayName":null,"points":25,"followers":3,"following":null,"isFollowing":false,"discoveries":[],"recipes":[{"id":"r","title":"t","methodCode":"V60"}]}"""))
        server.enqueue(MockResponse().setBody("""{"following":false,"requested":true}"""))
        server.enqueue(MockResponse().setBody("""{"id":"c","body":"حلو","createdAt":"2026-11-06T10:00:00Z","name":"أنا","isMine":true,"canDelete":true,"isOfficial":false}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.start()
        val api = ApiClient(server.url("/").toString())
        val p = api.profile("boysoth", null)
        assertEquals("@boysoth", p.shownName); assertNull(p.following); assertEquals(1, p.recipes.size)
        assertEquals("/api/v1/users/boysoth", server.takeRequest().path)
        assertTrue(api.follow("boysoth", "t").requested)
        assertEquals("POST", server.takeRequest().method)
        assertTrue(api.addComment("brew", "b1", "  حلو ", "t").isMine)
        assertEquals("""{"body":"حلو"}""", server.takeRequest().body.readUtf8())
        api.report("comment", "c", ReportReason.Spam, " ", "t")
        assertEquals("""{"targetType":"comment","targetId":"c","reason":"spam"}""", server.takeRequest().body.readUtf8())
        server.shutdown()
    }
}
