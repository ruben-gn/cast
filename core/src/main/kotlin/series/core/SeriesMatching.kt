package series.core

import podcast.core.models.PodcastId
import series.core.models.SeriesRule

fun List<SeriesRule>.matchSeriesName(podcastId: PodcastId, title: String): String? =
    filter { it.podcastId == podcastId && title.contains(it.name, ignoreCase = true) }
        .maxByOrNull { it.name.length }
        ?.name
