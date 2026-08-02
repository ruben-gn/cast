package series.core.usecase

import series.core.models.SeriesRule
import series.core.ports.SeriesRulePersistence

class ListSeriesRules(private val persistence: SeriesRulePersistence) {
    suspend operator fun invoke(): List<SeriesRule> = persistence.findAll()
}
