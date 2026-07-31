package cast.android.ui.nav

import kotlinx.serialization.Serializable

@Serializable object Recent
@Serializable object Catalog
@Serializable data class PodcastDetail(val podcastId: String)
@Serializable data class EpisodeDetail(val episodeId: String)
@Serializable object Queue
@Serializable object Downloads
@Serializable object Settings
@Serializable object NowPlaying
