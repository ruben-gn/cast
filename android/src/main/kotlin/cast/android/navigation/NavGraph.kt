package cast.android.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cast.android.ui.player.MiniPlayer
import cast.android.ui.player.NowPlayingScreen
import cast.android.ui.player.PlayerViewModel
import cast.android.ui.podcasts.PodcastDetailScreen
import cast.android.ui.podcasts.PodcastListScreen
import cast.android.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object PodcastList
@Serializable data class PodcastDetail(val podcastId: String)
@Serializable object NowPlaying
@Serializable object Settings

@Composable
fun CastNavGraph() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val controller by playerViewModel.controller.collectAsStateWithLifecycle()
    val currentMediaItem by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            MiniPlayer(
                mediaItem = currentMediaItem,
                isPlaying = isPlaying,
                controller = controller,
                onExpand = { navController.navigate(NowPlaying) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = PodcastList,
        ) {
            composable<PodcastList> {
                PodcastListScreen(
                    onPodcastClick = { navController.navigate(PodcastDetail(it)) },
                    onSettingsClick = { navController.navigate(Settings) },
                )
            }
            composable<PodcastDetail> {
                PodcastDetailScreen(
                    playerViewModel = playerViewModel,
                    onEpisodePlay = { navController.navigate(NowPlaying) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NowPlaying> {
                NowPlayingScreen(
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Settings> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
