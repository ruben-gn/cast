package podcast.core.ports

import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import shared.model.EpisodeId
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

fun interface FeedInfoProvider {
    suspend fun fetch(url: FeedUrl): FeedInfo
}

data class FeedInfo(
    val title: String,
    val url: String,
    val description: String,
    val image: String,
    val episodes: List<EpisodeInfo> = emptyList()
)

data class EpisodeInfo(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: Duration?,
    val publishedAt: Instant
)

fun FeedInfo.toPodcast(id: PodcastId, created: Instant, updated: Instant) = Podcast(
    id = id,
    url = FeedUrl(url),
    name = title,
    image = image,
    listening = true,
    created = created,
    updated = updated
)

fun EpisodeInfo.toEpisode(podcastId: PodcastId) = Episode(
    id = EpisodeId(UUID.randomUUID().toString()),
    feedGuid = guid,
    podcastId = podcastId,
    title = title,
    description = description,
    audioUrl = audioUrl,
    duration = duration,
    publishedAt = publishedAt
)
