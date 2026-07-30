package cast.android.network

import android.os.Handler
import android.os.Looper
import cast.api.PlaybackStateResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Null when no server is configured yet. OkHttp upgrades an http(s) URL to a WebSocket itself, so
 * there is no ws:// scheme to build — which also keeps a bare "host:port" working, as elsewhere.
 */
internal fun playbackSocketUrl(base: String): HttpUrl? =
    normalizeBaseUrl(base)?.newBuilder()?.addPathSegments("api/playback")?.build()

@Singleton
class PlaybackWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    connectivityObserver: ConnectivityObserver,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _states = MutableSharedFlow<PlaybackStateResponse>(replay = 1)
    val states: SharedFlow<PlaybackStateResponse> = _states.asSharedFlow()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var active = false
    private val pending = ArrayDeque<PendingMessage>()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private data class PendingMessage(val coalesceKey: String?, val message: String)

    init {
        // Network came back: reconnect right away instead of waiting out the retry timer.
        scope.launch {
            connectivityObserver.isConnected.collect { online ->
                if (online && active && webSocket == null) {
                    reconnectHandler.removeCallbacksAndMessages(null)
                    openWebSocket()
                }
            }
        }
    }

    fun connect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        active = true
        openWebSocket()
    }

    private fun openWebSocket() {
        // An in-flight socket we've given up on would otherwise linger until its own timeout.
        webSocket?.cancel()
        // PlaybackService starts before a server URL exists on a fresh install, and building a
        // Request from an unparseable URL would throw straight out of onCreate, killing the process.
        val wsUrl = playbackSocketUrl(baseUrlInterceptor.baseUrl)
        if (wsUrl == null) {
            if (active) reconnectHandler.postDelayed(::openWebSocket, RECONNECT_DELAY_MS)
            return
        }
        val ws = okHttpClient.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    if (webSocket !== ws) return
                    connected = true
                    synchronized(pending) {
                        for (msg in pending) ws.send(msg.message)
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

    /**
     * Sends now, or queues for the next (re)connect. A non-null [coalesceKey] keeps only the
     * newest queued message per key, so an offline stretch of 1 Hz progress updates flushes as a
     * single message instead of a burst of stale ones.
     */
    fun send(message: String, coalesceKey: String? = null) {
        val ws = webSocket
        if (connected && ws != null && ws.send(message)) return
        synchronized(pending) {
            if (coalesceKey != null) pending.removeAll { it.coalesceKey == coalesceKey }
            pending.add(PendingMessage(coalesceKey, message))
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
