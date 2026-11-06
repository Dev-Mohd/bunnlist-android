package com.bunnlist.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thin client over the mobile API. Every call is a suspend function off the main thread. */
class ApiClient(
    baseUrl: String = "https://bunnlist.com",
    private val http: OkHttpClient = defaultHttp(),
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // MARK: Auth

    /** Starts email sign-in. The server answers the same whether or not the email exists. */
    suspend fun requestEmailCode(email: String) {
        send("POST", "/api/v1/auth/email/request", buildJsonObject { put("email", email.trim()) })
    }

    suspend fun verifyEmailCode(email: String, code: String, deviceId: String): TokenResponse =
        decode(send("POST", "/api/v1/auth/email/verify", buildJsonObject {
            put("email", email.trim()); put("code", code.trim()); put("deviceId", deviceId)
        }))

    /** Signs in with a Google ID token (Credential Manager). */
    suspend fun exchangeGoogle(idToken: String, deviceId: String): TokenResponse =
        decode(send("POST", "/api/v1/auth/exchange", buildJsonObject {
            put("provider", "google"); put("credential", idToken); put("deviceId", deviceId)
        }))

    suspend fun refresh(refreshToken: String): TokenResponse =
        decode(send("POST", "/api/v1/auth/refresh", buildJsonObject { put("refreshToken", refreshToken) }))

    // MARK: Catalog

    suspend fun browse(query: String? = null, limit: Int = 40, offset: Int = 0, accessToken: String? = null, filters: BrowseFilters = BrowseFilters()): BrowsePage {
        val params = buildMap {
            put("limit", "$limit"); put("offset", "$offset")
            if (!query.isNullOrBlank()) put("q", query.trim())
            putAll(filters.params())
        }
        return decode(send("GET", "/api/v1/catalog/browse", null, accessToken, params))
    }

    suspend fun lot(slug: String, accessToken: String? = null): RemoteLotDetail =
        decode(send("GET", "/api/v1/catalog/lots/$slug", null, accessToken))

    // MARK: Advisor

    suspend fun advise(answers: AdvisorAnswers, accessToken: String? = null): RemoteAdvisorResult =
        decode(send("POST", "/api/v1/advisor", answers.body(), accessToken))

    // MARK: Collection and reviews

    suspend fun collection(token: String): List<CollectionItem> =
        decode<CollectionList>(send("GET", "/api/v1/collection", null, token)).entries

    suspend fun collectionFlags(slug: String, token: String): CollectionFlags =
        decode(send("GET", "/api/v1/catalog/lots/$slug/collection", null, token))

    suspend fun setCollection(slug: String, flags: CollectionFlags, token: String): CollectionFlags =
        decode(send("PUT", "/api/v1/catalog/lots/$slug/collection", buildJsonObject {
            put("owned", flags.owned); put("favorite", flags.favorite); put("wantsToTry", flags.wantsToTry); put("tried", flags.tried)
        }, token))

    suspend fun myReview(slug: String, token: String): MyReview? =
        decode<MyReviewEnvelope>(send("GET", "/api/v1/catalog/lots/$slug/review", null, token)).review

    suspend fun submitReview(slug: String, review: MyReview, token: String): ReviewResult =
        decode(send("POST", "/api/v1/catalog/lots/$slug/review", buildJsonObject {
            put("rating", review.rating); put("brewMethod", review.brewMethod); put("wouldBuyAgain", review.wouldBuyAgain)
            review.body?.trim()?.takeIf { it.isNotEmpty() }?.let { put("body", it) }
        }, token))

    // MARK: Account

    suspend fun me(token: String): Me = decode(send("GET", "/api/v1/me", null, token))

    /** Permanently deletes the account and its data. */
    suspend fun deleteAccount(token: String) { send("POST", "/api/v1/account/delete", buildJsonObject {}, token) }

    suspend fun myFeedback(token: String): List<Feedback> = decode<FeedbackList>(send("GET", "/api/v1/feedback", null, token)).items

    suspend fun sendFeedback(kind: FeedbackKind, body: String, token: String): Feedback =
        decode(send("POST", "/api/v1/feedback", buildJsonObject { put("kind", kind.code); put("body", body.trim()) }, token))

    // MARK: Community

    suspend fun home(token: String?): HomeFeed = decode(send("GET", "/api/v1/home", null, token))
    suspend fun followingFeed(token: String): List<PulseItem> = decode<PulseList>(send("GET", "/api/v1/community/following", null, token)).items
    suspend fun updates(token: String?): List<OfficialUpdate> = decode<UpdateList>(send("GET", "/api/v1/updates", null, token)).updates

    suspend fun leaderboard(allTime: Boolean, kind: LeaderKind, token: String?): Leaderboard =
        decode(send("GET", "/api/v1/community/leaderboard", null, token,
            mapOf("period" to if (allTime) "all" else "week", "kind" to kind.code, "limit" to "50")))

    suspend fun searchPeople(q: String, token: String?): List<PersonCard> =
        decode<PeopleList>(send("GET", "/api/v1/users/search", null, token, mapOf("q" to q))).people
    suspend fun suggestedPeople(token: String): SuggestedPeople = decode(send("GET", "/api/v1/users/suggested", null, token))

    suspend fun profile(username: String, token: String?): PublicProfile = decode(send("GET", "/api/v1/users/${enc(username)}", null, token))
    suspend fun personCoffees(username: String, shelf: String, token: String?): PersonCoffees =
        decode(send("GET", "/api/v1/users/${enc(username)}/coffees", null, token, mapOf("shelf" to shelf)))
    suspend fun follow(username: String, token: String): FollowResult = decode(send("POST", "/api/v1/users/${enc(username)}/follow", buildJsonObject {}, token))
    suspend fun unfollow(username: String, token: String) { send("DELETE", "/api/v1/users/${enc(username)}/follow", null, token) }
    suspend fun setBlocked(username: String, blocked: Boolean, token: String) {
        send(if (blocked) "POST" else "DELETE", "/api/v1/users/${enc(username)}/block", if (blocked) buildJsonObject {} else null, token)
    }

    suspend fun comments(type: String, id: String, token: String?): CommentList = decode(send("GET", "/api/v1/comments/$type/${enc(id)}", null, token))
    suspend fun addComment(type: String, id: String, body: String, token: String): Comment =
        decode(send("POST", "/api/v1/comments/$type/${enc(id)}", buildJsonObject { put("body", body.trim()) }, token))
    suspend fun deleteComment(id: String, token: String) { send("DELETE", "/api/v1/comments/item/${enc(id)}", null, token) }

    suspend fun report(targetType: String, targetId: String, reason: ReportReason, details: String?, token: String) {
        send("POST", "/api/v1/reports", buildJsonObject {
            put("targetType", targetType); put("targetId", targetId); put("reason", reason.code)
            details?.trim()?.takeIf { it.isNotEmpty() }?.let { put("details", it.take(500)) }
        }, token)
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // MARK: Discover

    suspend fun forYou(token: String): ForYou = decode(send("GET", "/api/v1/me/for-you", null, token))
    suspend fun facets(): Facets = decode(send("GET", "/api/v1/catalog/facets", null))

    /** Reads a bag photo (JPEG, base64) and returns matching coffees. Signed in only; limited per day. */
    suspend fun scanBag(jpegBase64: String, token: String): BagScanResult =
        decode(send("POST", "/api/v1/scan/bag", buildJsonObject { put("imageBase64", jpegBase64); put("mediaType", "image/jpeg") }, token))

    // MARK: Plumbing

    internal suspend fun send(
        method: String, path: String, body: JsonObject?, bearer: String? = null, query: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val url = base.newBuilder().encodedPath(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()
        val response = try { http.newCall(req).execute() } catch (e: IOException) { throw ApiError.Network(e.message) }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiError.from(it.code, reasonOf(text))
            text
        }
    }

    internal inline fun <reified T> decode(text: String): T =
        try { json.decodeFromString<T>(text) } catch (e: Exception) { throw ApiError.Malformed }

    private fun reasonOf(text: String): String? = try {
        (json.parseToJsonElement(text) as? JsonObject)?.get("reason")?.jsonPrimitive?.content
    } catch (e: Exception) { null }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
}
