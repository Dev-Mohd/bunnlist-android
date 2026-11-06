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

    suspend fun browse(query: String? = null, limit: Int = 40, offset: Int = 0, accessToken: String? = null): BrowsePage {
        val params = buildMap {
            put("limit", "$limit"); put("offset", "$offset")
            if (!query.isNullOrBlank()) put("q", query.trim())
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
