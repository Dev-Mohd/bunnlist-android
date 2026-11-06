package com.bunnlist.app.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.bunnlist.app.BuildConfig
import com.bunnlist.app.BunnListApp
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleSignIn {
    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    sealed interface Outcome {
        data object Success : Outcome
        data object Cancelled : Outcome
        data class Failed(val messageAr: String) : Outcome
    }

    /** Shows Google's account sheet, then trades the ID token for BunnList tokens (same account as iOS). */
    suspend fun signIn(context: Context, app: BunnListApp): Outcome {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (e: GetCredentialCancellationException) {
            return Outcome.Cancelled
        } catch (e: NoCredentialException) {
            return Outcome.Failed("ما فيه حساب Google على الجوال. أضف حساب من الإعدادات أو ادخل بالإيميل.")
        } catch (e: GetCredentialException) {
            return Outcome.Failed("ما قدرنا ندخل بـGoogle الحين. جرّب الإيميل.")
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return Outcome.Failed("ما قدرنا ندخل بـGoogle الحين. جرّب الإيميل.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return try {
            app.session.adopt(app.api.exchangeGoogle(idToken, app.deviceId))
            Outcome.Success
        } catch (e: com.bunnlist.core.ApiError) {
            Outcome.Failed(if (e is com.bunnlist.core.ApiError.Network) "تأكد من اتصالك بالإنترنت." else "ما قدرنا ندخل بـGoogle الحين. جرّب الإيميل.")
        }
    }
}
