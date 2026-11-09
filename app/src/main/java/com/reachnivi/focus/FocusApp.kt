package com.reachnivi.focus

import android.app.Application
import com.reachnivi.focus.usage.BlockerHealthWorker
import com.reachnivi.focus.usage.DailySnapshotWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        container.notifier.createChannel()
        container.healthNotifier.createChannel()
        container.connectionNotifier.createChannel()
        DailySnapshotWorker.schedule(this)
        BlockerHealthWorker.schedule(this)
        appScope.launch { container.sessions.completeIfExpired() }
    }
}
