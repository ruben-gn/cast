package cast.android.service

import cast.api.EpisodeEndedMessage
import cast.api.GetPlaybackStateMessage
import cast.api.PlaybackClientMessage
import cast.api.PlaybackServerMessage
import cast.api.PlaybackStateResponse
import cast.api.StartPlaybackMessage
import cast.api.UpdateProgressMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the playback socket's JSON, which the webapp's player.js hand-writes and hand-reads. A
 * rename or a restructured message class that changes these bytes silently breaks the webapp.
 */
class PlaybackMessageWireFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `client messages keep the format the webapp speaks`() {
        assertEquals(
            listOf(
                """{"type":"start","episodeId":"ep1","startPositionMs":0}""",
                """{"type":"update","episodeId":"ep1","progressMs":45000,"updatedAt":1000}""",
                """{"type":"ended","episodeId":"ep1"}""",
                """{"type":"get","episodeId":"ep1"}""",
            ),
            listOf<PlaybackClientMessage>(
                StartPlaybackMessage("ep1", 0L),
                UpdateProgressMessage("ep1", 45_000L, 1_000L),
                EpisodeEndedMessage("ep1"),
                GetPlaybackStateMessage("ep1"),
            ).map { json.encodeToString(PlaybackClientMessage.serializer(), it) },
        )
    }

    @Test
    fun `an update without a timestamp is accepted, as the webapp sends it`() {
        val message = json.decodeFromString<PlaybackClientMessage>(
            """{"type":"update","episodeId":"ep1","progressMs":45000}"""
        )

        assertEquals(UpdateProgressMessage("ep1", 45_000L, null), message)
    }

    @Test
    fun `a state message decodes from what the server sends`() {
        val message = json.decodeFromString<PlaybackServerMessage>(
            """{"type":"state","episodeId":"ep1","progressMs":45000,"played":false}"""
        )

        assertEquals(PlaybackStateResponse("ep1", 45_000L, false), message)
    }
}
