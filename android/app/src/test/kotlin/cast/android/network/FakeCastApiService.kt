package cast.android.network

import cast.api.AddPodcastRequest
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import cast.api.ReorderQueueRequest
import cast.api.SettingsDto
import okhttp3.MultipartBody
import retrofit2.Response

/** Builds an [EpisodeDetailDto] with only an id varying; everything else gets harmless defaults. */
fun episode(id: String, played: Boolean = false, progressMs: Long = 0L) = EpisodeDetailDto(
    id = id,
    title = "Episode $id",
    description = "",
    audioUrl = "https://example.test/$id.mp3",
    duration = null,
    durationMs = null,
    publishedAt = null,
    played = played,
    progressMs = progressMs,
)

class FakeCastApiService(
    var recent: List<EpisodeDetailDto> = emptyList(),
    var queue: List<EpisodeDetailDto> = emptyList(),
    var podcasts: List<PodcastSummaryDto> = emptyList(),
    /** Queue contents the mutation endpoints return; defaults to [queue] if unset. */
    var queueAfterMutation: List<EpisodeDetailDto>? = null,
    /** Value returned by getSettings(); also what refresh() should pull. */
    var settings: SettingsDto = SettingsDto(hidePlayed = false),
    /** Captures the last SettingsDto passed to updateSettings(). */
    var updatedSettings: SettingsDto? = null,
) : CastApiService {

    private fun mutatedQueue() = queueAfterMutation ?: queue

    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> = recent
    override suspend fun getQueue(): List<EpisodeDetailDto> = queue
    override suspend fun listPodcasts(): List<PodcastSummaryDto> = podcasts

    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> = mutatedQueue()
    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> = mutatedQueue()
    override suspend fun reorderQueue(request: ReorderQueueRequest): List<EpisodeDetailDto> = mutatedQueue()

    override suspend fun markPlayed(episodeId: String): Response<Unit> = Response.success(Unit)
    override suspend fun markUnplayed(episodeId: String): Response<Unit> = Response.success(Unit)

    // Unused by the low-hanging-fruit tests.
    override suspend fun getPodcast(id: String): PodcastDetailDto = TODO()
    override suspend fun addPodcast(request: AddPodcastRequest): PodcastDetailDto = TODO()
    override suspend fun markAllPodcastPlayed(id: String): Response<Unit> = TODO()
    override suspend fun getEpisode(episodeId: String): EpisodeDetailDto = TODO()
    override suspend fun importOpml(file: MultipartBody.Part): Response<Unit> = TODO()
    override suspend fun getSettings(): SettingsDto = settings
    override suspend fun updateSettings(settings: SettingsDto): Response<Unit> {
        updatedSettings = settings
        return Response.success(Unit)
    }
}
