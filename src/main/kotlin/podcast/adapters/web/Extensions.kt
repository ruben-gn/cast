package podcast.adapters.web

import kotlin.time.Duration

fun Duration.formatted(): String =
    toComponents { _, hours, minutes, seconds, _ ->
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
