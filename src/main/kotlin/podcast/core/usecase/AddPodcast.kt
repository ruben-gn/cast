package podcast.core.usecase

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence
import java.time.Clock
import java.util.*

class AddPodcast(
    private val podcasts: PodcastPersistence,
    private val clock: Clock
) {
    operator fun invoke(url: String): Podcast {
        val podcast = Podcast(
            id = UUID.randomUUID().toString(),
            url = url,
            name = "New Podcast",
            image = "",
            createdAt = clock.instant(),
        )
        podcasts.save(podcast)
        return podcast
    }
}