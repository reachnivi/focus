package com.reachnivi.focus.data

import com.reachnivi.focus.core.PolicySnapshot
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
        settings.settings,
    ) { apps, domains, schedules, limits, s ->
        PolicySnapshot(
            blockedApps = apps.toSet(),
            blockedDomains = domains.toSet(),
            mode = s.blockMode,
            schedules = schedules,
            session = s.session,
            dailyLimitsMs = limits.associate { it.packageName to it.minutesPerDay * 60_000L },
        )
    }.stateIn(scope, SharingStarted.Eagerly, PolicySnapshot())
}
