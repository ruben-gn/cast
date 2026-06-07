package cast.android.network

import cast.api.AddPodcastRequest
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import cast.api.ReorderQueueRequest
import cast.api.SettingsDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface CastApiService {

    @GET("api/podcasts")
    suspend fun listPodcasts(): List<PodcastSummaryDto>

    @GET("api/podcasts/{id}")
    suspend fun getPodcast(@Path("id") id: String): PodcastDetailDto

    @POST("api/podcasts")
    suspend fun addPodcast(@Body request: AddPodcastRequest): PodcastDetailDto

    @POST("api/podcasts/{id}/played")
    suspend fun markAllPodcastPlayed(@Path("id") id: String): Response<Unit>

    @DELETE("api/podcasts/{id}")
    suspend fun removePodcast(@Path("id") id: String): Response<Unit>

    @GET("api/episodes/recent")
    suspend fun getRecentEpisodes(): List<EpisodeDetailDto>

    @GET("api/episodes/{episodeId}")
    suspend fun getEpisode(@Path("episodeId") episodeId: String): EpisodeDetailDto

    @POST("api/episodes/{episodeId}/played")
    suspend fun markPlayed(@Path("episodeId") episodeId: String): Response<Unit>

    @DELETE("api/episodes/{episodeId}/played")
    suspend fun markUnplayed(@Path("episodeId") episodeId: String): Response<Unit>

    @GET("api/queue")
    suspend fun getQueue(): List<EpisodeDetailDto>

    @POST("api/queue/{episodeId}")
    suspend fun addToQueue(@Path("episodeId") episodeId: String): List<EpisodeDetailDto>

    @DELETE("api/queue/{episodeId}")
    suspend fun removeFromQueue(@Path("episodeId") episodeId: String): List<EpisodeDetailDto>

    @PUT("api/queue")
    suspend fun reorderQueue(@Body request: ReorderQueueRequest): List<EpisodeDetailDto>

    @Multipart
    @POST("api/podcasts/import")
    suspend fun importOpml(@Part file: MultipartBody.Part): Response<Unit>

    @GET("api/settings")
    suspend fun getSettings(): SettingsDto

    @PUT("api/settings")
    suspend fun updateSettings(@Body settings: SettingsDto): Response<Unit>
}
