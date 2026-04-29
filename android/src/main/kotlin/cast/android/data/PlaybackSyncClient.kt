package cast.android.data

import cast.api.PlaybackStateResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSyncClient @Inject constructor(
    private val httpClient: HttpClient,
    private val settings: CastSettings,
    private val json: Json,
) {
    suspend fun getPosition(episodeId: String): Long? {
        val wsUrl = settings.serverUrl.first().toWsUrl()
        var result: Long? = null
        httpClient.webSocket("$wsUrl/api/playback") {
            send(Frame.Text(json.encodeToString(GetRequest(episodeId = episodeId))))
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val response = json.decodeFromString<PlaybackStateResponse>(frame.readText())
                    if (response.type == "state" && response.episodeId == episodeId) {
                        result = response.progressMs
                        break
                    }
                }
            }
        }
        return result
    }

    suspend fun updatePosition(episodeId: String, progressMs: Long) {
        val wsUrl = settings.serverUrl.first().toWsUrl()
        httpClient.webSocket("$wsUrl/api/playback") {
            send(Frame.Text(json.encodeToString(UpdateRequest(episodeId = episodeId, progressMs = progressMs))))
        }
    }

    private fun String.toWsUrl() = replace("https://", "wss://").replace("http://", "ws://")

    @Serializable
    private data class GetRequest(val type: String = "get", val episodeId: String)

    @Serializable
    private data class UpdateRequest(val type: String = "update", val episodeId: String, val progressMs: Long)
}
