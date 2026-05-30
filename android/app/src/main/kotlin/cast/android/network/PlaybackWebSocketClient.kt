package cast.android.network

import android.os.Handler
import android.os.Looper
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

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var active = false
    private val pending = ArrayDeque<String>()
    private val reconnectHandler = Handler(Looper.getMainLooper())

    fun connect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        active = true
        openWebSocket()
    }

    private fun openWebSocket() {
        val wsUrl = baseUrlInterceptor.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/playback"
        val ws = okHttpClient.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    if (webSocket !== ws) return
                    connected = true
                    synchronized(pending) {
                        for (msg in pending) ws.send(msg)
                        pending.clear()
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    _states.tryEmit(json.decodeFromString(text))
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    if (webSocket !== ws) return
                    webSocket = null
                    connected = false
                    if (active) reconnectHandler.postDelayed(::openWebSocket, RECONNECT_DELAY_MS)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    if (webSocket !== ws) return
                    webSocket = null
                    connected = false
                    if (active) reconnectHandler.postDelayed(::openWebSocket, RECONNECT_DELAY_MS)
                }
            }
        )
        webSocket = ws
    }

    fun disconnect() {
        active = false
        reconnectHandler.removeCallbacksAndMessages(null)
        connected = false
        webSocket?.close(1000, null)
        webSocket = null
    }

    fun send(message: String) {
        val ws = webSocket
        if (connected && ws != null && ws.send(message)) return
        synchronized(pending) { pending.add(message) }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
