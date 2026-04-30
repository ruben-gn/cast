package cast.android.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import cast.android.data.CastSettings
import cast.android.data.PodcastRepository
import cast.api.PlaybackStateResponse
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class CastMediaLibraryService : MediaLibraryService() {

    @Inject lateinit var httpClient: HttpClient
    @Inject lateinit var settings: CastSettings
    @Inject lateinit var json: Json
    @Inject lateinit var repository: PodcastRepository

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private var syncJob: Job? = null
    private var positionFetchedForEpisode: String? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            positionFetchedForEpisode = null
            val item = mediaItem ?: return
            scope.launch {
                val audioUrl = item.localConfiguration?.uri?.toString() ?: return@launch
                settings.saveLastPlayed(
                    episodeId = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "",
                    audioUrl = audioUrl,
                    imageUrl = item.mediaMetadata.artworkUri?.toString() ?: "",
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startSync() else stopSync()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)
        mediaSession = MediaLibrarySession.Builder(this, player, AutoCallback()).build()
        restoreLastPlayed()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        stopSync()
        mediaSession.release()
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun restoreLastPlayed() {
        scope.launch {
            val episodeId = settings.lastEpisodeId.first() ?: return@launch
            val audioUrl = settings.lastEpisodeUrl.first() ?: return@launch
            val title = settings.lastEpisodeTitle.first() ?: ""
            val imageUrl = settings.lastPodcastImage.first() ?: ""
            val item = MediaItem.Builder()
                .setMediaId(episodeId)
                .setUri(audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtworkUri(imageUrl.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) })
                        .build()
                ).build()
            player.setMediaItem(item)
            player.prepare()
        }
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            val wsUrl = settings.serverUrl.first().toWsUrl()
            runCatching {
                httpClient.webSocket("$wsUrl/api/playback") {
                    val episodeId = withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId }
                        ?: return@webSocket

                    if (positionFetchedForEpisode != episodeId) {
                        positionFetchedForEpisode = episodeId
                        send(Frame.Text(json.encodeToString(WsGetRequest(episodeId = episodeId))))
                        val frame = withTimeoutOrNull(3_000) { incoming.receive() }
                        if (frame is Frame.Text) {
                            val resp = json.decodeFromString<PlaybackStateResponse>(frame.readText())
                            if (resp.type == "state" && resp.progressMs > 0) {
                                withContext(Dispatchers.Main) { player.seekTo(resp.progressMs) }
                            }
                        }
                    }

                    while (isActive) {
                        delay(5_000)
                        val (id, posMs) = withContext(Dispatchers.Main) {
                            player.currentMediaItem?.mediaId to player.currentPosition
                        }
                        if (id != null) send(Frame.Text(json.encodeToString(WsUpdateRequest(episodeId = id, progressMs = posMs))))
                    }
                }
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun String.toWsUrl() = replace("https://", "wss://").replace("http://", "ws://")

    @Serializable private data class WsGetRequest(val type: String = "get", val episodeId: String)
    @Serializable private data class WsUpdateRequest(val type: String = "update", val episodeId: String, val progressMs: Long)

    private inner class AutoCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId("ROOT")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Cast")
                        .build()
                ).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            runCatching {
                val items = withContext(Dispatchers.IO) {
                    if (parentId == "ROOT") {
                        repository.listPodcasts().map { podcast ->
                            MediaItem.Builder()
                                .setMediaId(podcast.id)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle(podcast.name)
                                        .setArtworkUri(Uri.parse(podcast.image))
                                        .build()
                                ).build()
                        }
                    } else {
                        val podcast = repository.getPodcast(parentId)
                        podcast.episodes.map { episode ->
                            val extras = Bundle().apply {
                                putLong("durationMs", episode.duration.parseDurationMs() ?: 0L)
                            }
                            MediaItem.Builder()
                                .setMediaId(episode.id)
                                .setUri(episode.audioUrl)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setTitle(episode.title)
                                        .setArtworkUri(Uri.parse(podcast.image))
                                        .setExtras(extras)
                                        .build()
                                ).build()
                        }
                    }
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }.getOrElse { LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO) }
        }
    }
}

private fun String?.parseDurationMs(): Long? {
    if (this == null) return null
    val parts = split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1000
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        else -> null
    }
}
