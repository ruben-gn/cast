package podcast.core.usecase

import podcast.core.models.Episode
import podcast.core.ports.PodcastCatalog
import java.time.Instant

class FindRecentEpisodes(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(publishedAfter: Instant): List<Episode> =
        catalog.findEpisodesPublishedAfter(publishedAfter)
}
