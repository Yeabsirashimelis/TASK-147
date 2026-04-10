package com.eaglepoint.storefront.ingestion

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UserAgentRotatorTest {

    @Test
    fun `next returns a non-empty user agent string`() {
        val rotator = UserAgentRotator()
        val ua = rotator.next()
        assertThat(ua).isNotEmpty()
        assertThat(ua).contains("Mozilla")
    }

    @Test
    fun `next does not return same agent consecutively`() {
        val rotator = UserAgentRotator()
        val first = rotator.next()
        val second = rotator.next()
        // With 8 agents, consecutive should differ
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `returns valid mobile user agents`() {
        val rotator = UserAgentRotator()
        repeat(20) {
            val ua = rotator.next()
            assertThat(ua).contains("Mobile")
            assertThat(ua).contains("Android")
        }
    }
}
