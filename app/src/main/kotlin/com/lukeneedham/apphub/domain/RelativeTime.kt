package com.lukeneedham.apphub.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** How long before [nowMillis] [thenMillis] was, as "12 seconds old", "2 minutes old", "1 hour old", "3 days old"… */
fun ageText(thenMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - thenMillis).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> unitsOld(elapsed / SECOND, "second")
        elapsed < HOUR -> unitsOld(elapsed / MINUTE, "minute")
        elapsed < DAY -> unitsOld(elapsed / HOUR, "hour")
        else -> unitsOld(elapsed / DAY, "day")
    }
}

private fun unitsOld(count: Long, unit: String) = "$count $unit${if (count == 1L) "" else "s"} old"

private val timestampFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

/** [millis] as "25/10/2026 02:21:44" in [zone]. */
fun formatTimestamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timestampFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
