package cast.api

import kotlinx.serialization.Serializable

@Serializable
data class PodcastSummaryDto(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val created: String,
    val updated: String,
)

@Serializable
data class EpisodeDetailDto(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String?,
    val publishedAt: String?,
    val played: Boolean,
    val progressMs: Long,
)

@Serializable
data class PodcastDetailDto(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val created: String,
    val updated: String,
    val episodes: List<EpisodeDetailDto>,
)

@Serializable
data class AddPodcastRequest(val feed: String)
