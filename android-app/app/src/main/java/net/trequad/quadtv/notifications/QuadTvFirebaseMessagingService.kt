package net.trequad.quadtv.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import net.trequad.quadtv.MainActivity
import net.trequad.quadtv.R

class QuadTvFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "QuadTV FCM token refreshed for QuadMedia backend registration")
        // Device registration owns the portal device id; FcmTokenRepository registers this token
        // after bootstrap has a device id. Avoid logging the raw token.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "QuadTV"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "New QuadMedia notification"
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.quadtv_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QuadTV QuadMedia Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Announcements, subscription warnings, and service alerts from QuadMedia"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "QuadTV-FCM"
        private const val CHANNEL_ID = "quadtv_quadmedia_alerts"
        private const val NOTIFICATION_ID = 4001
    }
}
