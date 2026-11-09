package com.reachnivi.focus.block

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reachnivi.focus.R
import com.reachnivi.focus.core.BlockerStatus
import com.reachnivi.focus.permissions.Permissions

/** Tells the user when the blocker has stopped, so blocks never fail silently. */
class HealthNotifier(private val context: Context) {

    private val prefs = context.getSharedPreferences("blocker_health", Context.MODE_PRIVATE)

    var lastAlertAt: Long
        get() = prefs.getLong(KEY_LAST_ALERT, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_ALERT, value).apply()

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Blocker status", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Lets you know if the blocker stops, so your blocks keep working"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showBlockerDown(status: BlockerStatus) {
        if (!Permissions.hasNotificationPermission(context)) return
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (status) {
            BlockerStatus.NOT_RUNNING ->
                "Focus blocker stopped" to "Android stopped it. Tap, then turn Focus blocker off and on again."
            else ->
                "Focus blocker is off" to "Your blocked apps and sites aren't paused. Tap to turn it back on."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            lastAlertAt = System.currentTimeMillis()
        } catch (e: SecurityException) {
            // Notification permission revoked in the meantime; nothing else to do.
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "blocker_health"
        const val NOTIFICATION_ID = 43
        const val KEY_LAST_ALERT = "last_alert_at"
    }
}
