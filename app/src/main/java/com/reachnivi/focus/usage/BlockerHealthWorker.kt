package com.reachnivi.focus.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reachnivi.focus.FocusApp
import com.reachnivi.focus.core.BlockerHealth
import com.reachnivi.focus.core.BlockerStatus
import com.reachnivi.focus.permissions.Permissions
import com.reachnivi.focus.widget.FocusWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Every 15 minutes: if the blocker is down while there's something to block, say so. */
class BlockerHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FocusApp).container
        FocusWidget.refresh(applicationContext)
        var status = Permissions.blockerStatus(applicationContext)
        if (status == BlockerStatus.NOT_RUNNING) {
            // A freshly started process may not have re-bound the service yet; give Android a moment.
            delay(RECHECK_DELAY_MS)
            status = Permissions.blockerStatus(applicationContext)
        }
        if (status == BlockerStatus.ACTIVE) {
            container.healthNotifier.cancel()
            return Result.success()
        }
        val rules = container.rules
        val hasRules = rules.blockedApps.first().isNotEmpty() ||
            rules.blockedDomains.first().isNotEmpty() ||
            rules.limits.first().isNotEmpty()
        if (BlockerHealth.shouldAlert(status, hasRules, System.currentTimeMillis(), container.healthNotifier.lastAlertAt)) {
            container.healthNotifier.showBlockerDown(status)
        }
        return Result.success()
    }

    companion object {
        private const val RECHECK_DELAY_MS = 5_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BlockerHealthWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("blocker-health", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
