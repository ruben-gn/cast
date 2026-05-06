package queue.core.model

import shared.model.EpisodeId

data class Queue(val episodeIds: Set<EpisodeId>)