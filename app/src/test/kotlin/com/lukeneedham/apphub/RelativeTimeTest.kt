package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private fun ago(elapsed: Long) = relativeTime(thenMillis = 1_000_000_000L, nowMillis = 1_000_000_000L + elapsed)

    @Test
    fun `under a minute`() {
        assertEquals("less than 1 minute ago", ago(0))
        assertEquals("less than 1 minute ago", ago(minute - 1))
    }

    @Test
    fun `minutes`() {
        assertEquals("1 minute ago", ago(minute))
        assertEquals("2 minutes ago", ago(2 * minute + 5))
        assertEquals("59 minutes ago", ago(hour - 1))
    }

    @Test
    fun `hours and days`() {
        assertEquals("1 hour ago", ago(hour))
        assertEquals("2 hours ago", ago(2 * hour))
        assertEquals("23 hours ago", ago(day - 1))
        assertEquals("1 day ago", ago(day))
        assertEquals("2 days ago", ago(2 * day))
    }

    @Test
    fun `a time in the future counts as just now`() {
        assertEquals("less than 1 minute ago", relativeTime(thenMillis = 5000, nowMillis = 1000))
    }
}
