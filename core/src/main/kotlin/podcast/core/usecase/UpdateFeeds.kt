package podcast.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import podcast.core.ports.PodcastCatalog

private val log = KotlinLogging.logger {}

class UpdateFeeds(
    private val catalog: PodcastCatalog,
    private val updateFeed: UpdateFeed
) {

    suspend operator fun invoke() = supervisorScope {
        catalog.findAll().forEach { podcast ->
            launch {
                try {
                    updateFeed(podcast)
                } catch (e: Exception) {
                    log.error(e) { "Failed to update feed ${podcast.url}" }
                }
            }
        }
    }
}