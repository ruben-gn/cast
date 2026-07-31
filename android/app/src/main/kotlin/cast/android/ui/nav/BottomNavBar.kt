package cast.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    // Now Playing is a transient overlay, not part of any tab's history. Pop it (unsaved) before
    // switching tabs; otherwise popUpTo's saveState captures it inside the tab's stack and
    // restoreState resurrects it the next time that tab is selected.
    fun navigateToTab(route: Any) {
        if (currentDest?.hasRoute(NowPlaying::class) == true) navController.popBackStack()
        navController.navigate(route) {
            popUpTo<Recent> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavigationBar {
        NavigationBarItem(
            selected = currentDest?.hasRoute(Recent::class) == true,
            onClick = { navigateToTab(Recent) },
            icon = { Icon(Icons.Default.Headphones, contentDescription = "Recent") },
            label = { Text("Recent") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Catalog::class) == true ||
                currentDest?.hasRoute(PodcastDetail::class) == true,
            onClick = { navigateToTab(Catalog) },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Catalog") },
            label = { Text("Catalog") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Queue::class) == true,
            onClick = { navigateToTab(Queue) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Queue") },
            label = { Text("Queue") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Downloads::class) == true,
            onClick = { navigateToTab(Downloads) },
            icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
            label = { Text("Downloads") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Settings::class) == true,
            onClick = { navigateToTab(Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
    }
}
