package podcast.core

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.model.PodcastSummary
import podcast.core.port.PodcastPersistence

private val log = KotlinLogging.logger { }

class ListPodcasts(private val podcasts: PodcastPersistence) {
    suspend operator fun invoke(): List<PodcastSummary> =
        podcasts.findAllSummaries().also { log.debug { "Listed ${it.size} podcasts." } }
}
