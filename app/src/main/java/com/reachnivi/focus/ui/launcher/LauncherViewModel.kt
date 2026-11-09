package com.reachnivi.focus.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reachnivi.focus.AppContainer
import com.reachnivi.focus.block.TemporaryPasses
import com.reachnivi.focus.core.BlockDecision
import com.reachnivi.focus.core.BlockPolicyEngine
import com.reachnivi.focus.core.DayUsage
import com.reachnivi.focus.data.InstalledApp
import com.reachnivi.focus.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class HomeApp(val packageName: String, val label: String, val pausedNow: Boolean)

data class LauncherUiState(
    val favourites: List<HomeApp> = emptyList(),
    val today: DayUsage? = null,
    val hasUsageAccess: Boolean = true,
)

class LauncherViewModel(private val container: AppContainer) : ViewModel() {

    private val usage = MutableStateFlow<DayUsage?>(null)
    private val hasUsageAccess = MutableStateFlow(true)

    private val _allApps = MutableStateFlow<List<InstalledApp>?>(null)
    val allApps: StateFlow<List<InstalledApp>?> = _allApps.asStateFlow()

    val state: StateFlow<LauncherUiState> = combine(
        container.settings.settings,
        container.policy.snapshot,
        usage,
        hasUsageAccess,
    ) { settings, policy, today, access ->
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        LauncherUiState(
            favourites = settings.homeApps
                .filter { container.catalog.isInstalled(it) }
                .map { pkg ->
                    val decision = BlockPolicyEngine.decideApp(policy, pkg, now, zone, today?.perAppMs?.get(pkg))
                    HomeApp(
                        packageName = pkg,
                        label = container.catalog.label(pkg),
                        pausedNow = decision is BlockDecision.Block &&
                            !TemporaryPasses.isActive(TemporaryPasses.appKey(pkg)),
                    )
                },
            today = today,
            hasUsageAccess = access,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    /** Called on every resume: cheap enough, and keeps the summary line honest. */
    fun refresh() {
        viewModelScope.launch {
            val access = container.usage.hasAccess()
            hasUsageAccess.value = access
            usage.value = if (access) runCatching { container.usage.day(LocalDate.now()) }.getOrNull() else null
            if (_allApps.value == null) loadApps()
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _allApps.value = runCatching { container.catalog.allLaunchableApps() }.getOrDefault(emptyList())
        }
    }

    fun launch(packageName: String): Boolean = container.catalog.launch(packageName)

    fun toggleFavourite(packageName: String, current: List<String>) {
        val next = if (packageName in current) current - packageName else current + packageName
        if (next.size > SettingsRepository.MAX_HOME_APPS) return
        viewModelScope.launch { container.settings.setHomeApps(next) }
    }
}
