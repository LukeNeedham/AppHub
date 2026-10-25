package com.lukeneedham.apphub.domain

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** How long before [nowMillis] [thenMillis] was, as "12 seconds ago", "2 minutes ago", "2 hours ago", "3 days ago"… */
fun relativeTime(thenMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - thenMillis).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> unitsAgo(elapsed / SECOND, "second")
        elapsed < HOUR -> unitsAgo(elapsed / MINUTE, "minute")
        elapsed < DAY -> unitsAgo(elapsed / HOUR, "hour")
        else -> unitsAgo(elapsed / DAY, "day")
    }
}

private fun unitsAgo(count: Long, unit: String) = "$count $unit${if (count == 1L) "" else "s"} ago"
