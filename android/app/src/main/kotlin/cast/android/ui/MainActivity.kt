package cast.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cast.android.domain.model.Settings
import cast.android.domain.model.ThemeMode
import cast.android.domain.repository.SettingsRepository
import cast.android.network.ConnectivityObserver
import cast.android.ui.components.OfflineBanner
import cast.android.ui.components.PlayerBar
import cast.android.ui.nav.AppNavGraph
import cast.android.ui.nav.BottomNavBar
import cast.android.ui.nav.NowPlaying
import cast.android.ui.theme.CastTheme
import cast.android.ui.theme.PaperBackground
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var connectivityObserver: ConnectivityObserver

    @Inject lateinit var settingsRepository: SettingsRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(Settings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System-bar icons must match the resolved theme, not the device setting, since the
            // user can override it (e.g. force Light while the phone is in dark mode). Light bars
            // (light theme) want dark icons, and vice-versa.
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            CastTheme(darkTheme = darkTheme) {
                PaperBackground {
                    CastApp(connectivityObserver)
                }
            }
        }
    }

    // New-episode notifications need the runtime grant on 13+. Denial is respected: the system
    // only re-prompts a limited number of times, and the worker keeps running without posting.
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            // Transparent so the PaperBackground (linen + grain) shows through; surfaces
            // like cards and bars still paint their own opaque colors on top.
            containerColor = Color.Transparent,
            // A transparent container makes Scaffold derive no content color (contentColorFor
            // finds no match → falls back to the root default Color.Black), which is invisible
            // on the dark Ember background. Set it explicitly so default-colored Text stays readable.
            contentColor = MaterialTheme.colorScheme.onBackground,
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
            AppNavGraph(
                navController = navController,
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            )
        }
    }
}
