package podcast.core.usecase

import podcast.core.models.SeriesRule
import podcast.core.ports.SeriesRulePersistence

class ListSeriesRules(private val persistence: SeriesRulePersistence) {
    suspend operator fun invoke(): List<SeriesRule> = persistence.findAll()
}
