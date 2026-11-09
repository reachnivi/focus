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
import com.reachnivi.focus.core.Bedtime
import com.reachnivi.focus.core.DndAction
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.data.SettingsRepository
import com.reachnivi.focus.permissions.Permissions
import java.time.LocalDate
import java.time.LocalDateTime

/** Runs on the blocker's tick: Do Not Disturb during bedtime, and a gentle wind-down reminder before it. */
class BedtimeController(private val context: Context, private val settings: SettingsRepository) {

    private val nm = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Bedtime", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder to start winding down before bedtime"
        }
        nm.createNotificationChannel(channel)
    }

    suspend fun tick() {
        val s = settings.current()
        val now = LocalDateTime.now()
        val rule = s.bedtime

        // Do Not Disturb follows bedtime, and only what Focus turned on is turned off again.
        val active = rule?.isActiveAt(now) == true
        if (Permissions.hasDndAccess(context)) {
            val filterIsAll = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            when (Bedtime.dndAction(active, s.bedtimeDnd, filterIsAll, s.dndSetByUs)) {
                DndAction.TURN_ON -> {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    settings.setDndSetByUs(true)
                }
                DndAction.RESTORE -> {
                    if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    settings.setDndSetByUs(false)
                }
                DndAction.NONE -> Unit
            }
        } else if (s.dndSetByUs && !active) {
            settings.setDndSetByUs(false)
        }

        if (rule != null) {
            val notifiedOn = s.windDownNotifiedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (Bedtime.windDownDue(rule, now, s.windDownMinutes, notifiedOn)) {
                val start = Bedtime.nextStart(rule, now) ?: return
                notifyWindDown(s.windDownMinutes, Format.minuteOfDay(rule.startMinute))
                settings.setWindDownNotifiedOn(start.toLocalDate().toString())
            }
        }
    }

    private fun notifyWindDown(minutes: Int, at: String) {
        if (!Permissions.hasNotificationPermission(context)) return
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Bedtime in $minutes min")
            .setContentText("Start winding down. Distracting apps rest from $at.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked; bedtime still blocks.
        }
    }

    private companion object {
        const val CHANNEL_ID = "bedtime"
        const val NOTIFICATION_ID = 45
    }
}
