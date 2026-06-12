package cast.android.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import cast.android.ui.screens.CatalogScreen
import cast.android.ui.screens.EpisodeDetailScreen
import cast.android.ui.screens.NowPlayingScreen
import cast.android.ui.screens.PodcastDetailScreen
import cast.android.ui.screens.QueueScreen
import cast.android.ui.screens.RecentScreen
import cast.android.ui.screens.SettingsScreen

private const val TRANSITION_MS = 100

@Composable
fun AppNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Recent,
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = { fadeOut(tween(TRANSITION_MS)) },
        modifier = modifier,
    ) {
        composable<Recent> { RecentScreen(navController) }
        composable<Catalog> { CatalogScreen(navController) }
        composable<PodcastDetail> {
            val dest: PodcastDetail = it.toRoute()
            PodcastDetailScreen(dest.podcastId, navController)
        }
        composable<EpisodeDetail>(
            deepLinks = listOf(navDeepLink<EpisodeDetail>(basePath = "cast://episode")),
        ) { EpisodeDetailScreen(navController) }
        composable<Queue> { QueueScreen(navController) }
        composable<Settings> { SettingsScreen(navController) }
        composable<NowPlaying>(
            deepLinks = listOf(navDeepLink { uriPattern = "cast://now-playing" }),
        ) { NowPlayingScreen(navController) }
    }
}
