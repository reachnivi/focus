package com.reachnivi.focus.usage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reachnivi.focus.MainActivity
import com.reachnivi.focus.R
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.core.WeeklyRecap
import com.reachnivi.focus.permissions.Permissions
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/** Sunday-evening recap of the week, sent at most once a week. */
class RecapNotifier(private val context: Context) {
    private val prefs = context.getSharedPreferences("weekly_recap", Context.MODE_PRIVATE)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Weekly recap", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A short look back at your week every Sunday evening"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun isDue(now: LocalDateTime = LocalDateTime.now()): Boolean =
        now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18 && prefs.getString(KEY_SENT, null) != weekKey(now)

    fun send(recap: WeeklyRecap, now: LocalDateTime = LocalDateTime.now()) {
        if (!Permissions.hasNotificationPermission(context) || recap.thisWeekMs <= 0) return
        val change = recap.changePercent?.let { if (it <= 0) "${-it}% less than last week" else "$it% more than last week" }
        val best = recap.bestDay?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val text = listOfNotNull(change, best?.let { "Lightest day: $it" }).joinToString(". ").ifEmpty { "Tap to see your week." }
        val open = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Your week: ${Format.duration(recap.thisWeekMs)} on your phone")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_SENT, weekKey(now)).apply()
        } catch (e: SecurityException) {
            // Permission revoked; try again next week.
        }
    }

    private fun weekKey(now: LocalDateTime) =
        "${now.get(IsoFields.WEEK_BASED_YEAR)}-${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    private companion object {
        const val CHANNEL_ID = "weekly_recap"
        const val NOTIFICATION_ID = 46
        const val KEY_SENT = "sent_week"
    }
}
