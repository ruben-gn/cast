package podcast.core.model

import kotlin.time.Duration

fun Duration.formatted(): String =
    toComponents { hours, minutes, seconds, _ ->
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }