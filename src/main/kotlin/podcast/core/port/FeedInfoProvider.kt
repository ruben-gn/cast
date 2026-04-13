package podcast.core.port

fun interface FeedInfoProvider {
    suspend fun fetch(url: String): FeedInfo
}

data class FeedInfo(val title: String, val description: String, val image: String)