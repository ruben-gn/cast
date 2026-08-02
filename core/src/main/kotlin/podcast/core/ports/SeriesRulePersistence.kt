package podcast.core.ports

import podcast.core.models.SeriesRule

interface SeriesRulePersistence {
    suspend fun add(rule: SeriesRule)
    suspend fun remove(rule: SeriesRule): Boolean
    suspend fun findAll(): List<SeriesRule>
}
