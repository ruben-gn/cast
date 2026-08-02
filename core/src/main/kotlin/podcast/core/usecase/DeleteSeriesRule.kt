package podcast.core.usecase

import podcast.core.models.SeriesRule
import podcast.core.ports.SeriesRulePersistence

class DeleteSeriesRule(private val persistence: SeriesRulePersistence) {
    suspend operator fun invoke(rule: SeriesRule): Boolean = persistence.remove(rule)
}
