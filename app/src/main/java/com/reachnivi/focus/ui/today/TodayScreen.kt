package com.reachnivi.focus.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reachnivi.focus.core.DayUsage
import com.reachnivi.focus.core.Format
import com.reachnivi.focus.core.WeeklyRecap
import com.reachnivi.focus.ui.components.AppIcon
import com.reachnivi.focus.ui.connect.ConnectionCard
import com.reachnivi.focus.ui.components.BarChart
import com.reachnivi.focus.ui.components.BlockerStatusPill
import com.reachnivi.focus.ui.components.rememberBlockerStatus
import com.reachnivi.focus.core.BlockerStatus
import com.reachnivi.focus.ui.components.EmptyHint
import com.reachnivi.focus.ui.components.FocusCard
import com.reachnivi.focus.ui.components.IconBadge
import com.reachnivi.focus.ui.components.LocalAppContainer
import com.reachnivi.focus.ui.components.Meter
import com.reachnivi.focus.ui.components.ProgressRing
import com.reachnivi.focus.ui.components.SectionHeader
import com.reachnivi.focus.ui.components.StatTile
import com.reachnivi.focus.ui.components.rememberResumeTick
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(onStartFocus: () -> Unit, onFixPermissions: () -> Unit, onOpenCircle: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val appContext = LocalContext.current.applicationContext
    val vm: TodayViewModel = viewModel { TodayViewModel(container, appContext) }
    val state by vm.state.collectAsStateWithLifecycle()
    val resumeTick = rememberResumeTick()
    LaunchedEffect(resumeTick) { vm.refresh() }
    val blocker = rememberBlockerStatus()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(state.range, blocker, vm::setRange) }
        if (!state.hasUsageAccess || blocker == BlockerStatus.OFF) {
            item { SetupBanner(state.hasUsageAccess, blocker != BlockerStatus.OFF, onFixPermissions) }
        }
        item {
            AnimatedContent(state.range, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "range") { range ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (range) {
                        Range.DAY -> {
                            DayRingCard(state)
                            StreakRow(state)
                            HourlyCard(state.today)
                            FocusPromptCard(state, onStartFocus)
                            ConnectionCard(onOpenCircle)
                            TopAppsCard("Most used today", state.today, state.loading)
                        }
                        Range.WEEK -> {
                            WeekCard(state)
                            state.history?.let { RecapCard(it.recap) }
                            TopAppsCard("Most used this week", DayUsage.merge(state.week.map { it.second }), state.loading)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(range: Range, blocker: BlockerStatus, onRange: (Range) -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    Column {
        Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(greeting, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        BlockerStatusPill(blocker)
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Range.entries.forEachIndexed { i, r ->
                SegmentedButton(
                    selected = r == range,
                    onClick = { onRange(r) },
                    shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size),
                    icon = {},
                ) { Text(if (r == Range.DAY) "Today" else "This week") }
            }
        }
    }
}

@Composable
private fun SetupBanner(usage: Boolean, blocker: Boolean, onFix: () -> Unit) {
    FocusCard(color = MaterialTheme.colorScheme.tertiaryContainer, onClick = onFix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Rounded.WarningAmber,
                background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Finish setting up", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !usage && !blocker -> "Allow usage access and the blocker to get started."
                        !usage -> "Allow usage access to see your screen time."
                        else -> "Turn on the blocker so your block list works."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DayRingCard(state: TodayUiState) {
    val total = state.today.totalMs
    val progress = if (state.goalMs > 0) total.toFloat() / state.goalMs else 0f
    val over = total > state.goalMs
    FocusCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ProgressRing(progress = progress, modifier = Modifier.size(212.dp).padding(4.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Screen time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Format.duration(total), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "of ${Format.duration(state.goalMs)} goal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    state.loading -> " "
                    total == 0L -> "A fresh start. Your day is wide open."
                    over -> "${Format.duration(total - state.goalMs)} over your goal. Be kind to yourself."
                    else -> "${Format.duration(state.goalMs - total)} left within your goal."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Unlocks", state.today.unlocks.toString(), Modifier.weight(1f), Icons.Rounded.LockOpen)
                StatTile("Focused", Format.duration(state.focusedMsToday), Modifier.weight(1f), Icons.Rounded.SelfImprovement)
                val y = state.yesterday?.totalMs
                val change = if (y != null && y > 0) ((total - y) * 100f / y).roundToInt() else null
                StatTile(
                    "vs yesterday",
                    change?.let { (if (it <= 0) "↓ " else "↑ ") + "${abs(it)}%" } ?: "—",
                    Modifier.weight(1f),
                    if ((change ?: 0) <= 0) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp,
                )
            }
            if (state.pausesToday > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Paused ${state.pausesToday} time${if (state.pausesToday == 1) "" else "s"} today · let go ${state.letGoToday}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HourlyCard(day: DayUsage) {
    FocusCard {
        SectionHeader("Through the day")
        Spacer(Modifier.height(12.dp))
        BarChart(
            values = day.hourlyMs,
            highlightIndex = LocalTime.now().hour,
            height = 96.dp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("12 AM", "6 AM", "12 PM", "6 PM").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FocusPromptCard(state: TodayUiState, onStartFocus: () -> Unit) {
    val session = state.session
    FocusCard(color = MaterialTheme.colorScheme.primaryContainer, onClick = onStartFocus) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (session != null) {
                    Text("Focus session in progress", style = MaterialTheme.typography.titleSmall)
                    val ends = Instant.ofEpochMilli(session.endsAt).atZone(ZoneId.systemDefault()).toLocalTime()
                    Text(
                        "Ends at ${ends.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text("Ready to focus?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pause distractions for a while and do one thing well.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (session == null) {
                Button(onClick = onStartFocus) { Text("Start") }
            } else {
                FilledTonalButton(onClick = onStartFocus) { Text("View") }
            }
        }
    }
}

@Composable
private fun WeekCard(state: TodayUiState) {
    val totals = state.week.map { it.second.totalMs }
    val daysWithData = totals.count { it > 0 }.coerceAtLeast(1)
    val average = totals.sum() / daysWithData
    val labels = state.week.map { it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    FocusCard {
        Text("Daily average", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Format.duration(average), style = MaterialTheme.typography.displaySmall)
        Text(
            "${Format.duration(totals.sum())} this week · ${state.week.sumOf { it.second.unlocks }} unlocks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        BarChart(
            values = totals,
            labels = labels,
            highlightIndex = totals.lastIndex,
            goal = state.goalMs,
            height = 140.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Dashed line: your daily goal",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopAppsCard(title: String, usage: DayUsage, loading: Boolean) {
    val catalog = LocalAppContainer.current.catalog
    val top = usage.topApps(6)
    FocusCard {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        if (top.isEmpty()) {
            EmptyHint(
                Icons.Rounded.HourglassEmpty,
                if (loading) "Gathering your day…" else "Nothing yet",
                if (loading) "One moment." else "Apps you use will show up here.",
            )
        } else {
            val max = top.first().second.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                top.forEach { (pkg, ms) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    catalog.label(pkg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(Format.duration(ms), style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(6.dp))
                            Meter(ms.toFloat() / max)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakRow(state: TodayUiState) {
    val connection by LocalAppContainer.current.connections.summary.collectAsStateWithLifecycle(initialValue = null)
    val h = state.history ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Under goal", plural(h.underGoalStreak, "day"), Modifier.weight(1f), Icons.Rounded.Spa)
        StatTile("Focus streak", plural(h.focusStreak, "day"), Modifier.weight(1f), Icons.Rounded.SelfImprovement)
        StatTile("Connection", plural(connection?.weekStreak ?: 0, "wk"), Modifier.weight(1f), Icons.Rounded.Favorite)
    }
}

@Composable
private fun RecapCard(recap: WeeklyRecap) {
    val catalog = LocalAppContainer.current.catalog
    FocusCard {
        SectionHeader("Last 7 days vs the 7 before")
        Spacer(Modifier.height(8.dp))
        val change = recap.changePercent
        Text(
            when {
                change == null -> "Not enough history yet. Check back next week."
                change < 0 -> "${-change}% less screen time"
                change > 0 -> "$change% more screen time"
                else -> "About the same as before"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if ((change ?: 0) <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        Text(
            "${Format.duration(recap.thisWeekMs)} vs ${Format.duration(recap.lastWeekMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recap.bestDay?.let { day ->
            Spacer(Modifier.height(10.dp))
            Text(
                "Lightest day: ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} (${Format.duration(recap.bestDayMs)})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        recap.biggestDrop?.let { drop ->
            Text(
                "Biggest drop: ${catalog.label(drop.packageName)}, ${Format.duration(-drop.changeMs)} less",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"
