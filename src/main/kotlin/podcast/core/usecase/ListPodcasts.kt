package podcast.core.usecase

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence

class ListPodcasts(private val podcasts: PodcastPersistence) {
    operator fun invoke(): List<Podcast> = podcasts.findAll()
}