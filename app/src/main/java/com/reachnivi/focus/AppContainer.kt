package com.reachnivi.focus

import android.content.Context
import com.reachnivi.focus.block.SessionNotifier
import com.reachnivi.focus.data.AppCatalog
import com.reachnivi.focus.data.AppDatabase
import com.reachnivi.focus.data.FocusSessionManager
import com.reachnivi.focus.data.PolicyRepository
import com.reachnivi.focus.data.RulesRepository
import com.reachnivi.focus.data.SettingsRepository
import com.reachnivi.focus.usage.UsageRepository
import kotlinx.coroutines.CoroutineScope

/** Hand-rolled dependency container: small app, no DI framework needed yet. */
class AppContainer(context: Context, appScope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.build(appContext)

    val settings = SettingsRepository(appContext)
    val rules = RulesRepository(db.focusDao())
    val catalog = AppCatalog(appContext)
    val usage = UsageRepository(appContext, db.focusDao(), catalog)
    val notifier = SessionNotifier(appContext)
    val sessions = FocusSessionManager(db.focusDao(), settings, notifier)
    val policy = PolicyRepository(rules, settings, appScope)
}
