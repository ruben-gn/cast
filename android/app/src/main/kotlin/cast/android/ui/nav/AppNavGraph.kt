package cast.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import cast.android.ui.screens.CatalogScreen
import cast.android.ui.screens.PodcastDetailScreen
import cast.android.ui.screens.QueueScreen
import cast.android.ui.screens.RecentScreen
import cast.android.ui.screens.SettingsScreen

@Composable
fun AppNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Recent, modifier = modifier) {
        composable<Recent> { RecentScreen(navController) }
        composable<Catalog> { CatalogScreen(navController) }
        composable<PodcastDetail> {
            val dest: PodcastDetail = it.toRoute()
            PodcastDetailScreen(dest.podcastId, navController)
        }
        composable<Queue> { QueueScreen(navController) }
        composable<Settings> { SettingsScreen(navController) }
    }
}
