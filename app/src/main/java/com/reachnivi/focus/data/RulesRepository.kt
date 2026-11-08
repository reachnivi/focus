package com.reachnivi.focus.data

import com.reachnivi.focus.core.DomainMatcher
import com.reachnivi.focus.core.ScheduleRule
import com.reachnivi.focus.data.BlockTargetEntity.Companion.TYPE_APP
import com.reachnivi.focus.data.BlockTargetEntity.Companion.TYPE_DOMAIN
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RulesRepository(private val dao: FocusDao) {

    private val targets = dao.observeTargets()

    val blockedApps: Flow<List<String>> =
        targets.map { list -> list.filter { it.type == TYPE_APP }.map { it.value } }

    val blockedDomains: Flow<List<String>> =
        targets.map { list -> list.filter { it.type == TYPE_DOMAIN }.map { it.value } }

    val schedules: Flow<List<ScheduleRule>> =
        dao.observeSchedules().map { list -> list.map { it.toRule() } }

    val limits: Flow<List<AppLimitEntity>> = dao.observeLimits()

    suspend fun setAppBlocked(packageName: String, blocked: Boolean) {
        if (blocked) {
            dao.insertTarget(BlockTargetEntity(type = TYPE_APP, value = packageName))
        } else {
            dao.deleteTarget(TYPE_APP, packageName)
        }
    }

    /** @return the normalized domain that was added, or null if the input wasn't a domain. */
    suspend fun addDomain(input: String): String? {
        val domain = DomainMatcher.normalize(input) ?: return null
        dao.insertTarget(BlockTargetEntity(type = TYPE_DOMAIN, value = domain))
        return domain
    }

    suspend fun removeDomain(domain: String) = dao.deleteTarget(TYPE_DOMAIN, domain)

    suspend fun saveSchedule(rule: ScheduleRule) = dao.upsertSchedule(ScheduleEntity.from(rule))

    suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    suspend fun setLimit(packageName: String, minutes: Int) =
        dao.upsertLimit(AppLimitEntity(packageName, minutes))

    suspend fun removeLimit(packageName: String) = dao.deleteLimit(packageName)
}
