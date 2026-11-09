package com.reachnivi.focus.block

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reachnivi.focus.MainActivity
import com.reachnivi.focus.R
import com.reachnivi.focus.permissions.Permissions

/** "How was your call?" after a Connection challenge call ends, wherever the user ends up. */
class ConnectionNotifier(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Connection challenge", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Asks how your call went, so it counts toward your streak"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun callEnded(name: String, talkedSec: Int) {
        if (!Permissions.hasNotificationPermission(context)) return
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val minutes = (talkedSec / 60).coerceAtLeast(1)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("How was your call with $name?")
            .setContentText("About $minutes min. Tap to log it and keep your streak.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked; the sheet still appears next time Focus opens.
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "connections"
        const val NOTIFICATION_ID = 44
    }
}
