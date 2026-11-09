package com.reachnivi.focus.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.core.ActiveSession
import com.reachnivi.focus.core.DayUsage
import com.reachnivi.focus.data.PauseEventEntity
import com.reachnivi.focus.usage.HistorySummary
import com.reachnivi.focus.permissions.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class Range { DAY, WEEK }

data class TodayUiState(
    val loading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val range: Range = Range.DAY,
    val today: DayUsage = DayUsage.EMPTY,
    val yesterday: DayUsage? = null,
    val week: List<Pair<LocalDate, DayUsage>> = emptyList(),
    val goalMinutes: Int = 180,
    val focusedMsToday: Long = 0,
    val session: ActiveSession? = null,
    /** Mindful pauses shown today, and how many ended with "Not now". */
    val pausesToday: Int = 0,
    val letGoToday: Int = 0,
    val history: HistorySummary? = null,
) {
    val goalMs: Long get() = goalMinutes * 60_000L
}

private data class UsageData(
    val loading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val week: List<Pair<LocalDate, DayUsage>> = emptyList(),
    val range: Range = Range.DAY,
)

class TodayViewModel(private val container: AppContainer, private val appContext: Context) : ViewModel() {

    private val data = MutableStateFlow(UsageData())
    private val history = MutableStateFlow<HistorySummary?>(null)
    private val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val state: StateFlow<TodayUiState> = combine(
        data,
        container.settings.settings,
        container.sessions.sessionsSince(startOfToday),
        container.pauseLog.since(startOfToday),
        history,
    ) { d, settings, sessions, pauses, hist ->
        val now = System.currentTimeMillis()
        TodayUiState(
            loading = d.loading,
            hasUsageAccess = d.hasUsageAccess,
            range = d.range,
            today = d.week.lastOrNull()?.second ?: DayUsage.EMPTY,
            yesterday = d.week.getOrNull(d.week.size - 2)?.second,
            week = d.week,
            goalMinutes = settings.dailyGoalMinutes,
            focusedMsToday = sessions.sumOf { it.focusedMs(now).coerceAtLeast(0) },
            session = settings.session?.takeIf { it.isRunning(now) },
            pausesToday = pauses.size,
            letGoToday = pauses.count { it.outcome == PauseEventEntity.SKIPPED },
            history = hist,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun refresh() {
        viewModelScope.launch {
            val hasAccess = Permissions.hasUsageAccess(appContext)
            data.update { it.copy(hasUsageAccess = hasAccess) }
            val today = LocalDate.now()
            val week = if (hasAccess) container.usage.days(today.minusDays(6), today) else emptyList()
            data.update { it.copy(loading = false, week = week) }
            history.value = runCatching { container.history.summary(container.settings.current().dailyGoalMinutes) }
                .getOrNull() ?: history.value
        }
    }

    fun setRange(range: Range) = data.update { it.copy(range = range) }
}
