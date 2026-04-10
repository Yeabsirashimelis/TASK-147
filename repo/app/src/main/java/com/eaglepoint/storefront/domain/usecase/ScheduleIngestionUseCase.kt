package com.eaglepoint.storefront.domain.usecase

import android.content.Context
import com.eaglepoint.storefront.ingestion.IngestionWorker

class ScheduleIngestionUseCase(
    private val context: Context
) {

    fun schedule(intervalHours: Long = DEFAULT_INTERVAL_HOURS, replaceExisting: Boolean = false) {
        IngestionWorker.schedule(context, intervalHours, replaceExisting)
    }

    fun cancel() {
        IngestionWorker.cancel(context)
    }

    companion object {
        private const val DEFAULT_INTERVAL_HOURS = 6L
    }
}
