package podcast.core

sealed class PodcastException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FeedFetchFailed(url: String, cause: Throwable) :
        PodcastException("Failed to fetch or parse feed at $url: ${cause.message}", cause)
}
