package com.eaglepoint.storefront.ingestion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class IngestionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val ingestionEngine: IngestionEngine by inject()

    override suspend fun doWork(): Result {
        return try {
            val results = ingestionEngine.runIngestionForAllSources(SYSTEM_USER_ID)
            val allSucceeded = results.all {
                it.status == com.eaglepoint.storefront.domain.model.IngestionStatus.SUCCESS
            }

            if (allSucceeded || results.isEmpty()) {
                Result.success()
            } else {
                val failures = results.count {
                    it.status == com.eaglepoint.storefront.domain.model.IngestionStatus.FAILURE
                }
                if (failures == results.size) {
                    // All failed — retry with backoff
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                } else {
                    // Partial success
                    Result.success()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "ingestion_periodic"
        const val SYSTEM_USER_ID = "system"
        const val MAX_RETRY_COUNT = 3
        private const val DEFAULT_INTERVAL_HOURS = 6L
        private const val BACKOFF_DELAY_MINUTES = 5L

        fun schedule(
            context: Context,
            intervalHours: Long = DEFAULT_INTERVAL_HOURS,
            replaceExisting: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<IngestionWorker>(
                intervalHours, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .build()

            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, policy, workRequest)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
