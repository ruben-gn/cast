package cast.android.ui.nav

import kotlinx.serialization.Serializable

@Serializable object Recent
@Serializable object Catalog
@Serializable data class PodcastDetail(val podcastId: String)
@Serializable object Queue
@Serializable object Settings
@Serializable object NowPlaying
