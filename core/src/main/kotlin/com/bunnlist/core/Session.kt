package com.bunnlist.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where tokens live (encrypted on the device). */
interface TokenStore {
    fun load(): StoredTokens?
    fun save(tokens: StoredTokens)
    fun clear()
}

data class StoredTokens(val accessToken: String, val refreshToken: String, val accessExpiresAtMillis: Long, val accountId: String)

/**
 * Holds the signed-in session and hands out a fresh access token. Refreshes ahead of
 * expiry and once more on a 401; a failed refresh signs out (the server rotates refresh
 * tokens, so a reused one is dead). One refresh at a time.
 */
class Session(private val api: ApiClient, private val store: TokenStore, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    @Volatile var tokens: StoredTokens? = store.load(); private set

    val isSignedIn: Boolean get() = tokens != null

    fun adopt(response: TokenResponse) {
        val t = StoredTokens(response.accessToken, response.refreshToken, now() + response.accessExpiresInSeconds * 1000L, response.accountId)
        tokens = t; store.save(t)
    }

    fun signOut() { tokens = null; store.clear() }

    suspend fun accessToken(): String = lock.withLock {
        val t = tokens ?: throw ApiError.Unauthorized
        if (t.accessExpiresAtMillis - now() > 60_000) return@withLock t.accessToken
        refreshLocked(t)
    }

    private suspend fun refreshLocked(t: StoredTokens): String = try {
        api.refresh(t.refreshToken).also { adopt(it) }.accessToken
    } catch (e: ApiError.Unauthorized) { signOut(); throw e }

    /** Runs [block] with a token; on 401 refreshes once and retries. */
    suspend fun <T> withToken(block: suspend (String) -> T): T {
        val token = accessToken()
        return try { block(token) } catch (e: ApiError.Unauthorized) {
            val fresh = lock.withLock { tokens?.let { refreshLocked(it) } ?: throw e }
            block(fresh)
        }
    }
}
