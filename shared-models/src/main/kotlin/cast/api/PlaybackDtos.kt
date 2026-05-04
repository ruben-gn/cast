package cast.api

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackStateResponse(
    val type: String,
    val episodeId: String,
    val progressMs: Long,
    val played: Boolean,
)
