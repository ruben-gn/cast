package cast.android.data

import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val httpClient: HttpClient,
    private val settings: CastSettings,
) {
    suspend fun listPodcasts(): List<PodcastSummaryDto> {
        val base = settings.serverUrl.first()
        return httpClient.get("$base/api/podcasts").body()
    }

    suspend fun getPodcast(id: String): PodcastDetailDto {
        val base = settings.serverUrl.first()
        return httpClient.get("$base/api/podcasts/$id").body()
    }
}
