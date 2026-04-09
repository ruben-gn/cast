package podcast.core.usecase

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence
import java.util.UUID

class AddPodcast(
    private val podcasts: PodcastPersistence
) {
    operator fun invoke(url: String): Podcast {
        val podcast = Podcast(
            id = UUID.randomUUID().toString(),
            url = url,
            name = "New Podcast",
            image = ""
        )
        podcasts.save(podcast)
        return podcast
    }
}