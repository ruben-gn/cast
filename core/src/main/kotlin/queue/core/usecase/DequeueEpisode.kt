package queue.core.usecase

import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class DequeueEpisode(private val queues: QueuePersistence) {
    suspend operator fun invoke(episodeId: EpisodeId) =
        queues.get()
            .let { queue -> queue.copy(episodeIds = queue.episodeIds - episodeId) }
            .also { queues.save(it) }
}