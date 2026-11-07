package com.bunnlist.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Push through Firebase: the phone's token goes to the server while signed in. */
object Push {
    const val CHANNEL = "general"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "إشعارات BunnList", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "متابعين جدد، الاقتراحات، التعليقات والإعجابات، وملخص الأسبوع"
        })
    }

    private suspend fun token(): String? = suspendCancellableCoroutine { c ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { c.resume(it) }
                .addOnFailureListener { c.resume(null) }
        }.onFailure { c.resume(null) }
    }

    /** After sign-in and on each launch: tell the server where to reach this phone. */
    fun register(app: BunnListApp, fcmToken: String? = null) {
        if (!app.session.isSignedIn) return
        scope.launch {
            val t = fcmToken ?: token() ?: return@launch
            runCatching { app.session.withToken { app.api.registerDevice(t, it) } }
        }
    }

    /** Before signing out or deleting the account, so the next person on this phone doesn't get them. */
    suspend fun unregister(app: BunnListApp) {
        if (!app.session.isSignedIn) return
        val t = token() ?: return
        runCatching { app.session.withToken { app.api.unregisterDevice(t, it) } }
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** Receives new tokens, and shows notifications that arrive while the app is open (the system shows the rest). */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? BunnListApp)?.let { Push.register(it, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        if (!Push.canNotify(this)) return
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { message.data.forEach { (k, v) -> putExtra(k, v) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, Push.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF536344.toInt())
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), notification) }
    }
}
