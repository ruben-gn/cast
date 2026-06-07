package queue.core.usecase

import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class DequeueEpisodes(private val queues: QueuePersistence) {
    suspend operator fun invoke(episodeIds: List<EpisodeId>) {
        if (episodeIds.isEmpty()) return
        val remove = episodeIds.toSet()
        queues.get()
            .let { queue -> queue.copy(episodeIds = queue.episodeIds.filterNot { it in remove }) }
            .also { queues.save(it) }
    }
}
