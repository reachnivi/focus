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

class SessionNotifier(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Focus sessions", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the countdown while a focus session is running"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(endsAt: Long, strict: Boolean) {
        if (!Permissions.hasNotificationPermission(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(if (strict) "Strict focus in progress" else "Focus in progress")
            .setContentText("Distracting apps are paused. You've got this.")
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setTimeoutAfter((endsAt - System.currentTimeMillis()).coerceAtLeast(1_000))
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and notify; the session still works.
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "focus_session"
        const val NOTIFICATION_ID = 42
    }
}
