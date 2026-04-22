package podcast.core

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence

class GetPodcast(private val podcasts: PodcastPersistence) {
    suspend operator fun invoke(id: String): Podcast? = podcasts.findById(id)
}
