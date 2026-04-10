package com.eaglepoint.storefront.ingestion

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class RequestPacer {

    private val lastRequestTimes = ConcurrentHashMap<String, Long>()

    suspend fun pace(sourceId: String, delayMs: Long) {
        val lastTime = lastRequestTimes[sourceId]
        if (lastTime != null) {
            val elapsed = System.currentTimeMillis() - lastTime
            if (elapsed < delayMs) {
                // Add jitter: 50-150% of remaining delay
                val remaining = delayMs - elapsed
                val jitter = (remaining * (0.5 + Math.random())).toLong()
                delay(jitter)
            }
        }
        lastRequestTimes[sourceId] = System.currentTimeMillis()
    }

    fun reset(sourceId: String) {
        lastRequestTimes.remove(sourceId)
    }

    fun resetAll() {
        lastRequestTimes.clear()
    }
}
