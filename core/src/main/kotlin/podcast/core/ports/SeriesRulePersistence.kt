package podcast.core.ports

import podcast.core.models.PodcastId
import podcast.core.models.SeriesRule

interface SeriesRulePersistence {
    suspend fun add(rule: SeriesRule)
    suspend fun remove(rule: SeriesRule): Boolean
    suspend fun removeAllFor(podcastId: PodcastId)
    suspend fun findAll(): List<SeriesRule>
}
