package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.ageText
import com.lukeneedham.apphub.domain.formatTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class AgeTextTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private fun age(elapsed: Long) = ageText(thenMillis = 1_000_000_000L, nowMillis = 1_000_000_000L + elapsed)

    @Test
    fun `under a minute counts the seconds`() {
        assertEquals("0 seconds old", age(0))
        assertEquals("1 second old", age(1_000))
        assertEquals("12 seconds old", age(12_500))
        assertEquals("59 seconds old", age(minute - 1))
    }

    @Test
    fun `minutes`() {
        assertEquals("1 minute old", age(minute))
        assertEquals("2 minutes old", age(2 * minute + 5))
        assertEquals("59 minutes old", age(hour - 1))
    }

    @Test
    fun `hours and days`() {
        assertEquals("1 hour old", age(hour))
        assertEquals("2 hours old", age(2 * hour))
        assertEquals("23 hours old", age(day - 1))
        assertEquals("1 day old", age(day))
        assertEquals("2 days old", age(2 * day))
    }

    @Test
    fun `a time in the future counts as just made`() {
        assertEquals("0 seconds old", ageText(thenMillis = 5000, nowMillis = 1000))
    }

    @Test
    fun `a timestamp is day first with seconds`() {
        // 2026-10-25T00:21:44Z
        assertEquals("25/10/2026 00:21:44", formatTimestamp(1_792_887_704_000L, ZoneOffset.UTC))
        assertEquals("25/10/2026 02:21:44", formatTimestamp(1_792_887_704_000L, ZoneOffset.ofHours(2)))
    }
}
