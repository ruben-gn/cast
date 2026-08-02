package podcast.fakes

import podcast.core.models.SeriesRule
import podcast.core.ports.SeriesRulePersistence

class FakeSeriesRulePersistence : SeriesRulePersistence {
    private val rules = mutableSetOf<SeriesRule>()

    override suspend fun add(rule: SeriesRule) {
        rules.add(rule)
    }

    override suspend fun remove(rule: SeriesRule): Boolean = rules.remove(rule)

    override suspend fun findAll(): List<SeriesRule> = rules.toList()
}
