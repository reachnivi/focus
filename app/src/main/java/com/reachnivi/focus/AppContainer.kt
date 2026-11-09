package com.reachnivi.focus

import android.content.Context
import com.reachnivi.focus.block.BedtimeController
import com.reachnivi.focus.block.ConnectionNotifier
import com.reachnivi.focus.block.HealthNotifier
import com.reachnivi.focus.block.SessionNotifier
import com.reachnivi.focus.data.AppCatalog
import com.reachnivi.focus.data.AppDatabase
import com.reachnivi.focus.data.ChallengeStore
import com.reachnivi.focus.data.ConnectionsRepository
import com.reachnivi.focus.data.FocusSessionManager
import com.reachnivi.focus.data.GardenRepository
import com.reachnivi.focus.data.PauseLog
import com.reachnivi.focus.data.PolicyRepository
import com.reachnivi.focus.data.RulesRepository
import com.reachnivi.focus.data.SettingsRepository
import com.reachnivi.focus.data.StepStore
import com.reachnivi.focus.usage.HistoryRepository
import com.reachnivi.focus.usage.RecapNotifier
import com.reachnivi.focus.usage.UsageRepository
import com.reachnivi.focus.widget.FocusWidget
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
    val healthNotifier = HealthNotifier(appContext)
    val connectionNotifier = ConnectionNotifier(appContext)
    val bedtime = BedtimeController(appContext, settings)
    val history = HistoryRepository(usage, db.focusDao())
    val recap = RecapNotifier(appContext)
    val steps = StepStore(appContext, db.focusDao())
    val garden = GardenRepository(db.focusDao(), settings, usage, steps.stepsByDay)
    val sessions = FocusSessionManager(db.focusDao(), settings, notifier) { FocusWidget.refresh(appContext) }
    val policy = PolicyRepository(rules, settings, appScope)
    val pauseLog = PauseLog(db.focusDao(), appScope)
    val connections = ConnectionsRepository(appContext, db.focusDao(), ChallengeStore(appContext))
}
