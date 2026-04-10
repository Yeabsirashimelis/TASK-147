package com.eaglepoint.storefront.ingestion

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequestPacerTest {

    private lateinit var pacer: RequestPacer

    @BeforeEach
    fun setUp() {
        pacer = RequestPacer()
    }

    @Test
    fun `first request for source does not delay`() = runTest {
        val start = System.currentTimeMillis()
        pacer.pace("source-1", 5000)
        val elapsed = System.currentTimeMillis() - start
        // First request should be near-instant
        assertThat(elapsed).isLessThan(100)
    }

    @Test
    fun `reset clears timing for source`() = runTest {
        pacer.pace("source-1", 1000)
        pacer.reset("source-1")
        val start = System.currentTimeMillis()
        pacer.pace("source-1", 1000)
        val elapsed = System.currentTimeMillis() - start
        assertThat(elapsed).isLessThan(100)
    }

    @Test
    fun `resetAll clears all timings`() = runTest {
        pacer.pace("source-1", 1000)
        pacer.pace("source-2", 1000)
        pacer.resetAll()
        val start = System.currentTimeMillis()
        pacer.pace("source-1", 1000)
        val elapsed = System.currentTimeMillis() - start
        assertThat(elapsed).isLessThan(100)
    }

    @Test
    fun `different sources are paced independently`() = runTest {
        pacer.pace("source-1", 5000)
        val start = System.currentTimeMillis()
        pacer.pace("source-2", 5000)
        val elapsed = System.currentTimeMillis() - start
        // Source-2 should not be delayed by source-1
        assertThat(elapsed).isLessThan(100)
    }
}
