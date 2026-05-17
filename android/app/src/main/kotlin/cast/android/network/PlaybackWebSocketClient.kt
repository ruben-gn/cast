package cast.android.network

import cast.api.PlaybackStateResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val baseUrlInterceptor: BaseUrlInterceptor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _states = MutableSharedFlow<PlaybackStateResponse>(replay = 1)
    val states: SharedFlow<PlaybackStateResponse> = _states.asSharedFlow()

    private var webSocket: WebSocket? = null

    fun connect() {
        val wsUrl = baseUrlInterceptor.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/playback"
        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    _states.tryEmit(json.decodeFromString(text))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Phase 4: reconnection logic
                }
            }
        )
    }

    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
    }

    fun send(message: String) {
        webSocket?.send(message)
    }
}
