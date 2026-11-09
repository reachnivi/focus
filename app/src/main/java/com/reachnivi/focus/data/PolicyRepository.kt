package com.reachnivi.focus.data

import com.reachnivi.focus.core.PolicySnapshot
import com.reachnivi.focus.core.ScheduleRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Live, combined view of every blocking rule, consumed by the accessibility service. */
class PolicyRepository(rules: RulesRepository, settings: SettingsRepository, scope: CoroutineScope) {
    val snapshot: StateFlow<PolicySnapshot> = combine(
        rules.blockedApps,
        rules.blockedDomains,
        rules.schedules,
        rules.limits,
        rules.pausedApps,
        settings.settings,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val apps = values[0] as List<String>
        @Suppress("UNCHECKED_CAST")
        val domains = values[1] as List<String>
        @Suppress("UNCHECKED_CAST")
        val schedules = values[2] as List<ScheduleRule>
        @Suppress("UNCHECKED_CAST")
        val limits = values[3] as List<AppLimitEntity>
        @Suppress("UNCHECKED_CAST")
        val paused = values[4] as List<String>
        val s = values[5] as AppSettings
        PolicySnapshot(
            blockedApps = apps.toSet(),
            blockedDomains = domains.toSet(),
            mode = s.blockMode,
            schedules = schedules,
            session = s.session,
            dailyLimitsMs = limits.associate { it.packageName to it.minutesPerDay * 60_000L },
            pausedApps = paused.toSet(),
        )
    }.stateIn(scope, SharingStarted.Eagerly, PolicySnapshot())
}
