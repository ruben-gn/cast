package cast.android.domain.repository.impl

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.DownloadStatus
import cast.android.service.EpisodeDownloadService
import cast.api.EpisodeDetailDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val json: Json,
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()

    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = refreshStatuses()

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) = refreshStatuses()

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) =
                refreshStatuses()
        })
        refreshStatuses()
    }

    override suspend fun downloadedEpisodes(): List<EpisodeDetailDto> = withContext(Dispatchers.IO) {
        buildList {
            downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
                while (cursor.moveToNext()) {
                    episodeFromRequest(cursor.download.request, json)?.let(::add)
                }
            }
        }
    }

    override fun download(episode: EpisodeDetailDto) {
        DownloadService.sendAddDownload(
            context,
            EpisodeDownloadService::class.java,
            episodeDownloadRequest(episode, json),
            /* foreground = */ true,
        )
    }

    override fun remove(episodeId: String) {
        DownloadService.sendRemoveDownload(
            context,
            EpisodeDownloadService::class.java,
            episodeId,
            /* foreground = */ true,
        )
    }

    private fun refreshStatuses() {
        scope.launch {
            _statuses.value = buildMap {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        downloadStatusOf(download.state)?.let { put(download.request.id, it) }
                    }
                }
            }
        }
    }
}

/**
 * The request carries the full episode DTO so the Downloads screen can list and play episodes
 * from the download index alone — cold start, fully offline, no server or list cache needed.
 * Both the request id and the cache key must be the episode id: that is the identity used by
 * mediaId/customCacheKey everywhere, and a mismatch would make playback miss the downloaded bytes.
 */
@OptIn(UnstableApi::class)
internal fun episodeDownloadRequest(episode: EpisodeDetailDto, json: Json): DownloadRequest =
    DownloadRequest.Builder(episode.id, episode.audioUrl.toUri())
        .setCustomCacheKey(episode.id)
        .setData(json.encodeToString(EpisodeDetailDto.serializer(), episode).encodeToByteArray())
        .build()

@OptIn(UnstableApi::class)
internal fun episodeFromRequest(request: DownloadRequest, json: Json): EpisodeDetailDto? =
    runCatching {
        json.decodeFromString(EpisodeDetailDto.serializer(), request.data.decodeToString())
    }.getOrNull()

@OptIn(UnstableApi::class)
internal fun downloadStatusOf(state: Int): DownloadStatus? = when (state) {
    Download.STATE_COMPLETED -> DownloadStatus.DOWNLOADED
    Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING ->
        DownloadStatus.DOWNLOADING
    else -> null
}
