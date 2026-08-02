package series.core.usecase

import podcast.core.ports.PodcastCatalog
import series.core.models.SeriesRule
import series.core.ports.SeriesRulePersistence

class CreateSeriesRule(
    private val catalog: PodcastCatalog,
    private val persistence: SeriesRulePersistence,
) {
    suspend operator fun invoke(rule: SeriesRule): Boolean {
        catalog.findById(rule.podcastId) ?: return false
        persistence.add(rule)
        return true
    }
}
