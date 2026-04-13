package podcast.core.usecase

import podcast.core.model.Podcast
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import java.time.Clock
import java.util.*

class AddFeed(
    private val podcasts: PodcastPersistence,
    private val feedInfoProvider: FeedInfoProvider,
    private val clock: Clock
) {
    suspend operator fun invoke(url: String): Podcast {
        podcasts.findByUrl(url)?.let { return it }

        val feedInfo = feedInfoProvider.fetch(url)

        val podcast = Podcast(
            id = UUID.randomUUID().toString(),
            url = url,
            name = feedInfo.title,
            image = feedInfo.image,
            createdAt = clock.instant(),
        )

        podcasts.save(podcast)
        return podcast
    }
}