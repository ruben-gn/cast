package podcast.core

import podcast.core.models.FeedUrl

sealed class PodcastException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FeedFetchFailed(url: FeedUrl, cause: Throwable) :
        PodcastException("Failed to fetch or parse feed at $url: ${cause.message}", cause)
}
