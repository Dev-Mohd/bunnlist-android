package com.bunnlist.app

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bunnlist.core.ApiClient
import com.bunnlist.core.Session
import com.bunnlist.core.StoredTokens
import com.bunnlist.core.TokenStore
import java.util.UUID

/** App-wide services: one API client and the signed-in session. */
class BunnListApp : Application(), coil.ImageLoaderFactory {
    /** Photos fade in instead of popping, and stay cached on the phone whatever the store's headers say. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).crossfade(220).respectCacheHeaders(false).build()

    lateinit var api: ApiClient; private set
    lateinit var session: Session; private set
    lateinit var deviceId: String; private set

    override fun onCreate() {
        super.onCreate()
        val prefs = securePrefs(this)
        deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
        api = ApiClient()
        session = Session(api, PrefsTokenStore(prefs))
        runCatching { Push.createChannel(this) }
    }

    companion object {
        /**
         * Encrypted preferences for the tokens. Some phones (notably some Samsung models) have a broken or reset
         * Android Keystore, and opening the encrypted file throws, which closed the app the moment it opened.
         * Now: try; on failure start the encrypted store clean (you sign in again); if the Keystore is unusable
         * altogether, fall back to app-private preferences so the app still opens.
         */
        fun securePrefs(context: Context): android.content.SharedPreferences = try {
            openEncrypted(context)
        } catch (e: Exception) {
            android.util.Log.w("BunnList", "Encrypted prefs unavailable, resetting", e)
            runCatching { context.deleteSharedPreferences(SECURE_FILE) }
            runCatching { java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS) }
            try { openEncrypted(context) } catch (e2: Exception) {
                android.util.Log.w("BunnList", "Keystore unusable, using private prefs", e2)
                context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
            }
        }

        private const val SECURE_FILE = "bunnlist_secure"
        private const val FALLBACK_FILE = "bunnlist_private"

        private fun openEncrypted(context: Context) = EncryptedSharedPreferences.create(
            context, SECURE_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { it.getString("deviceId", null) }   // reading once surfaces a corrupt keyset here, not later
    }
}

/** Tokens in encrypted shared preferences (keys held by the Android Keystore). */
private class PrefsTokenStore(private val prefs: android.content.SharedPreferences) : TokenStore {
    override fun load(): StoredTokens? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        return StoredTokens(access, refresh, prefs.getLong("expires", 0), prefs.getString("account", "") ?: "")
    }
    override fun save(tokens: StoredTokens) {
        prefs.edit().putString("access", tokens.accessToken).putString("refresh", tokens.refreshToken)
            .putLong("expires", tokens.accessExpiresAtMillis).putString("account", tokens.accountId).apply()
    }
    override fun clear() { prefs.edit().remove("access").remove("refresh").remove("expires").remove("account").apply() }
}
