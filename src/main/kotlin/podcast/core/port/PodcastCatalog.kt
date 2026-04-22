package podcast.core.port

import podcast.core.model.Episode
import podcast.core.model.Podcast

fun interface PodcastCatalog {
    suspend fun register(podcast: Podcast, episodes: List<Episode>)
}
