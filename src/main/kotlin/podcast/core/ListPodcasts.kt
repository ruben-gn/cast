package podcast.core

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence

private val log = KotlinLogging.logger { }

class ListPodcasts(private val podcasts: PodcastPersistence) {
    operator fun invoke(): List<Podcast> = podcasts.findAll().also { log.info { "Listed ${it.size} podcasts."} }
}