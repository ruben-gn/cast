package cast.android.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import cast.android.domain.repository.QueueRepository
import cast.api.EpisodeDetailDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The regression net for "episodes must be marked played when they finish."
 *
 * Drives a *real* ExoPlayer (on Robolectric, fake clock) through a two-item playlist to its natural
 * end and asserts the listener emits `ended` for each episode at the right moment:
 *  - the first item via the auto-advance transition (mid-queue completion), and
 *  - the last item via STATE_ENDED (queue exhausted).
 *
 * The up-next item is owned by the backend queue (the listener rebuilds the player's tail from it on
 * every transition via reconcileQueueTail), so the test seeds it through [FakeQueueRepository] rather
 * than adding it to the player directly. A custom [MediaSource.Factory] makes every MediaItem — both
 * the one we set and the one reconcile re-adds from the queue — a playable fake source.
 *
 * This exercises the actual player → listener wiring, so a future change that breaks the
 * transition-reason handling or the end-of-playlist path fails here instead of on a phone.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.14 supports SDK 35 at most; the app targets 36, so pin the sandbox to 35.
@Config(sdk = [35])
class QueuePlaybackListenerTest {

    private lateinit var player: ExoPlayer
    private val wsMessages = mutableListOf<String>()
    private val queue = FakeQueueRepository(upNext = listOf("ep2"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        player = TestExoPlayerBuilder(context).setMediaSourceFactory(FakeSourceFactory).build()
        player.addListener(
            QueuePlaybackListener(
                player = player,
                // Unconfined so the listener's queue/store coroutines run inline on the player thread.
                scope = CoroutineScope(Dispatchers.Unconfined),
                queue = queue,
                store = NoopProgressStore,
                sendWs = { message, _ -> wsMessages += message },
                toMediaItem = { MediaItem.Builder().setMediaId(it.id).build() },
                onWidgetUpdate = {},
                startProgressSync = {},
                stopProgressSync = {},
            )
        )
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun `each episode is marked played when it finishes`() {
        // ep1 is now-playing; ep2 is seeded as up-next in the queue and appended by reconcileQueueTail.
        player.setMediaItems(listOf(MediaItem.Builder().setMediaId("ep1").build()))
        player.prepare()
        player.play()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
        // runUntilPlaybackState returns once the player's internal state is ENDED, but the
        // onPlaybackStateChanged listener callback (which emits the last item's `ended`) is still a
        // queued main-looper runnable. Drain it before asserting.
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("ep1", "ep2"), endedEpisodeIds())
    }

    private fun endedEpisodeIds(): List<String> =
        wsMessages
            .filter { it.contains(""""type":"ended"""") }
            .map { it.substringAfter(""""episodeId":"""").substringBefore('"') }

    /** Builds a playable fake source per MediaItem, preserving its mediaId so transitions report it. */
    private object FakeSourceFactory : MediaSource.Factory {
        override fun setDrmSessionManagerProvider(p: DrmSessionManagerProvider): MediaSource.Factory = this
        override fun setLoadErrorHandlingPolicy(p: LoadErrorHandlingPolicy): MediaSource.Factory = this
        override fun getSupportedTypes(): IntArray = intArrayOf()
        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val window = TimelineWindowDefinition.Builder()
                .setDurationUs(2 * C.MICROS_PER_SECOND)
                .setMediaItem(mediaItem)
                .build()
            return FakeMediaSource(FakeTimeline(window))
        }
    }

    private object NoopProgressStore : PlaybackProgressStore {
        override suspend fun cachedProgressMs(episodeId: String): Long? = null
        override fun cacheProgress(episodeId: String, progressMs: Long) {}
        override fun clearCachedProgress(episodeId: String) {}
        override fun rememberLastEpisode(episodeId: String) {}
        override fun clearLastEpisode() {}
    }

    /** Stateful stand-in: cachedQueue/getQueue reflect the current up-next, removeFromQueue mutates it. */
    private class FakeQueueRepository(upNext: List<String>) : QueueRepository {
        private val ids = upNext.toMutableList()
        private var cached: List<EpisodeDetailDto>? = null
        override val queueIds = MutableStateFlow(ids.toList())

        override fun cachedQueue(): List<EpisodeDetailDto>? = cached
        override suspend fun getQueue(): List<EpisodeDetailDto> = ids.map(::episode).also { cached = it }
        override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> {
            ids += episodeId
            queueIds.value = ids.toList()
            return getQueue()
        }
        override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> {
            ids.remove(episodeId)
            queueIds.value = ids.toList()
            return getQueue()
        }
        override suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto> {
            ids.clear()
            ids += episodeIds
            queueIds.value = ids.toList()
            return getQueue()
        }

        private fun episode(id: String) = EpisodeDetailDto(
            id = id,
            title = id,
            description = "",
            audioUrl = "",
            duration = null,
            durationMs = null,
            publishedAt = null,
            played = false,
            progressMs = 0L,
            podcastId = null,
            podcastName = null,
            podcastImage = null,
        )
    }
}
