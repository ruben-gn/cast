package cast.android.domain.repository.impl

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import cast.android.domain.repository.DownloadStatus
import cast.android.network.episode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `download request round-trips the episode metadata`() {
        val episode = episode("ep-1", played = false, progressMs = 0L)
        val request = episodeDownloadRequest(episode, json)
        assertEquals(episode, episodeFromRequest(request, json))
    }

    @Test
    fun `download request is keyed by the episode id`() {
        val episode = episode("ep-1", played = false, progressMs = 0L)
        val request = episodeDownloadRequest(episode, json)
        assertEquals("ep-1", request.id)
        assertEquals("ep-1", request.customCacheKey)
        assertEquals(episode.audioUrl, request.uri.toString())
    }

    @Test
    fun `corrupt download data maps to null instead of crashing`() {
        val request = DownloadRequest.Builder("x", "https://example.test/x.mp3".toUri())
            .setData(byteArrayOf(1, 2, 3))
            .build()
        assertNull(episodeFromRequest(request, json))
    }

    @Test
    fun `only active and completed downloads surface a status`() {
        assertEquals(DownloadStatus.DOWNLOADED, downloadStatusOf(Download.STATE_COMPLETED))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_QUEUED))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_DOWNLOADING))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_RESTARTING))
        assertNull(downloadStatusOf(Download.STATE_FAILED))
        assertNull(downloadStatusOf(Download.STATE_REMOVING))
        assertNull(downloadStatusOf(Download.STATE_STOPPED))
    }
}
