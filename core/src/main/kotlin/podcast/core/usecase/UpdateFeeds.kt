package podcast.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import podcast.core.ports.PodcastCatalog

private val log = KotlinLogging.logger {}

class UpdateFeeds(
    private val catalog: PodcastCatalog,
    private val updateFeed: UpdateFeed
) {

    suspend operator fun invoke() = supervisorScope {
        catalog.findAll().map { podcast ->
            launch {
                updateFeed(podcast)
                    .onSuccess { (updatedPodcast, episodes) -> log.info { "Updated feed for ${updatedPodcast.name}: ${episodes.size} episodes" } }
                    .onFailure { error -> log.error(error) { "Failed to update feed ${podcast.url}" } }
            }
        }.joinAll()
    }
}