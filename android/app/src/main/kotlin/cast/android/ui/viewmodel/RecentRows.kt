package cast.android.ui.viewmodel

import cast.api.EpisodeDetailDto

sealed interface RecentRow {
    data class Single(val episode: EpisodeDetailDto) : RecentRow
    data class Series(val key: String, val name: String, val episodes: List<EpisodeDetailDto>) : RecentRow
}

fun groupIntoRows(episodes: List<EpisodeDetailDto>): List<RecentRow> {
    fun keyOf(episode: EpisodeDetailDto): String? =
        episode.seriesName?.let { "${episode.podcastId}|$it" }

    val members = episodes.groupBy(::keyOf)
    val emitted = mutableSetOf<String>()
    return episodes.mapNotNull { episode ->
        val key = keyOf(episode)
        when {
            key == null || members.getValue(key).size == 1 -> RecentRow.Single(episode)
            !emitted.add(key) -> null
            else -> RecentRow.Series(key, episode.seriesName!!, members.getValue(key))
        }
    }
}

fun guessSeriesName(title: String, siblingTitles: List<String>): String {
    val longest = siblingTitles
        .map { title.commonPrefixWith(it, ignoreCase = true) }
        .maxByOrNull { it.length }
        .orEmpty()
    val trimmed = longest.trim { it.isDigit() || it.isWhitespace() || it in "-–—:,.#()|" }
    return if (trimmed.length >= 3) trimmed else title
}
