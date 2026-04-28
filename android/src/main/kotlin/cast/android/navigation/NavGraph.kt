package cast.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable object PodcastList
@Serializable data class PodcastDetail(val podcastId: String)
@Serializable object NowPlaying
@Serializable object Settings

@Composable
fun CastNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PodcastList) {
        composable<PodcastList> { }
        composable<PodcastDetail> { }
        composable<NowPlaying> { }
        composable<Settings> { }
    }
}
