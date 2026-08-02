package series.core.ports

import series.core.models.SeriesRule

interface SeriesRulePersistence {
    suspend fun add(rule: SeriesRule)
    suspend fun remove(rule: SeriesRule): Boolean
    suspend fun findAll(): List<SeriesRule>
}
