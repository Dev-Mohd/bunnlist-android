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
class BunnListApp : Application() {
    lateinit var api: ApiClient; private set
    lateinit var session: Session; private set
    lateinit var deviceId: String; private set

    override fun onCreate() {
        super.onCreate()
        val prefs = securePrefs(this)
        deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
        api = ApiClient()
        session = Session(api, PrefsTokenStore(prefs))
        Push.createChannel(this)
    }

    companion object {
        fun securePrefs(context: Context) = EncryptedSharedPreferences.create(
            context, "bunnlist_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
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
