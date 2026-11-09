package com.reachnivi.focus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.reachnivi.focus.FocusApp
import com.reachnivi.focus.MainActivity
import com.reachnivi.focus.core.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class WidgetState(
    val todayMs: Long,
    val goalMs: Long,
    val underGoalStreak: Int,
    val sessionEndsAt: Long?,
    val hasAccess: Boolean,
)

private val bg = ColorProvider(day = Color(0xFFF6F4EF), night = Color(0xFF191D1A))
private val ink = ColorProvider(day = Color(0xFF1C211E), night = Color(0xFFE6E8E3))
private val soft = ColorProvider(day = Color(0xFF5B635D), night = Color(0xFFA3AAA3))
private val sage = ColorProvider(day = Color(0xFF3F5E45), night = Color(0xFFA8C5A0))
private val clay = ColorProvider(day = Color(0xFFC8775B), night = Color(0xFFE4A389))
private val track = ColorProvider(day = Color(0xFFDCE8D6), night = Color(0xFF2C4331))

/** Home-screen widget: today's time against the goal, the streak, and one tap to focus. */
class FocusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { Content(state) }
    }

    private suspend fun load(context: Context): WidgetState {
        val c = (context.applicationContext as FocusApp).container
        val settings = c.settings.current()
        val access = c.usage.hasAccess()
        val today = if (access) runCatching { c.usage.day(LocalDate.now()).totalMs }.getOrDefault(0L) else 0L
        val streak = runCatching { c.history.summary(settings.dailyGoalMinutes).underGoalStreak }.getOrDefault(0)
        val now = System.currentTimeMillis()
        return WidgetState(
            todayMs = today,
            goalMs = settings.dailyGoalMinutes * 60_000L,
            underGoalStreak = streak,
            sessionEndsAt = settings.session?.takeIf { it.isRunning(now) }?.endsAt,
            hasAccess = access,
        )
    }

    @Composable
    private fun Content(s: WidgetState) {
        val over = s.todayMs > s.goalMs
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Today", style = TextStyle(color = soft, fontSize = 12.sp))
            Text(
                if (s.hasAccess) Format.duration(s.todayMs) else "Open Focus to set up",
                style = TextStyle(color = ink, fontSize = 26.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = if (s.goalMs > 0) (s.todayMs.toFloat() / s.goalMs).coerceIn(0f, 1f) else 0f,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = if (over) clay else sage,
                backgroundColor = track,
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                buildString {
                    append("of ${Format.duration(s.goalMs)} goal")
                    if (s.underGoalStreak > 0) append(" · ${s.underGoalStreak}-day streak")
                },
                style = TextStyle(color = soft, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                if (s.sessionEndsAt != null) {
                    val ends = Instant.ofEpochMilli(s.sessionEndsAt).atZone(ZoneId.systemDefault()).toLocalTime()
                        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    Text("Focusing until $ends", style = TextStyle(color = sage, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                } else {
                    Button(text = "Start focus", onClick = actionRunCallback<StartFocusAction>())
                }
            }
        }
    }

    companion object {
        /** Refresh every placed widget. Safe to call from anywhere; failures are ignored. */
        suspend fun refresh(context: Context) {
            runCatching { FocusWidget().updateAll(context) }
        }
    }
}

/** "Start focus" on the widget: a non-strict session at the last-used length. */
class StartFocusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = (context.applicationContext as FocusApp).container
        val s = c.settings.current()
        if (s.session?.isRunning(System.currentTimeMillis()) != true) {
            c.sessions.start(s.lastFocusMinutes, strict = false)
        }
        FocusWidget.refresh(context)
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}
