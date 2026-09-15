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

/**
 * Confirms a write reached the database. Clients hold unacknowledged progress in a durable outbox
 * and replay it on reconnect; without an ack they cannot tell a persisted write from one the socket
 * merely accepted before the connection died.
 */
@Serializable
sealed interface PlaybackAck : PlaybackServerMessage

@Serializable
@SerialName("progress-ack")
data class ProgressAckMessage(
    override val episodeId: String,
    // Echoes the acknowledged update's timestamp, so an ack can't retire progress recorded after it.
    // Null for clients that don't timestamp their updates (the webapp).
    val updatedAt: Long? = null,
) : PlaybackAck

@Serializable
@SerialName("ended-ack")
data class EpisodeEndedAckMessage(
    override val episodeId: String,
) : PlaybackAck
