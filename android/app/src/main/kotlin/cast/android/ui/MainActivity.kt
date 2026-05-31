package cast.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cast.android.network.ConnectivityObserver
import cast.android.ui.components.OfflineBanner
import cast.android.ui.components.PlayerBar
import cast.android.ui.nav.AppNavGraph
import cast.android.ui.nav.BottomNavBar
import cast.android.ui.nav.NowPlaying
import cast.android.ui.theme.CastTheme
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CastTheme {
                CastApp(connectivityObserver)
            }
        }
    }
}

@Composable
private fun CastApp(connectivityObserver: ConnectivityObserver) {
    val isConnected by connectivityObserver.isConnected.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val onNowPlaying = navBackStackEntry?.destination?.route == NowPlaying::class.qualifiedName
    CompositionLocalProvider(LocalPlayerViewModel provides playerViewModel) {
        Scaffold(
            topBar = { OfflineBanner(visible = !isConnected) },
            bottomBar = {
                Column {
                    if (!onNowPlaying) {
                        PlayerBar(onClick = { navController.navigate(NowPlaying) })
                    }
                    BottomNavBar(navController)
                }
            },
        ) { innerPadding ->
            AppNavGraph(navController = navController, modifier = Modifier.padding(innerPadding))
        }
    }
}
