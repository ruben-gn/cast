package podcast.core

import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastPersistence

class GetPodcast(private val podcasts: PodcastPersistence) {
    suspend operator fun invoke(id: PodcastId): Podcast? = podcasts.findById(id)
}
