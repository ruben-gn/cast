package podcast.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

private val log = KotlinLogging.logger { }

class DeletePodcast(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: PodcastId): Boolean {
        val podcast = catalog.findById(id) ?: run {
            log.info { "Cannot delete podcast $id: not found." }
            return false
        }
        catalog.delete(id)
        log.info { "Deleted podcast ${podcast.name} ($id) from catalog." }
        return true
    }
}
