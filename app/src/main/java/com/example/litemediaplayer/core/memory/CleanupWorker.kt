package com.example.litemediaplayer.core.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import java.util.concurrent.TimeUnit

class CleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        applicationContext.imageLoader.memoryCache?.clear()
        Runtime.getRuntime().gc()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "memory_cleanup_worker"

        fun schedule(context: Context, intervalMinutes: Int) {
            val safeInterval = intervalMinutes.coerceIn(5, 60)
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(
                safeInterval.toLong(),
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
