package series.core.usecase

import series.core.models.SeriesRule
import series.core.ports.SeriesRulePersistence

class DeleteSeriesRule(private val persistence: SeriesRulePersistence) {
    suspend operator fun invoke(rule: SeriesRule): Boolean = persistence.remove(rule)
}
