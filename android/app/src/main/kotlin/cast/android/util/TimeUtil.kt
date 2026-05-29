package cast.android.util

import java.time.Instant
import java.time.temporal.ChronoUnit

fun relativeTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(instant, Instant.now()).toInt()
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 35 -> { val w = days / 7; "$w ${if (w == 1) "week" else "weeks"} ago" }
        days < 365 -> { val m = days / 30; "$m ${if (m == 1) "month" else "months"} ago" }
        else -> { val y = days / 365; "$y ${if (y == 1) "year" else "years"} ago" }
    }
}
