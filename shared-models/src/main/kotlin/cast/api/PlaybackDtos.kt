package cast.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The playback WebSocket protocol. Both directions are sealed hierarchies discriminated on `type`,
 * which is kotlinx-serialization's default class discriminator — so the wire format is exactly the
 * flat JSON objects the webapp's player.js has always sent and read.
 */
@Serializable
sealed interface PlaybackClientMessage {
    val episodeId: String
}

@Serializable
@SerialName("start")
data class StartPlaybackMessage(
    override val episodeId: String,
    val startPositionMs: Long,
) : PlaybackClientMessage

@Serializable
@SerialName("update")
data class UpdateProgressMessage(
    override val episodeId: String,
    val progressMs: Long,
    // Wire-compat: the webapp doesn't send it, and neither did Android before the offline outbox.
    val updatedAt: Long? = null,
) : PlaybackClientMessage

@Serializable
@SerialName("ended")
data class EpisodeEndedMessage(
    override val episodeId: String,
) : PlaybackClientMessage

@Serializable
@SerialName("get")
data class GetPlaybackStateMessage(
    override val episodeId: String,
) : PlaybackClientMessage

@Serializable
sealed interface PlaybackServerMessage {
    val episodeId: String
}

@Serializable
@SerialName("state")
data class PlaybackStateResponse(
    override val episodeId: String,
    val progressMs: Long,
    val played: Boolean,
) : PlaybackServerMessage
