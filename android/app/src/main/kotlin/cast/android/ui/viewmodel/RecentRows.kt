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

// Covers "S06 E08", "S06E08", "s6.e8" and "1x08", plus anything trailing them.
private val seasonEpisodeMarker = Regex(
    """\s*[-–—:|(\[]*\s*\b(s\d{1,2}\s*[.\-x]?\s*e\d{1,3}|\d{1,2}x\d{1,3})\b.*""",
    RegexOption.IGNORE_CASE,
)

private val serialWords = setOf(
    "part", "pt", "deel", "episode", "ep", "afl", "aflevering",
    "chapter", "vol", "volume", "season", "seizoen",
)

private const val SEPARATORS = "-–—:,.#()|[]"

fun guessSeriesName(title: String, siblingTitles: List<String>): String {
    val withoutMarker = title.replace(seasonEpisodeMarker, "")
    if (withoutMarker != title) {
        val name = stripTrailingSerial(withoutMarker)
        if (name.length >= 3) return name
    }

    val prefix = siblingTitles
        .map { title.commonPrefixWith(it, ignoreCase = true) }
        .maxByOrNull { it.length }
        .orEmpty()
    val whole = if (cutsMidWord(title, prefix)) prefix.substringBeforeLast(' ', "") else prefix
    val name = stripTrailingSerial(whole)
    return if (name.length >= 3) name else title
}

private fun cutsMidWord(title: String, prefix: String): Boolean =
    prefix.isNotEmpty() &&
        prefix.length < title.length &&
        !prefix.last().isWhitespace() &&
        !title[prefix.length].isWhitespace()

private fun stripTrailingSerial(value: String): String {
    var name = value
    while (true) {
        val trimmed = name.trimEnd { it.isWhitespace() || it in SEPARATORS }
        // Keep a trailing number when it is all that is left — the name may itself be one ("1619").
        val withoutNumber = trimmed.trimEnd { it.isDigit() }.trimEnd { it.isWhitespace() || it in SEPARATORS }
        val candidate = if (withoutNumber.isEmpty()) trimmed else withoutNumber
        val lastWord = candidate.substringAfterLast(' ')
        if (lastWord.lowercase() !in serialWords) return candidate
        name = candidate.dropLast(lastWord.length)
    }
}
