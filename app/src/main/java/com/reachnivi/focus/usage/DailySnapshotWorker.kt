package com.reachnivi.focus.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reachnivi.focus.FocusApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DailySnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FocusApp).container
        if (!container.usage.hasAccess()) return Result.success()
        val today = LocalDate.now()
        return runCatching {
            container.usage.snapshot(today.minusDays(1))
            container.usage.snapshot(today)
            container.sessions.completeIfExpired()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySnapshotWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("daily-snapshot", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
