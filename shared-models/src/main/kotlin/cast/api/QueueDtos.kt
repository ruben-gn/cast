package cast.api

import kotlinx.serialization.Serializable

@Serializable
data class QueueDto(val episodeIds: List<String>)