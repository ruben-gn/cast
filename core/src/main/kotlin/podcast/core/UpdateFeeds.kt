package podcast.core

import podcast.core.ports.PodcastCatalog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class UpdateFeeds(
    private val catalog: PodcastCatalog,
    private val updateFeed: UpdateFeed
) {

    suspend operator fun invoke() = coroutineScope {
        catalog.findAll().forEach { podcast ->
            launch {
                updateFeed(podcast)
            }
        }
    }
}