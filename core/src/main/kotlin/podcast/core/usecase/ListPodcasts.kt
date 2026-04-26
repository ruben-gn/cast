package podcast.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.models.Podcast
import podcast.core.ports.PodcastCatalog

private val log = KotlinLogging.logger { }

class ListPodcasts(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(): List<Podcast> =
        catalog.findAll().also { log.debug { "Listed ${it.size} podcasts." } }
}
