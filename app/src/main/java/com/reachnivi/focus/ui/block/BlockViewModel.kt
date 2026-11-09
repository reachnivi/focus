package com.reachnivi.focus.ui.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.core.BlockMode
import com.reachnivi.focus.core.ScheduleRule
import com.reachnivi.focus.data.AppLimitEntity
import com.reachnivi.focus.data.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockUiState(
    val apps: List<String> = emptyList(),
    val pausedApps: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val schedules: List<ScheduleRule> = emptyList(),
    val limits: List<AppLimitEntity> = emptyList(),
    val mode: BlockMode = BlockMode.ALWAYS,
    /** A strict focus session is running: nothing may be loosened until it ends. */
    val locked: Boolean = false,
)

class BlockViewModel(private val container: AppContainer) : ViewModel() {
    private val rules = container.rules

    val state: StateFlow<BlockUiState> = combine(
        combine(rules.blockedApps, rules.pausedApps) { a, p -> a to p },
        rules.blockedDomains,
        rules.schedules,
        rules.limits,
        container.settings.settings,
    ) { (apps, paused), domains, schedules, limits, s ->
        BlockUiState(
            apps = apps,
            pausedApps = paused,
            domains = domains,
            schedules = schedules,
            limits = limits,
            mode = s.blockMode,
            locked = s.session?.let { it.strict && it.isRunning(System.currentTimeMillis()) } ?: false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockUiState())

    private val _installed = MutableStateFlow<List<InstalledApp>?>(null)
    val installed: StateFlow<List<InstalledApp>?> = _installed.asStateFlow()

    fun loadInstalled() {
        if (_installed.value != null) return
        viewModelScope.launch { _installed.value = container.catalog.launchableApps() }
    }

    fun setMode(mode: BlockMode) = launch { container.settings.setBlockMode(mode) }

    fun setAppBlocked(pkg: String, blocked: Boolean) = launch {
        if (!blocked && state.value.locked) return@launch
        rules.setAppBlocked(pkg, blocked)
    }

    /** Pauses only add friction, so they can be added or removed even during strict focus. */
    fun setAppPaused(pkg: String, paused: Boolean) = launch {
        if (paused && pkg in state.value.apps && state.value.locked) return@launch // don't silently unblock
        rules.setAppPaused(pkg, paused)
    }

    /** @return false when the text isn't a valid domain. */
    fun addDomain(input: String, onResult: (Boolean) -> Unit) = launch {
        onResult(rules.addDomain(input) != null)
    }

    fun removeDomain(domain: String) = launch {
        if (!state.value.locked) rules.removeDomain(domain)
    }

    fun saveSchedule(rule: ScheduleRule) = launch { rules.saveSchedule(rule) }

    fun deleteSchedule(id: Long) = launch {
        if (!state.value.locked) rules.deleteSchedule(id)
    }

    fun setLimit(pkg: String, minutes: Int) = launch {
        val existing = state.value.limits.firstOrNull { it.packageName == pkg }
        // During a strict session limits may only get tighter.
        if (state.value.locked && existing != null && minutes > existing.minutesPerDay) return@launch
        rules.setLimit(pkg, minutes)
    }

    fun removeLimit(pkg: String) = launch {
        if (!state.value.locked) rules.removeLimit(pkg)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
