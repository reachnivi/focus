package com.reachnivi.focus.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.core.ActiveSession
import com.reachnivi.focus.data.FocusSessionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class FocusUiState(
    val session: ActiveSession? = null,
    val now: Long = System.currentTimeMillis(),
    val minutes: Int = 25,
    val strict: Boolean = false,
    val blockedApps: Int = 0,
    val blockedSites: Int = 0,
    val todaySessions: List<FocusSessionEntity> = emptyList(),
)

private data class Draft(val minutes: Int? = null, val strict: Boolean = false)

class FocusViewModel(private val container: AppContainer) : ViewModel() {

    private val draft = MutableStateFlow(Draft())
    private val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val rules = combine(container.rules.blockedApps, container.rules.blockedDomains) { a, d -> a.size to d.size }

    val state: StateFlow<FocusUiState> = combine(
        container.settings.settings,
        draft,
        clock,
        rules,
        container.sessions.sessionsSince(startOfToday),
    ) { settings, d, now, counts, sessions ->
        val session = settings.session
        if (session != null && now >= session.endsAt) {
            viewModelScope.launch { container.sessions.completeIfExpired() }
        }
        FocusUiState(
            session = session?.takeIf { it.isRunning(now) },
            now = now,
            minutes = d.minutes ?: settings.lastFocusMinutes,
            strict = d.strict,
            blockedApps = counts.first,
            blockedSites = counts.second,
            todaySessions = sessions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun setMinutes(minutes: Int) = draft.update { it.copy(minutes = minutes.coerceIn(5, 240)) }

    fun setStrict(strict: Boolean) = draft.update { it.copy(strict = strict) }

    fun start() {
        val s = state.value
        viewModelScope.launch { container.sessions.start(s.minutes, s.strict) }
        draft.update { it.copy(strict = false) }
    }

    fun stop() {
        viewModelScope.launch { container.sessions.stop() }
    }
}
